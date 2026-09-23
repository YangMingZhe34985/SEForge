import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.SEFORGE_LOAD_TEST_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const API_BASE_URL = `${BASE_URL}/api/v1`;
const COURSE_ID = __ENV.SEFORGE_LOAD_TEST_COURSE_ID || '';

const IDLE_VUS = integerSetting('SEFORGE_LOAD_TEST_IDLE_VUS', 125);
const API_VUS = integerSetting('SEFORGE_LOAD_TEST_API_VUS', 50);
const SSE_VUS = integerSetting('SEFORGE_LOAD_TEST_SSE_VUS', 20);
const JOB_VUS = integerSetting('SEFORGE_LOAD_TEST_JOB_VUS', 5);
const HOLD_DURATION = __ENV.SEFORGE_LOAD_TEST_DURATION || '2m';
const RAMP_DURATION = __ENV.SEFORGE_LOAD_TEST_RAMP_DURATION || '20s';
const ACTIVE_START = __ENV.SEFORGE_LOAD_TEST_ACTIVE_START || RAMP_DURATION;
const HEAVY_START = __ENV.SEFORGE_LOAD_TEST_HEAVY_START || '30s';
const SSE_TIMEOUT = __ENV.SEFORGE_LOAD_TEST_SSE_TIMEOUT || '2m';
const SSE_MAX_DURATION = __ENV.SEFORGE_LOAD_TEST_SSE_MAX_DURATION || '3m';
const JOB_TIMEOUT_SECONDS = integerSetting('SEFORGE_LOAD_TEST_JOB_TIMEOUT_SECONDS', 120);
const EXPECTED_SSE_TERMINAL = __ENV.SEFORGE_LOAD_TEST_EXPECT_SSE_TERMINAL || 'done';
const REQUIRE_SSE_CITATION = booleanSetting('SEFORGE_LOAD_TEST_REQUIRE_SSE_CITATION', true);

const apiBusinessFailures = new Rate('api_business_failures');
const authenticationFailures = new Rate('authentication_failures');
const sseTerminalFailures = new Rate('sse_terminal_failures');
const sseStreamDuration = new Trend('sse_stream_duration', true);
const jobTerminalFailures = new Rate('job_terminal_failures');
const jobEndToEndDuration = new Trend('job_end_to_end_duration', true);

const scenarios = {};
const thresholds = {
  checks: ['rate>0.99'],
  authentication_failures: ['rate<0.01'],
};

if (IDLE_VUS > 0) {
  scenarios.idle_sessions = {
    executor: 'ramping-vus',
    exec: 'idleSession',
    startVUs: 0,
    stages: [
      { duration: RAMP_DURATION, target: IDLE_VUS },
      { duration: HOLD_DURATION, target: IDLE_VUS },
      { duration: '10s', target: 0 },
    ],
    gracefulRampDown: '5s',
    tags: { workload: 'idle-session' },
  };
  thresholds['http_req_failed{scenario:idle_sessions}'] = ['rate<0.01'];
}

if (API_VUS > 0) {
  scenarios.traditional_api = {
    executor: 'constant-vus',
    exec: 'traditionalApi',
    vus: API_VUS,
    duration: HOLD_DURATION,
    startTime: ACTIVE_START,
    gracefulStop: '10s',
    tags: { workload: 'traditional-api' },
  };
  thresholds['http_req_failed{scenario:traditional_api}'] = ['rate<0.01'];
  thresholds['http_req_duration{scenario:traditional_api}'] = ['p(95)<500'];
  thresholds.api_business_failures = ['rate<0.01'];
}

if (SSE_VUS > 0) {
  scenarios.sse_streams = {
    executor: 'per-vu-iterations',
    exec: 'sseStream',
    vus: SSE_VUS,
    iterations: 1,
    startTime: HEAVY_START,
    maxDuration: SSE_MAX_DURATION,
    gracefulStop: '10s',
    tags: { workload: 'sse' },
  };
  thresholds['http_req_failed{scenario:sse_streams}'] = ['rate<0.01'];
  thresholds.sse_terminal_failures = ['rate<0.01'];
}

if (JOB_VUS > 0) {
  scenarios.background_jobs = {
    executor: 'per-vu-iterations',
    exec: 'backgroundJob',
    vus: JOB_VUS,
    iterations: 1,
    startTime: HEAVY_START,
    maxDuration: `${JOB_TIMEOUT_SECONDS + 30}s`,
    gracefulStop: '10s',
    tags: { workload: 'background-job' },
  };
  thresholds['http_req_failed{scenario:background_jobs}'] = ['rate<0.01'];
  thresholds.job_terminal_failures = ['rate<0.01'];
}

export const options = {
  scenarios,
  thresholds,
  insecureSkipTLSVerify: booleanSetting('SEFORGE_LOAD_TEST_INSECURE_TLS', false),
  userAgent: 'SEForge-k6-release-gate/1.0',
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const vuSessions = {};

export function setup() {
  validateConfiguration();
  const healthPath = __ENV.SEFORGE_LOAD_TEST_HEALTH_PATH || '/healthz';
  const response = http.get(resolveUrl(healthPath), {
    tags: { operation: 'release-readiness' },
    timeout: '10s',
  });
  const ready = check(response, {
    'release endpoint is ready': (res) => res.status === 200,
  });
  if (!ready) {
    throw new Error(`SEForge is not ready at ${resolveUrl(healthPath)} (HTTP ${response.status})`);
  }
}

export function idleSession() {
  ensureAuthenticated('user');
  const response = http.get(`${API_BASE_URL}/auth/me`, requestParams('session-heartbeat'));
  recordBusinessResponse(response, 'idle session remains authenticated');
  sleep(numberSetting('SEFORGE_LOAD_TEST_IDLE_INTERVAL_SECONDS', 15));
}

export function traditionalApi() {
  ensureAuthenticated('user');

  const responses = http.batch([
    ['GET', `${API_BASE_URL}/courses?page=0&size=20`, null, requestParams('courses-list')],
    ['GET', `${API_BASE_URL}/courses/${COURSE_ID}`, null, requestParams('course-detail')],
    ['GET', `${API_BASE_URL}/courses/${COURSE_ID}/assignments?page=0&size=20`, null,
      requestParams('assignments-list')],
    ['GET', `${API_BASE_URL}/courses/${COURSE_ID}/conversations?page=0&size=20`, null,
      requestParams('conversations-list')],
  ]);

  recordBusinessResponse(responses[0], 'courses list succeeds');
  recordBusinessResponse(responses[1], 'course detail succeeds');
  recordBusinessResponse(responses[2], 'assignments list succeeds');
  recordBusinessResponse(responses[3], 'conversations list succeeds');
  sleep(numberSetting('SEFORGE_LOAD_TEST_THINK_TIME_SECONDS', 1));
}

export function sseStream() {
  const auth = ensureAuthenticated('user');
  const requestId = uniqueId('sse');
  const conversation = http.post(
    `${API_BASE_URL}/courses/${COURSE_ID}/conversations`,
    JSON.stringify({ title: `k6 ${requestId}` }),
    requestParams('conversation-create', auth, '30s'),
  );

  if (!recordBusinessResponse(conversation, 'SSE conversation is created')) {
    fail(`Cannot create SSE conversation (HTTP ${conversation.status})`);
  }
  const conversationId = envelopeData(conversation)?.id;
  if (!conversationId) {
    apiBusinessFailures.add(1, { operation: 'conversation-create' });
    fail('Conversation response did not contain data.id');
  }

  const startedAt = Date.now();
  const response = http.post(
    `${API_BASE_URL}/courses/${COURSE_ID}/conversations/${conversationId}/messages`,
    JSON.stringify({
      requestId,
      content: __ENV.SEFORGE_LOAD_TEST_SSE_QUESTION || '请根据课程资料简要说明软件需求可验证性的含义。',
    }),
    requestParams('course-qa-sse', auth, SSE_TIMEOUT, { Accept: 'text/event-stream' }),
  );
  sseStreamDuration.add(Date.now() - startedAt);

  const contentType = response.headers['Content-Type'] || response.headers['content-type'] || '';
  const eventNames = sseEventNames(String(response.body || ''));
  const terminalEvents = eventNames.filter((name) => name === 'done' || name === 'error');
  const hasDelta = eventNames.includes('message.delta');
  const hasRequiredCitation = !REQUIRE_SSE_CITATION || eventNames.includes('citation');
  const streamOk = response.status === 200
    && contentType.toLowerCase().includes('text/event-stream')
    && hasDelta
    && hasRequiredCitation
    && terminalEvents.length === 1
    && terminalEvents[0] === EXPECTED_SSE_TERMINAL;

  sseTerminalFailures.add(streamOk ? 0 : 1);
  check(response, {
    'SSE returns event-stream': () => response.status === 200
      && contentType.toLowerCase().includes('text/event-stream'),
    'SSE emits answer deltas': () => hasDelta,
    'SSE emits required citations': () => hasRequiredCitation,
    'SSE emits exactly one expected terminal event': () => terminalEvents.length === 1
      && terminalEvents[0] === EXPECTED_SSE_TERMINAL,
  });
}

export function backgroundJob() {
  const auth = ensureAuthenticated('teacher');
  const idempotencyKey = uniqueId('job');
  const configuredPath = __ENV.SEFORGE_LOAD_TEST_JOB_PATH
    || `/api/v1/courses/{courseId}/analytics/snapshots`;
  let path = configuredPath.replace(/\{courseId\}/g, COURSE_ID)
    .replace(/\{idempotencyKey\}/g, encodeURIComponent(idempotencyKey));
  if (!configuredPath.includes('{idempotencyKey}')) {
    path = appendQuery(path, 'idempotencyKey', idempotencyKey);
  }
  if (__ENV.SEFORGE_LOAD_TEST_CLASS_ID) {
    path = appendQuery(path, 'classId', __ENV.SEFORGE_LOAD_TEST_CLASS_ID);
  }

  const bodyTemplate = __ENV.SEFORGE_LOAD_TEST_JOB_BODY || '{}';
  const body = bodyTemplate.replace(/\{courseId\}/g, COURSE_ID)
    .replace(/\{idempotencyKey\}/g, idempotencyKey);
  assertJson(body, 'SEFORGE_LOAD_TEST_JOB_BODY');

  const startedAt = Date.now();
  const submitted = http.post(resolveUrl(path), body,
    requestParams('job-submit', auth, '30s'));
  if (!recordBusinessResponse(submitted, 'background job is accepted')) {
    jobTerminalFailures.add(1);
    fail(`Background job was not accepted (HTTP ${submitted.status})`);
  }

  const submittedData = envelopeData(submitted) || {};
  const jobId = submittedData.asyncJobId || submittedData.jobId || submittedData.id;
  if (!jobId) {
    jobTerminalFailures.add(1);
    fail('Background job response did not contain an async job id');
  }

  const deadline = Date.now() + JOB_TIMEOUT_SECONDS * 1000;
  let finalStatus = null;
  while (Date.now() < deadline) {
    const statusResponse = http.get(`${API_BASE_URL}/jobs/${jobId}`, requestParams('job-status'));
    const statusData = envelopeData(statusResponse);
    if (!statusData || statusResponse.status !== 200) {
      jobTerminalFailures.add(1);
      check(statusResponse, { 'job status remains queryable': () => false });
      break;
    }
    finalStatus = statusData.status;
    if (['COMPLETED', 'FAILED', 'DEAD_LETTER', 'CANCELLED'].includes(finalStatus)) break;
    sleep(numberSetting('SEFORGE_LOAD_TEST_JOB_POLL_SECONDS', 1));
  }

  const completed = finalStatus === 'COMPLETED';
  jobEndToEndDuration.add(Date.now() - startedAt);
  jobTerminalFailures.add(completed ? 0 : 1);
  check(finalStatus, {
    'background job reaches COMPLETED': (status) => status === 'COMPLETED',
  });
}

function ensureAuthenticated(kind) {
  if (vuSessions[kind]) return vuSessions[kind];
  const credentials = credentialsFor(kind);
  const initialCsrf = fetchCsrf('csrf-before-login');
  const login = http.post(
    `${API_BASE_URL}/auth/login`,
    JSON.stringify({ identifier: credentials.identifier, password: credentials.password }),
    requestParams('login', initialCsrf, '30s'),
  );
  const loginOk = isBusinessSuccess(login);
  authenticationFailures.add(loginOk ? 0 : 1, { role: kind });
  check(login, { 'authentication succeeds': () => loginOk });
  if (!loginOk) fail(`Authentication failed for ${kind} test account (HTTP ${login.status})`);

  // Spring Security rotates the session/CSRF state after authentication.
  const authenticatedCsrf = fetchCsrf('csrf-after-login');
  vuSessions[kind] = authenticatedCsrf;
  return authenticatedCsrf;
}

function fetchCsrf(operation) {
  const response = http.get(`${API_BASE_URL}/auth/csrf`, requestParams(operation));
  const data = envelopeData(response);
  const ok = response.status === 200 && data?.headerName && data?.token;
  authenticationFailures.add(ok ? 0 : 1, { operation });
  check(response, { 'CSRF token is issued': () => Boolean(ok) });
  if (!ok) fail(`CSRF token request failed (HTTP ${response.status})`);
  return { headerName: data.headerName, token: data.token };
}

function requestParams(operation, csrf, timeout = '15s', extraHeaders = {}) {
  const headers = { ...extraHeaders };
  if (csrf) {
    headers['Content-Type'] = 'application/json';
    headers[csrf.headerName] = csrf.token;
  }
  return {
    headers,
    timeout,
    tags: { operation },
  };
}

function recordBusinessResponse(response, assertionName) {
  const ok = isBusinessSuccess(response);
  apiBusinessFailures.add(ok ? 0 : 1, { operation: response.request?.tags?.operation || 'unknown' });
  check(response, { [assertionName]: () => ok });
  return ok;
}

function isBusinessSuccess(response) {
  if (!response || response.status < 200 || response.status >= 300) return false;
  try {
    return response.json().code === 'OK';
  } catch (_) {
    return false;
  }
}

function envelopeData(response) {
  try {
    const envelope = response.json();
    return envelope?.code === 'OK' ? envelope.data : null;
  } catch (_) {
    return null;
  }
}

function credentialsFor(kind) {
  if (kind === 'teacher') {
    return {
      identifier: __ENV.SEFORGE_LOAD_TEST_TEACHER_IDENTIFIER
        || __ENV.SEFORGE_LOAD_TEST_IDENTIFIER,
      password: __ENV.SEFORGE_LOAD_TEST_TEACHER_PASSWORD
        || __ENV.SEFORGE_LOAD_TEST_PASSWORD,
    };
  }

  const prefix = __ENV.SEFORGE_LOAD_TEST_USER_PREFIX || '';
  if (prefix) {
    const poolSize = integerSetting('SEFORGE_LOAD_TEST_USER_COUNT', 200);
    return {
      identifier: `${prefix}${((__VU - 1) % poolSize) + 1}`,
      password: __ENV.SEFORGE_LOAD_TEST_PASSWORD,
    };
  }
  return {
    identifier: __ENV.SEFORGE_LOAD_TEST_IDENTIFIER,
    password: __ENV.SEFORGE_LOAD_TEST_PASSWORD,
  };
}

function validateConfiguration() {
  const activeVus = IDLE_VUS + API_VUS + SSE_VUS + JOB_VUS;
  if (activeVus <= 0) throw new Error('At least one workload VU must be enabled');
  if (!/^\d+$/.test(COURSE_ID) || Number(COURSE_ID) <= 0) {
    throw new Error('SEFORGE_LOAD_TEST_COURSE_ID must be a positive numeric course id');
  }

  const prefix = __ENV.SEFORGE_LOAD_TEST_USER_PREFIX || '';
  const identifier = __ENV.SEFORGE_LOAD_TEST_IDENTIFIER || '';
  requireSecret('SEFORGE_LOAD_TEST_PASSWORD', __ENV.SEFORGE_LOAD_TEST_PASSWORD);
  if (!prefix && !identifier) {
    throw new Error('Set SEFORGE_LOAD_TEST_IDENTIFIER or SEFORGE_LOAD_TEST_USER_PREFIX');
  }
  if (prefix && integerSetting('SEFORGE_LOAD_TEST_USER_COUNT', 200) <= 0) {
    throw new Error('SEFORGE_LOAD_TEST_USER_COUNT must be greater than zero');
  }

  if (JOB_VUS > 0) {
    const teacher = __ENV.SEFORGE_LOAD_TEST_TEACHER_IDENTIFIER || identifier;
    const teacherPassword = __ENV.SEFORGE_LOAD_TEST_TEACHER_PASSWORD
      || __ENV.SEFORGE_LOAD_TEST_PASSWORD;
    if (!teacher) throw new Error('A teacher identifier is required when background jobs are enabled');
    requireSecret('SEFORGE_LOAD_TEST_TEACHER_PASSWORD', teacherPassword);
  }
  if (!['done', 'error'].includes(EXPECTED_SSE_TERMINAL)) {
    throw new Error('SEFORGE_LOAD_TEST_EXPECT_SSE_TERMINAL must be done or error');
  }
}

function requireSecret(name, value) {
  if (!value || /^change-me/i.test(value)) {
    throw new Error(`${name} must be set to a non-placeholder test credential`);
  }
}

function sseEventNames(body) {
  return body.split(/\r?\n/)
    .filter((line) => line.startsWith('event:'))
    .map((line) => line.substring('event:'.length).trim());
}

function resolveUrl(path) {
  if (/^https?:\/\//i.test(path)) return path;
  return `${BASE_URL}${path.startsWith('/') ? '' : '/'}${path}`;
}

function appendQuery(path, key, value) {
  const separator = path.includes('?') ? '&' : '?';
  return `${path}${separator}${encodeURIComponent(key)}=${encodeURIComponent(value)}`;
}

function uniqueId(prefix) {
  return `${prefix}-${__VU}-${__ITER}-${Date.now()}`;
}

function assertJson(value, name) {
  try {
    JSON.parse(value);
  } catch (_) {
    throw new Error(`${name} must contain valid JSON`);
  }
}

function integerSetting(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') return fallback;
  const value = Number.parseInt(raw, 10);
  if (!Number.isFinite(value) || value < 0) throw new Error(`${name} must be a non-negative integer`);
  return value;
}

function numberSetting(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') return fallback;
  const value = Number(raw);
  if (!Number.isFinite(value) || value < 0) throw new Error(`${name} must be a non-negative number`);
  return value;
}

function booleanSetting(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(raw.toLowerCase());
}
