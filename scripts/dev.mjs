// One supervisor, separate API/worker JVMs. Never removes volumes or stops pre-existing services.
import { spawn, spawnSync } from 'node:child_process'
import { copyFileSync, createWriteStream, existsSync, mkdirSync, unlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import net from 'node:net'

const root = fileURLToPath(new URL('../', import.meta.url))
const baseEnv = { ...process.env }
if (!existsSync(resolve(root, '.env'))) throw new Error('请先复制 .env.example 为 .env 并填写本地凭据。')
process.loadEnvFile(resolve(root, '.env'))
const port = (name, fallback) => {
  const value = Number(process.env[name] || fallback)
  if (!Number.isInteger(value) || value < 1024 || value > 65535) throw new Error(`${name} 必须是 1024–65535 的端口`)
  return value
}
const apiPort = port('SEFORGE_DEV_API_PORT', 8080)
const workerPort = port('SEFORGE_DEV_WORKER_PORT', 8082)
const vitePort = port('SEFORGE_DEV_VITE_PORT', 5173)
if (new Set([apiPort, workerPort, vitePort]).size !== 3) throw new Error('API / Worker / Vite 端口不能重复')
const compose = ['compose', '--env-file', resolve(root, '.env'), '-f', resolve(root, 'backend/docker/docker-compose.yml')]
const infrastructure = ['mysql', 'redis', 'minio', 'etcd', 'milvus']
const children = new Set()
const startedServices = []
const runtimeJar = resolve(root, `logs/dev/backend-${process.pid}.jar`)
let javaPipeArgs = []
let stopping = false
let failed = false
mkdirSync(resolve(root, 'logs/dev'), { recursive: true })

function command(program, args, options = {}) {
  const { name, cwd = root, env = process.env, capture = false } = options
  const log = name ? createWriteStream(resolve(root, `logs/dev/${name}.log`), { flags: 'w' }) : null
  // Only fixed repository commands use cmd.exe; credentials are environment variables, never shell text.
  const child = spawn(program, args, { cwd, env, windowsHide: true,
    shell: process.platform === 'win32' && program.endsWith('.cmd'),
    stdio: ['ignore', capture || log ? 'pipe' : 'inherit', log ? 'pipe' : 'inherit'] })
  children.add(child)
  let output = ''
  if (capture) child.stdout.on('data', data => { output += data.toString() })
  if (log) { child.stdout.pipe(log); child.stderr.pipe(log) }
  const done = new Promise((accept, reject) => {
    child.once('error', reject)
    child.once('exit', code => {
      children.delete(child)
      log?.end()
      if (name && !stopping) {
        failed = true
        console.error(`${name} 已退出 (${code})，请查看 logs/dev/${name}.log`)
      }
      code === 0 || stopping ? accept(output) : reject(new Error(`${program} 退出码 ${code}`))
    })
  })
  if (name) done.catch(() => { failed = true })
  return { child, done }
}

async function freePort(value) {
  await new Promise((accept, reject) => {
    const server = net.createServer()
    server.once('error', () => reject(new Error(`端口 ${value} 已占用；请停止自己的旧开发进程，或设置 SEFORGE_DEV_*_PORT。不会终止已有进程。`)))
    server.listen(value, '127.0.0.1', () => server.close(accept))
  })
}

async function waitFor(url, jsonHealth = true) {
  const deadline = Date.now() + 180_000
  while (Date.now() < deadline && !stopping) {
    if (failed) throw new Error('开发进程提前退出')
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(2000) })
      if (response.ok && (!jsonHealth || (await response.json()).status === 'UP')) return
    } catch { /* Process is still starting. */ }
    await new Promise(accept => setTimeout(accept, 1000))
  }
  throw new Error(`启动未就绪：${url}，请检查 logs/dev/`)
}

async function cleanup() {
  if (stopping) return
  stopping = true
  for (const child of [...children]) {
    if (!child.pid) continue
    if (process.platform === 'win32') {
      await new Promise(accept => spawn('taskkill', ['/PID', String(child.pid), '/T', '/F'],
        { windowsHide: true, stdio: 'ignore' }).once('exit', accept))
    } else child.kill('SIGTERM')
  }
  if (startedServices.length) {
    await command('docker', [...compose, 'stop', ...startedServices]).done.catch(() => undefined)
  }
  try { unlinkSync(runtimeJar) } catch { /* May not have reached the build, or shutdown is still releasing it. */ }
  console.log('开发子进程已停止；原有基础设施及所有数据 volume 保留。')
}
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => void cleanup().then(() => process.exit(0)))

try {
  await Promise.all([apiPort, workerPort, vitePort].map(freePort))
  if (process.platform === 'win32') {
    const probe = (args) => spawnSync('java', [...args, resolve(root, 'scripts/DevHttpClientProbe.java')],
      { encoding: 'utf8', windowsHide: true, timeout: 20_000 })
    const nativeProbe = probe([])
    if (nativeProbe.status !== 0) {
      // OpenJDK's Windows PipeImpl falls back to authenticated loopback TCP when
      // its Unix-domain listener cannot bind. This path intentionally does not exist.
      javaPipeArgs = [`-Djdk.net.unixdomain.tmpdir=${resolve(tmpdir(), `seforge-pipe-${process.pid}-${Date.now()}`)}`]
      const fallback = probe(javaPipeArgs)
      writeFileSync(resolve(root, 'logs/dev/http-client-probe.log'),
        `nativeExit=${nativeProbe.status}; tcpExit=${fallback.status}\n${nativeProbe.stderr || nativeProbe.error?.code || ''}\n${fallback.stderr || fallback.error?.code || ''}`)
      if (fallback.status !== 0) throw new Error('JDK HTTP Client 初始化失败；请检查 JDK 21 和 logs/dev/http-client-probe.log')
      console.log('检测到 Windows JDK Pipe 问题；API/Worker 将使用进程级 loopback TCP 兼容模式。')
    }
  }
  await command('docker', [...compose, 'config', '--quiet']).done
  const existing = (await command('docker', [...compose, 'ps', '--services', '--status', 'running'], { capture: true }).done).trim().split(/\r?\n/)
  startedServices.push(...infrastructure.filter(service => !existing.includes(service)))
  console.log('启动并等待 MySQL / Redis / MinIO / Milvus；不会启动生产 API/Worker 容器。')
  await command('docker', [...compose, 'up', '-d', '--wait', '--wait-timeout', '180', ...infrastructure]).done
  // This is a one-shot job, not a long-running healthcheck target. Including it in
  // `up --wait` races a successful exit against Compose's running-state check.
  await command('docker', [...compose, 'run', '--rm', '--no-deps', 'minio-init']).done
  await command(process.platform === 'win32' ? 'mvnw.cmd' : './mvnw', ['-DskipTests', 'package'], { cwd: resolve(root, 'backend') }).done
  // npm install reconciles the lock without deleting binaries held by another dev server on Windows.
  await command(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['install'], { cwd: resolve(root, 'frontend'), env: baseEnv }).done
  copyFileSync(resolve(root, 'backend/target/seforge-backend.jar'), runtimeJar)
  const backendEnv = { ...process.env, SERVER_ADDRESS: '127.0.0.1', SEFORGE_SONAR_ENABLED: 'false',
    SEFORGE_DEV_ALLOWED_ORIGINS: `http://localhost:${vitePort},http://127.0.0.1:${vitePort}` }
  const startBackend = (name, profile, serverPort, jobs) => command('java', [...javaPipeArgs, '-jar', runtimeJar, `--spring.profiles.active=${profile}`],
    { name, cwd: resolve(root, 'backend'), env: { ...backendEnv, SERVER_PORT: String(serverPort), SEFORGE_JOBS_ENABLED: String(jobs) } })
  startBackend('api', 'dev', apiPort, false)
  await waitFor(`http://127.0.0.1:${apiPort}/actuator/health`)
  startBackend('worker', 'dev,worker', workerPort, true)
  await waitFor(`http://127.0.0.1:${workerPort}/actuator/health`)
  command(process.execPath, ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', String(vitePort), '--strictPort'],
    { name: 'vite', cwd: resolve(root, 'frontend'), env: { ...baseEnv, SEFORGE_API_PROXY: `http://127.0.0.1:${apiPort}` } })
  await waitFor(`http://127.0.0.1:${vitePort}`, false)
  console.log(`SEForge READY: http://127.0.0.1:${vitePort} (API ${apiPort}, Worker ${workerPort})`)
  console.log('日志：logs/dev/；Ctrl+C 停止本次启动的进程和基础设施（不删除数据）。')
  if (process.argv.includes('--smoke')) await cleanup()
  else while (!stopping) {
    if (failed) throw new Error('开发进程异常退出')
    await new Promise(accept => setTimeout(accept, 1000))
  }
} catch (error) {
  console.error(error.message)
  await cleanup()
  process.exitCode = 1
}
