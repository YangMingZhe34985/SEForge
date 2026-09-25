// Reproducible isolated release acceptance. All mutations target PROJECT, never the user's stack.
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { randomBytes, randomUUID, createHash, createCipheriv, createDecipheriv } from 'node:crypto'
import https from 'node:https'
import assert from 'node:assert/strict'
import { renderTls } from './render-tls.mjs'
const root=resolve(import.meta.dirname,'../..'), out=resolve(root,'backend/target/phase6-release')
const project='seforge-p6-verify', envPath=resolve(out,'release.env')
mkdirSync(out,{recursive:true})
const action=process.argv[2] || 'inspect'
function docker(args, input, raw=false) {
  try {return execFileSync('docker',args,{cwd:root,input,encoding:raw?undefined:'utf8',maxBuffer:64*1024*1024,stdio:['pipe','pipe','pipe']})}
  catch(e){writeFileSync(resolve(out,'last-command-error.log'),String(e.stderr||e.stdout||e.message));throw new Error(`Docker operation failed; see local last-command-error.log (${args[0]})`)}
}
const composeArgs=['compose','--project-name',project,'--env-file',envPath,'-f','backend/docker/docker-compose.yml','-f','backend/docker/compose.production.yml','-f','backend/docker/compose.release-test.yml']
const compose=(args,input)=>docker([...composeArgs,...args],input)
const sleep=ms=>new Promise(r=>setTimeout(r,ms))
function save(name,value){writeFileSync(resolve(out,name),JSON.stringify(value,null,2))}
if(action==='configure') {
  if(!existsSync(envPath)) {
    const secret=()=>randomBytes(24).toString('hex')
    const env={SEFORGE_IMAGE_TAG:'phase6-verify',SEFORGE_RELEASE_DIR:out.replaceAll('\\','/'),SEFORGE_HTTP_PORT:'18443',SESSION_COOKIE_SECURE:'true',SEFORGE_ALLOWED_ORIGINS:'https://localhost:18443',MYSQL_DATABASE:'seforge',MYSQL_APP_USERNAME:'seforge',MYSQL_APP_PASSWORD:secret(),MYSQL_ROOT_PASSWORD:secret(),REDIS_PASSWORD:secret(),MINIO_ROOT_USER:'phase6-root',MINIO_ROOT_PASSWORD:secret(),MINIO_APP_ACCESS_KEY:'phase6-app',MINIO_APP_SECRET_KEY:secret(),MINIO_APP_BUCKET:'seforge',SONAR_POSTGRES_PASSWORD:secret(),SEFORGE_BOOTSTRAP_ADMIN_EMAIL:'release-admin@example.invalid',SEFORGE_BOOTSTRAP_ADMIN_USERNAME:'release-admin',SEFORGE_BOOTSTRAP_ADMIN_PASSWORD:secret(),RELEASE_USER_PASSWORD:secret(),SEFORGE_SONAR_ENABLED:'false'}
    writeFileSync(envPath,Object.entries(env).map(([k,v])=>`${k}=${v}`).join('\n')+'\n',{mode:0o600})
  }
  mkdirSync(resolve(out,'tls'),{recursive:true})
  if(!existsSync(resolve(out,'tls/cert.pem'))) execFileSync('openssl',['req','-x509','-newkey','rsa:2048','-nodes','-days','3','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,DNS:frontend,IP:127.0.0.1','-keyout',resolve(out,'tls/key.pem'),'-out',resolve(out,'tls/cert.pem')],{stdio:'pipe'})
  writeFileSync(resolve(out,'nginx-tls.conf'),renderTls())
  let configured=readFileSync(envPath,'utf8')
  if(!configured.includes('SEFORGE_TLS_DIR=')) configured+=`SEFORGE_TLS_DIR=${resolve(out,'tls').replaceAll('\\','/')}\nSEFORGE_TLS_CONFIG=${resolve(out,'nginx-tls.conf').replaceAll('\\','/')}\n`
  writeFileSync(envPath,configured,{mode:0o600})
  compose(['config','--quiet']);console.log('Isolated TLS configuration validated');process.exit(0)
}
assert(existsSync(envPath),'Run configure first')
const env=Object.fromEntries(readFileSync(envPath,'utf8').trim().split(/\r?\n/).map(line=>{const index=line.indexOf('=');return [line.slice(0,index),line.slice(index+1)]}))
if(action==='build'){writeFileSync(resolve(out,'build.log'),compose(['build','api','worker','frontend']));console.log('Release images built');process.exit(0)}
if(action==='up'){console.log(compose(['up','-d','--wait','--wait-timeout','240','--scale','worker=5']));process.exit(0)}
const ca=readFileSync(resolve(out,'tls/cert.pem'))
const base=`https://localhost:${env.SEFORGE_HTTP_PORT}`
class Client {
  constructor(url=base){this.base=url}
  jar=new Map(); token=''; agent=new https.Agent({keepAlive:true,ca,maxSockets:4})
  async request(path,method='GET',body,headers={}) {
    const payload=body===undefined?undefined:Buffer.isBuffer(body)?body:Buffer.from(JSON.stringify(body))
    return new Promise((resolvePromise,reject)=>{
      const req=https.request(this.base+path,{method,agent:this.agent,headers:{...(payload?{'Content-Type':'application/json','Content-Length':payload.length}:{}),Cookie:[...this.jar].map(([k,v])=>`${k}=${v}`).join('; '),'X-XSRF-TOKEN':this.token,...headers}},res=>{
        for(const cookie of res.headers['set-cookie']||[]){const first=cookie.split(';')[0],i=first.indexOf('=');this.jar.set(first.slice(0,i),first.slice(i+1))}
        const chunks=[];res.on('data',d=>chunks.push(d));res.on('end',()=>{const text=Buffer.concat(chunks).toString();let json;try{json=JSON.parse(text)}catch{}resolvePromise({status:res.statusCode,headers:res.headers,text,json})})
      });req.setTimeout(150000,()=>req.destroy(new Error('Request deadline')));req.on('error',reject);req.end(payload)
    })
  }
  async csrf(){const r=await this.request('/api/v1/auth/csrf');assert.equal(r.status,200);this.token=r.json.data.token}
  async login(identifier,portal,password=env.RELEASE_USER_PASSWORD){await this.csrf();const r=await this.request('/api/v1/auth/login','POST',{identifier,portal,password});assert.equal(r.status,200,`login ${identifier}: ${r.status}`);assert((r.headers['set-cookie']||[]).some(c=>c.includes('SEFORGE_SESSION=')&&/secure/i.test(c)&&/httponly/i.test(c)));await this.csrf();return this}
  async data(path,method='GET',body,headers){const r=await this.request(path,method,body,headers);assert(r.status>=200&&r.status<300,`${method} ${path}: ${r.status} ${r.json?.message||''}`);return r.json?.data}
}
function sql(statement){return compose(['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names -u "$MYSQL_USER" "$MYSQL_DATABASE"'],statement)}
function stub(path){return compose(['exec','-T','provider-stub','node','-e',`fetch('http://localhost:8090/${path}').then(r=>r.text()).then(console.log)`]).trim()}
async function waitJob(client,id,timeout=240000){const start=Date.now();while(Date.now()-start<timeout){const job=await client.data(`/api/v1/jobs/${id}`);if(['COMPLETED','DEAD_LETTER','CANCELLED'].includes(job.status))return job;await sleep(500)}throw new Error(`Job ${id} did not terminate`)}
async function upload(client,course,name,content){const boundary='phase6-'+randomUUID();const bytes=Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${name}"\r\nContent-Type: text/plain\r\n\r\n${content}\r\n--${boundary}--\r\n`);return client.data(`/api/v1/courses/${course}/knowledge/documents`,'POST',bytes,{'Content-Type':`multipart/form-data; boundary=${boundary}`})}
const fixtureFile=resolve(out,'fixture.json')
if(action==='seed') {
  const admin=await new Client().login('release-admin','ADMIN',env.SEFORGE_BOOTSTRAP_ADMIN_PASSWORD)
  const teacher=await admin.data('/api/v1/admin/users','POST',{email:'release-teacher@example.invalid',username:'release-teacher',password:env.RELEASE_USER_PASSWORD,displayName:'Release Teacher',accountType:'TEACHER',roles:['USER']})
  const teacherId=Number(teacher.id)
  sql(`INSERT INTO semesters(id,code,name,starts_on,ends_on,status) VALUES(600,'release','Release','2026-01-01','2027-12-31','ACTIVE');\nINSERT INTO courses(id,code,name,semester_id,owner_id) VALUES(600,'release','Release',600,${teacherId});\nINSERT INTO course_classes(id,course_id,code,name) VALUES(600,600,'release','Release');\nINSERT INTO course_members(course_id,user_id,role) VALUES(600,${teacherId},'TEACHER');`)
  let seed='';for(let i=0;i<200;i++){const id=10000+i;seed+=`INSERT INTO users(id,email,username,password_hash) SELECT ${id},'release${i}@example.invalid','release${i}',password_hash FROM users WHERE id=${teacherId};\nINSERT INTO user_profiles(user_id,display_name,account_type,student_no) VALUES(${id},'Release ${i}','STUDENT','P6-${String(i).padStart(4,'0')}');\nINSERT INTO user_roles(user_id,role_id) SELECT ${id},id FROM roles WHERE code='USER';\nINSERT INTO course_members(course_id,class_id,user_id,role) VALUES(600,600,${id},'STUDENT');\n`}
  sql(seed)
  const teacherClient=await new Client().login('release-teacher','TEACHER')
  const document=await upload(teacherClient,600,'release-evidence.txt','Software engineering requires testable requirements and clear acceptance criteria. '.repeat(60))
  assert.equal((await waitJob(teacherClient,document.job.id)).status,'COMPLETED')
  const snapshot=await teacherClient.data('/api/v1/courses/600/analytics/snapshots','POST',{})
  assert.equal((await waitJob(teacherClient,snapshot.id)).status,'COMPLETED')
  save('fixture.json',{courseId:600,teacherId,documentId:document.document.id,ingestionJobId:document.job.id})
  console.log('200 student accounts, teacher, course, real indexed document and snapshot seeded');process.exit(0)
}
if(action==='inspect') {
  const states=compose(['ps','--format','json']).trim().split('\n').map(s=>JSON.parse(s));save('stack-health.json',states.map(s=>({service:s.Service,name:s.Name,state:s.State,health:s.Health})))
  assert(states.filter(s=>s.Service!=='minio-init').every(s=>s.Health==='healthy'))
  const containers=JSON.parse(docker(['inspect',...states.filter(s=>s.Service!=='minio-init').map(s=>s.Name)]))
  assert(containers.every(c=>c.HostConfig.Memory>0&&c.HostConfig.NanoCpus>0),'Every persistent service has resource limits')
  const workers=containers.filter(c=>c.Config.Labels['com.docker.compose.service']==='worker')
  assert.equal(workers.length,5)
  assert(workers.every(c=>Object.keys(c.NetworkSettings.Networks).length===1&&c.NetworkSettings.Networks[`${project}_data`]))
  assert(JSON.parse(docker(['network','inspect',`${project}_data`]))[0].Internal)
  save('network-resources.json',{workerCount:5,workersInternalOnly:true,allServicesResourceLimited:true})
  const admin=await new Client().login('release-admin','ADMIN',env.SEFORGE_BOOTSTRAP_ADMIN_PASSWORD)
  for(const path of ['/actuator/health','/actuator/health/readiness','/actuator/prometheus','/api-docs']){const r=await admin.request(path);assert.equal(r.status,200,path);save(path.includes('prometheus')?'metrics.json':path.includes('api-docs')?'openapi.json':'health.json',{path,status:r.status,contentType:r.headers['content-type'],sample:r.text.slice(0,300)})}
  const student=await new Client().login('P6-0000','STUDENT');assert.equal((await student.request('/actuator/prometheus')).status,403)
  assert.equal((await student.request('/api/v1/courses/600/analytics/dashboard')).status,403)
  const r=await student.request('/');assert(r.headers['content-security-policy']);assert(r.headers['strict-transport-security'])
  save('security-smoke.json',{secureCookie:true,csrf:true,studentMetricsForbidden:true,studentDashboardForbidden:true,csp:true,tls:true})
  const logServices=[`${project}-api-1`,...workers.map(c=>c.Name.replace(/^\//,''))]
  const logs=logServices.flatMap(name=>docker(['logs',name]).split(/\r?\n/).filter(Boolean))
  const parsed=logs.map(line=>JSON.parse(line));assert(parsed.length>0)
  const logText=logs.join('\n');for(const [name,value] of Object.entries(env))if(/PASSWORD|SECRET_KEY/.test(name))assert(!logText.includes(value))
  save('json-log-verification.json',{pass:true,services:logServices,parsedEvents:parsed.length,credentialsAbsent:true,freeFormMessagesSuppressed:true})
  console.log('Stack, HTTPS, secure sessions, metrics, OpenAPI and permissions PASS');process.exit(0)
}
if(action==='load') {
  const fixture=JSON.parse(readFileSync(fixtureFile,'utf8')),clients=[]
  // Login ramp is setup, not counted as traditional API latency. All 200 sessions stay alive.
  for(let i=0;i<200;i++){clients.push(await new Client().login(`P6-${String(i).padStart(4,'0')}`,'STUDENT'));if(i%25===24)console.log(`Authenticated ${i+1}/200`)}
  const teacher=await new Client().login('release-teacher','TEACHER')
  const conversations=await Promise.all(clients.slice(50,70).map(c=>c.data('/api/v1/courses/600/conversations','POST',{title:'Release streaming'})))
  const durations=[],failures=[],streams=[],jobs=[];let active=0,peak=0,stop=false
  stub('reset');stub('mode/heavy')
  const started=Date.now()
  const apiTasks=clients.slice(0,50).map(async(c,index)=>{while(!stop){const start=performance.now();try{const paths=['/api/v1/courses','/api/v1/courses/600','/api/v1/courses/600/assignments','/api/v1/courses/600/conversations'];const r=await c.request(paths[index%4]);durations.push(performance.now()-start);if(r.status!==200||r.json?.code!=='OK')failures.push({status:r.status,code:r.json?.code})}catch(e){failures.push({error:e.message})}await sleep(1000)}})
  const streamTasks=clients.slice(50,70).map(async(c,index)=>{active++;peak=Math.max(peak,active);const start=performance.now();try{const r=await c.request(`/api/v1/courses/600/conversations/${conversations[index].id}/messages`,'POST',{requestId:randomUUID(),content:'What are testable requirements?'});const events=[...r.text.matchAll(/^event:\s*(.+)$/gm)].map(m=>m[1].trim());const terminals=events.filter(e=>['done','error','cancelled'].includes(e));streams.push({status:r.status,terminalCount:terminals.length,terminal:terminals[0],citation:events.includes('citation'),durationMs:performance.now()-start});}catch(e){streams.push({error:e.message})}finally{active--}})
  // Five separate documents force actual parse/chunk/embedding/index work, not no-op snapshots.
  const runId=randomUUID()
  const jobTasks=Array.from({length:5},(_,i)=>(async()=>{try{const uploaded=await upload(teacher,fixture.courseId,`heavy-${Date.now()}-${i}.txt`,(`Document ${runId}-${i}: requirements must be clear, testable and auditable. `).repeat(1800));const job=await waitJob(teacher,uploaded.job.id);jobs.push({id:job.id,status:job.status,documentId:uploaded.document.id})}catch(e){jobs.push({status:'FAILED',error:e.message})}})())
  await Promise.all([...streamTasks,...jobTasks,sleep(120000)]);stop=true;await Promise.all(apiTasks);stub('mode/normal')
  const stats=JSON.parse(stub('stats'));const sorted=durations.sort((a,b)=>a-b),p95=sorted[Math.ceil(sorted.length*.95)-1];
  const result={startedAt:new Date(started).toISOString(),durationMs:Date.now()-started,authenticatedSessions:200,activeApiUsers:50,requestedStreams:20,clientPeakStreams:peak,provider:stats,traditional:{requests:durations.length,errors:failures.length,errorRate:failures.length/(durations.length||1),p95Ms:p95},streams,jobs,realProviderTtft:'UNVERIFIED'}
  result.pass=p95<500&&result.traditional.errorRate<.01&&stats.peakStreams>=20&&stats.peakHeavyEmbeddings>=5&&streams.length===20&&streams.every(s=>s.status===200&&s.terminalCount===1&&s.terminal==='done'&&s.citation)&&jobs.length===5&&jobs.every(j=>j.status==='COMPLETED')
  save('mixed-load-results.json',result);console.log(JSON.stringify({pass:result.pass,traditional:result.traditional,provider:stats,jobs:jobs.length}));assert(result.pass,'Mixed load gate failed');process.exit(0)
}
if(action==='recovery') {
  const teacher=await new Client().login('release-teacher','TEACHER'),results=[]
  stub('mode/heavy')
  const interrupted=await upload(teacher,600,`crash-${Date.now()}.txt`,'Crash recovery requirements. '.repeat(1500))
  for(let i=0;i<60;i++){if((await teacher.data(`/api/v1/jobs/${interrupted.job.id}`)).status==='RUNNING')break;await sleep(500)}
  assert.equal((await teacher.data(`/api/v1/jobs/${interrupted.job.id}`)).status,'RUNNING')
  compose(['kill','-s','SIGKILL','worker']);stub('mode/normal');compose(['up','-d','--scale','worker=5','worker'])
  const recovered=await waitJob(teacher,interrupted.job.id);assert.equal(recovered.status,'COMPLETED');assert(recovered.attempts>=2)
  results.push({fault:'worker SIGKILL',jobId:recovered.id,status:recovered.status,attempts:recovered.attempts})
  compose(['stop','worker'])
  const queued=await upload(teacher,600,`redis-${Date.now()}.txt`,'Redis durable outbox recovery. '.repeat(100))
  compose(['stop','redis']);await sleep(2000);compose(['start','redis']);await sleep(5000)
  // Lose only this disposable stack's transport stream; MySQL jobs/outbox stay intact.
  compose(['exec','-T','redis','sh','-c','redis-cli --no-auth-warning -a "$REDIS_PASSWORD" DEL seforge:jobs'])
  compose(['start','worker']);const redisJob=await waitJob(teacher,queued.job.id);assert.equal(redisJob.status,'COMPLETED')
  results.push({fault:'Redis stop/start + disposable stream loss',jobId:redisJob.id,status:redisJob.status,attempts:redisJob.attempts})
  stub('mode/fail');const failed=await upload(teacher,600,`ai-fail-${Date.now()}.txt`,'Provider failure must not become success.')
  const dead=await waitJob(teacher,failed.job.id);assert.equal(dead.status,'DEAD_LETTER')
  stub('mode/normal');const retry=await teacher.data(`/api/v1/courses/600/knowledge/documents/${failed.document.id}/reindex`,'POST',{})
  const retried=await waitJob(teacher,retry.id);assert.equal(retried.status,'COMPLETED')
  results.push({fault:'AI HTTP 503',failedJobId:dead.id,failedStatus:dead.status,retryJobId:retried.id,retryStatus:retried.status})
  for(const id of [recovered.id,redisJob.id,retried.id]){assert.equal((await teacher.data(`/api/v1/jobs/${id}`)).status,'COMPLETED')}
  const incomplete=Number(sql("SELECT COUNT(*) FROM async_job WHERE status NOT IN ('COMPLETED','DEAD_LETTER','CANCELLED');").trim())
  assert.equal(incomplete,0)
  save('fault-recovery-results.json',{pass:true,results,unfinishedJobs:incomplete,terminalStatesStable:true,transportScope:project})
  console.log('worker/Redis/AI fault recovery PASS');process.exit(0)
}
if(action==='backup') {
  const backup=resolve(out,'backup');mkdirSync(backup,{recursive:true})
  const freeze=Date.now();compose(['stop','frontend','api','worker'])
  const dumpOptions='--single-transaction --no-tablespaces --set-gtid-purged=OFF --skip-comments --order-by-primary'
  const dump=options=>compose(['exec','-T','mysql','sh','-c',`MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u "$MYSQL_USER" ${dumpOptions} ${options} "$MYSQL_DATABASE"`])
  const schema=dump('--no-data --skip-triggers'),data=dump('--no-create-info --skip-triggers --skip-extended-insert'),triggers=dump('--no-data --no-create-info --triggers')
  writeFileSync(resolve(backup,'schema.sql'),schema,{mode:0o600});writeFileSync(resolve(backup,'data.sql'),data,{mode:0o600});writeFileSync(resolve(backup,'triggers.sql'),triggers,{mode:0o600})
  const key=randomBytes(32),iv=randomBytes(12),cipher=createCipheriv('aes-256-gcm',key,iv)
  const config=readFileSync(envPath);const encrypted=Buffer.concat([cipher.update(config),cipher.final()]);const tag=cipher.getAuthTag()
  writeFileSync(resolve(backup,'configuration.enc'),Buffer.concat([iv,tag,encrypted]),{mode:0o600})
  // Test-only recovery key, kept separately from the backup archive. Production requires a vault.
  writeFileSync(resolve(out,'recovery-key.bin'),key,{mode:0o600})
  const mc=(projectName,command)=>docker(['run','--rm','--network',`${projectName}_data`,'--mount',`type=bind,source=${backup},target=/backup`,'-e',`MC_USER=${env.MINIO_ROOT_USER}`,'-e',`MC_PASSWORD=${env.MINIO_ROOT_PASSWORD}`,'--entrypoint','/bin/sh','quay.io/minio/mc:RELEASE.2025-04-16T18-13-26Z','-c',`mc alias set target http://minio:9000 "$MC_USER" "$MC_PASSWORD" >/dev/null && ${command}`])
  mc(project,`mc mirror target/${env.MINIO_APP_BUCKET} /backup/objects`)
  mc(project,`mc anonymous get-json target/${env.MINIO_APP_BUCKET} > /backup/bucket-policy.json`)
  const digest=value=>createHash('sha256').update(value).digest('hex')
  function objectManifest(dir,prefix=''){return readdirSync(dir,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?objectManifest(resolve(dir,entry.name),prefix+entry.name+'/'):[{key:prefix+entry.name,sha256:digest(readFileSync(resolve(dir,entry.name)))}]).sort((a,b)=>a.key.localeCompare(b.key))}
  const objects=objectManifest(resolve(backup,'objects'));assert(objects.length>0)
  const backupDone=Date.now();const restoreStart=Date.now(),restoreProject='seforge-p6-restore'
  const decipher=createDecipheriv('aes-256-gcm',key,iv);decipher.setAuthTag(tag);const recovered=Buffer.concat([decipher.update(encrypted),decipher.final()]);assert(recovered.equals(config))
  const restoreEnv=resolve(out,'restore.env');writeFileSync(restoreEnv,recovered.toString().replace('SEFORGE_HTTP_PORT=18443','SEFORGE_HTTP_PORT=18444').replace('https://localhost:18443','https://localhost:18444'),{mode:0o600})
  const restoreArgs=['compose','--project-name',restoreProject,'--env-file',restoreEnv,'-f','backend/docker/docker-compose.yml','-f','backend/docker/compose.production.yml','-f','backend/docker/compose.release-test.yml']
  const restore=(args,input)=>docker([...restoreArgs,...args],input)
  const restoreSql=statement=>restore(['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names -u "$MYSQL_USER" "$MYSQL_DATABASE"'],statement)
  const existing=docker(['ps','-aq','--filter',`label=com.docker.compose.project=${restoreProject}`]).trim()
  if(existing){
    // A failed infrastructure readiness attempt may be resumed only before any SQL restore.
    const appContainers=docker(['ps','-aq','--filter',`label=com.docker.compose.project=${restoreProject}`,'--filter','label=com.docker.compose.service=api']).trim()
    assert(!appContainers,'Never overwrite an existing restored application')
    assert.equal(Number(restoreSql('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();').trim()),0,'Never overwrite non-empty restore data')
  }
  // One-shot initialization exits successfully; it is not a persistent healthchecked service.
  restore(['up','-d','--wait','--wait-timeout','240','mysql','redis','minio','etcd','milvus','provider-stub','ai-egress'])
  restore(['run','--rm','minio-init'])
  restoreSql(schema);restoreSql(data);restoreSql(triggers)
  const restoredData=restore(['exec','-T','mysql','sh','-c',`MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u "$MYSQL_USER" ${dumpOptions} --no-create-info --skip-triggers --skip-extended-insert "$MYSQL_DATABASE"`])
  assert.equal(digest(restoredData),digest(data),'All table data must match before service restart')
  mc(restoreProject,`mc mirror /backup/objects target/${env.MINIO_APP_BUCKET}`)
  mc(restoreProject,`mc mirror target/${env.MINIO_APP_BUCKET} /backup/restored-objects`)
  assert.deepEqual(objectManifest(resolve(backup,'restored-objects')),objects)
  restore(['up','-d','--wait','--wait-timeout','240','api','worker','frontend'])
  const restoredClient=await new Client('https://localhost:18444').login('release-teacher','TEACHER')
  const documents=await restoredClient.data('/api/v1/courses/600/knowledge/documents')
  for(const document of documents.filter(d=>d.status!=='DELETED')) {
    const download=await restoredClient.request(`/api/v1/courses/600/knowledge/documents/${document.id}/download`);assert.equal(download.status,200)
    const job=await restoredClient.data(`/api/v1/courses/600/knowledge/documents/${document.id}/reindex`,'POST',{});assert.equal((await waitJob(restoredClient,job.id)).status,'COMPLETED')
  }
  const conversation=await restoredClient.data('/api/v1/courses/600/conversations','POST',{title:'Recovery citation'})
  const answer=await restoredClient.request(`/api/v1/courses/600/conversations/${conversation.id}/messages`,'POST',{requestId:randomUUID(),content:'What are testable requirements?'})
  assert.equal(answer.status,200);assert.match(answer.text,/event:citation|event: citation/);assert.equal((answer.text.match(/event:\s*done/g)||[]).length,1)
  const result={pass:true,backupStartedAt:new Date(freeze).toISOString(),backupDurationMs:backupDone-freeze,restoreDurationMs:Date.now()-restoreStart,rpoMs:0,rpoBasis:'Quiesced writers; complete SQL data and object SHA-256 manifests match',sqlSha256:digest(data),objects,configurationDecrypted:true,restoredDocuments:documents.length,indexRecovery:'Reindexed all documents into fresh real Milvus; authenticated Course QA citation and unique done verified',restoreProject}
  save('backup-restore-results.json',result);console.log(JSON.stringify({pass:true,rpoMs:0,rtoMs:result.restoreDurationMs,objects:objects.length,documents:documents.length}));process.exit(0)
}
throw new Error(`Unknown action: ${action}`)
