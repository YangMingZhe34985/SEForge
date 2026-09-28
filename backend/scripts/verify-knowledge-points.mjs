// Explicit real-provider smoke. Run from backend after package:
// node --env-file=../.env scripts/verify-knowledge-points.mjs --launch-api
// Creates a clearly named synthetic teacher/course, never modifies existing teaching data.
import { randomUUID } from 'node:crypto'
import { spawn } from 'node:child_process'
import { createWriteStream } from 'node:fs'
import { mkdir, writeFile } from 'node:fs/promises'
import { once } from 'node:events'
import assert from 'node:assert/strict'
const base = process.env.AUDIT_BASE || 'http://127.0.0.1:18081'
const evidence = { timestamp: new Date().toISOString(), realProvider: true, checks: [] }
let api
await mkdir('target', { recursive: true })
class Client {
  cookies = new Map()
  async request(path, method = 'GET', data) {
    let csrf
    if (method !== 'GET') csrf = await this.request('/auth/csrf')
    const headers = { Cookie: [...this.cookies].map(([k,v]) => `${k}=${v}`).join('; ') }
    if (csrf) headers[csrf.headerName] = csrf.token
    if (data && !(data instanceof FormData)) headers['Content-Type'] = 'application/json'
    const start = Date.now()
    const r = await fetch(base + '/api/v1' + path, { method, headers,
      body: data instanceof FormData ? data : data ? JSON.stringify(data) : undefined, signal: AbortSignal.timeout(630000) })
    for (const c of r.headers.getSetCookie()) { const pair = c.split(';')[0]; const i=pair.indexOf('='); this.cookies.set(pair.slice(0,i),pair.slice(i+1)) }
    if(r.ok && !r.headers.get('content-type')?.includes('application/json'))return r.blob()
    const result = await r.json()
    evidence.checks.push({ path, method, status: r.status, code: result.code, traceId: result.traceId, elapsedMs: Date.now()-start })
    if (!r.ok) throw new Error(`${r.status} ${result.code}: ${result.message}`)
    return result.data
  }
}
function pdf(text='Software engineering requirements should be complete, consistent, clear and verifiable. Traceability connects each requirement to a test. Cohesion groups related responsibilities. Coupling should be minimized between modules.') {
  const stream=`BT /F1 12 Tf 30 700 Td (${text}) Tj ET`
  const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 1200 800] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`]
  let body='%PDF-1.4\n'; const offsets=[]
  objects.forEach((o,i)=>{offsets.push(Buffer.byteLength(body));body+=`${i+1} 0 obj\n${o}\nendobj\n`})
  const xref=Buffer.byteLength(body)
  body+=`xref\n0 6\n0000000000 65535 f \n${offsets.map(o=>`${String(o).padStart(10,'0')} 00000 n \n`).join('')}trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return new Blob([body], {type:'application/pdf'})
}
try {
  if (process.argv.includes('--launch-api')) {
    // Only this child is terminated below; existing API/worker/infrastructure are untouched.
    const log=createWriteStream('target/bug-9-api.log')
    api=spawn('java',['-Djdk.net.unixdomain.tmpdir=C:/seforge-test-no-unix-domain','-jar','target/seforge-backend.jar','--spring.profiles.active=dev'],{
      windowsHide:true, env:{...process.env,SERVER_ADDRESS:'127.0.0.1',SERVER_PORT:new URL(base).port,SEFORGE_JOBS_ENABLED:'false',SEFORGE_SONAR_ENABLED:'false'},stdio:['ignore','pipe','pipe'] })
    api.stdout.pipe(log);api.stderr.pipe(log)
    let healthy=false
    for(let i=0;i<90;i++) {
      if(api.exitCode!==null)throw Error('Temporary API exited; inspect target/bug-9-api.log')
      try { const r=await fetch(base+'/actuator/health',{signal:AbortSignal.timeout(1500)}); healthy=r.ok&&(await r.json()).status==='UP' } catch {}
      if(healthy)break
      await new Promise(r=>setTimeout(r,1000))
    }
    assert.ok(healthy,'API health')
  }
  const admin=new Client()
  await admin.request('/auth/login','POST',{identifier:process.env.SEFORGE_BOOTSTRAP_ADMIN_USERNAME,password:process.env.SEFORGE_BOOTSTRAP_ADMIN_PASSWORD,portal:'ADMIN'})
  const suffix=Date.now(),username='kp-smoke-'+suffix,password=randomUUID()+'aA1!'
  await admin.request('/admin/users','POST',{username,password,email:username+'@example.invalid',displayName:'Knowledge point smoke teacher',accountType:'TEACHER',roles:['USER']})
  const semester=await admin.request('/admin/semesters','POST',{name:'BUG9 smoke '+suffix,startsOn:'2026-09-01',endsOn:'2027-01-31',status:'PLANNED'})
  const teacher=new Client()
  await teacher.request('/auth/login','POST',{identifier:username,password,portal:'TEACHER'})
  const course=await teacher.request('/courses','POST',{name:'BUG9 synthetic smoke '+suffix,semesterId:semester.id,description:'Synthetic real-provider verification; not teaching data'})
  const root=`/courses/${course.id}`
  const chapter=await teacher.request(root+'/chapters','POST',{title:'Software engineering principles',description:'Synthetic material',sortOrder:1})
  evidence.courseId=course.id;evidence.chapterId=chapter.id
  let expectedDocuments=1
  if(process.env.AUDIT_SOURCE_COURSE && process.env.AUDIT_SOURCE_CHAPTER){
    const sourceRoot=`/courses/${process.env.AUDIT_SOURCE_COURSE}`
    const originals=(await admin.request(sourceRoot+'/knowledge/documents')).filter(d=>String(d.chapterId)===process.env.AUDIT_SOURCE_CHAPTER&&d.status==='READY')
    assert.ok(originals.length>0)
    expectedDocuments=originals.length
    for(const d of originals){
      assert.ok(d.resourceId)
      const blob=await admin.request(sourceRoot+`/resources/${d.resourceId}/download`)
      const file=new FormData();file.append('file',blob,d.name)
      await teacher.request(root+`/resources/upload?chapterId=${chapter.id}`,'POST',file)
    }
  }else{
    expectedDocuments=Number(process.env.AUDIT_DOCUMENT_COUNT||1)
    assert.ok(Number.isInteger(expectedDocuments)&&expectedDocuments>=1&&expectedDocuments<=3)
    const materials=[
      'Requirements engineering: identify stakeholder needs, document measurable acceptance criteria, resolve conflicts and maintain traceability from requirements to tests. Validate requirements with stakeholders. ',
      'Software design: separate responsibilities, prefer high cohesion and low coupling. Encapsulation hides internal details behind stable interfaces. Architecture decisions balance quality attributes and constraints. ',
      'Software testing: use equivalence partitions and boundary value analysis. Distinguish verification from validation. Regression tests protect existing behavior. Test planning includes normal cases and failure cases. ',
    ]
    for(let i=0;i<expectedDocuments;i++){
      const file=new FormData();file.append('file',expectedDocuments===1?pdf():pdf(materials[i].repeat(25)),`knowledge-point-smoke-${i+1}.pdf`)
      await teacher.request(root+`/resources/upload?chapterId=${chapter.id}`,'POST',file)
    }
  }
  let doc
  for(let i=0;i<90;i++) {
    const documents=await teacher.request(root+'/knowledge/documents')
    doc=documents.find(d=>d.status!=='READY')||documents[0]
    if(documents.length===expectedDocuments&&documents.every(d=>d.status==='READY'))break
    if(['FAILED','CANCELLED'].includes(doc?.status))break
    await new Promise(r=>setTimeout(r,2000))
  }
  assert.equal(doc.status,'READY');evidence.documentId=doc.id
  console.log('PDF ingestion READY; invoking real REASONING provider')
  const path=root+`/chapters/${chapter.id}/knowledge-point-drafts`
  const draft=await teacher.request(path,'POST')
  assert.ok(draft.points.length>0)
  assert.equal(draft.coverage.documents,expectedDocuments)
  evidence.coverage=draft.coverage
  assert.equal((await teacher.request(root+'/knowledge-points')).length,0)
  const ids=new Set(draft.sources.map(s=>String(s.id)))
  assert.ok(draft.points.every(p=>p.sourceIds.length&&p.sourceIds.every(id=>ids.has(String(id)))))
  evidence.aiTraceId=draft.traceId;evidence.pointCount=draft.points.length;evidence.sourceCount=draft.sources.length
  evidence.inputCharacters=draft.sources.reduce((sum,s)=>sum+s.quote.length,0)
  const sourceDocuments=new Map(draft.sources.map(s=>[String(s.id),String(s.documentId)]))
  evidence.citedDocumentCount=new Set(draft.points.flatMap(p=>p.sourceIds.map(id=>sourceDocuments.get(String(id))))).size
  const selected={...draft.points[0],name:'Teacher reviewed: '+draft.points[0].name}
  const confirmed=await teacher.request(path+`/${draft.id}/confirm`,'POST',{points:[selected]})
  assert.deepEqual(await teacher.request(path+`/${draft.id}/confirm`,'POST',{points:[selected]}),confirmed)
  const points=await teacher.request(root+'/knowledge-points')
  assert.equal(points.length,1);assert.equal(points[0].title,selected.name)
  assert.ok(JSON.parse(points[0].sourceCitations).length>0)
  evidence.result='PASS'
  console.log(JSON.stringify({result:'PASS',courseId:course.id,aiTraceId:draft.traceId,points:draft.points.length,confirmed:confirmed.length}))
} catch(e) { evidence.result='FAIL';evidence.error=e.message;console.error(e.message);process.exitCode=1 }
finally {
  await writeFile(process.env.AUDIT_OUTPUT || 'target/bug-9-real-verification.json',JSON.stringify(evidence,null,2))
  if(api&&api.exitCode===null){const stopped=once(api,'exit');api.kill();await stopped}
}
