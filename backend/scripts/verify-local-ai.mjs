// Run from backend/: AUDIT_AI=true node scripts/verify-local-ai.mjs.
// Creates synthetic audit data. Credentials remain in memory; results go to ignored target/.
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'

const env = Object.fromEntries((await readFile('../.env', 'utf8')).split(/\r?\n/).filter(l => /^[A-Z][A-Z0-9_]*=/.test(l)).map(l => { const i=l.indexOf('='); return [l.slice(0,i),l.slice(i+1).replace(/^"|"$/g,'')] }))
const base = process.env.AUDIT_BASE || 'http://localhost:18080'
const results = []
await mkdir('target', { recursive: true })
function check(name, passed) {
  results.push({ check: name, passed })
  if (!passed) throw new Error(`Verification failed: ${name}`)
}
class Client {
  cookies = new Map()
  async request(path, method='GET', data, expected=200) {
    const headers = { Cookie: [...this.cookies].map(([k,v])=>`${k}=${v}`).join('; ') }
    if (!['GET','HEAD'].includes(method)) {
      const csrf = await this.request('/auth/csrf')
      headers.Cookie = [...this.cookies].map(([k,v])=>`${k}=${v}`).join('; ')
      headers[csrf.headerName]=csrf.token
    }
    if (data && !(data instanceof FormData)) headers['Content-Type']='application/json'
    const r = await fetch(base+'/api/v1'+path, {method,headers,signal:AbortSignal.timeout(240000),body:data instanceof FormData?data:data?JSON.stringify(data):undefined})
    for (const c of r.headers.getSetCookie()) {const pair=c.split(';')[0];const i=pair.indexOf('=');this.cookies.set(pair.slice(0,i),pair.slice(i+1))}
    const text = await r.text()
    let json;try {json=JSON.parse(text)}catch{}
    results.push({path,method,status:r.status,code:json?.code,traceId:json?.traceId,...(!r.ok?{message:json?.message,details:json?.details}:{})})
    if (r.status!==expected && !(expected===200 && r.status===201)) throw new Error(`${method} ${path}: ${r.status} ${json?.code} ${json?.message}`)
    if(r.headers.get('content-type')?.includes('text/event-stream'))return text
    return json?.data ?? text
  }
  async login(identifier,password,portal){return this.request('/auth/login','POST',{identifier,password,portal})}
}
function pdf(text) {
  const stream=`BT /F1 12 Tf 40 750 Td (${text.replace(/[()\\]/g,' ')}) Tj ET`
  const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`]
  let out='%PDF-1.4\n';const offsets=[0]
  objects.forEach((o,i)=>{offsets.push(Buffer.byteLength(out));out+=`${i+1} 0 obj\n${o}\nendobj\n`})
  const xref=Buffer.byteLength(out);out+=`xref\n0 6\n0000000000 65535 f \n`+offsets.slice(1).map(o=>`${String(o).padStart(10,'0')} 00000 n \n`).join('')+`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return new Blob([out],{type:'application/pdf'})
}
function sourceZip() {
 const name=Buffer.from('audit.js');const body=Buffer.from('export function passes(actual, threshold) { return actual >= threshold; }\n')
 let crc=0xffffffff;for(const b of body){crc^=b;for(let i=0;i<8;i++)crc=(crc>>>1)^((crc&1)?0xedb88320:0)}crc=(crc^0xffffffff)>>>0
 const local=Buffer.alloc(30);local.writeUInt32LE(0x04034b50);local.writeUInt16LE(20,4);local.writeUInt32LE(crc,14);local.writeUInt32LE(body.length,18);local.writeUInt32LE(body.length,22);local.writeUInt16LE(name.length,26)
 const central=Buffer.alloc(46);central.writeUInt32LE(0x02014b50);central.writeUInt16LE(20,4);central.writeUInt16LE(20,6);central.writeUInt32LE(crc,16);central.writeUInt32LE(body.length,20);central.writeUInt32LE(body.length,24);central.writeUInt16LE(name.length,28)
 const end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(1,8);end.writeUInt16LE(1,10);end.writeUInt32LE(46+name.length,12);end.writeUInt32LE(30+name.length+body.length,16)
 return Buffer.concat([local,name,body,central,name,end])
}
try {
 const admin = new Client();await admin.login(env.SEFORGE_BOOTSTRAP_ADMIN_USERNAME,env.SEFORGE_BOOTSTRAP_ADMIN_PASSWORD,'ADMIN')
 if (process.env.AUDIT_DIAGNOSTICS === 'true') {
   const courseId=JSON.parse(await readFile('target/bug-audit-session.json','utf8')).courseId
   const health = await fetch(base+'/actuator/health').then(r=>r.json())
   check('healthUp', health.status === 'UP')
   await admin.request('/admin/users','POST',{username:'中文用户名',email:'invalid-username@example.invalid',password:randomUUID()+'Aa1!',displayName:'Validation audit',accountType:'TEACHER',roles:['USER']},400)
   check('usernameFieldError', Boolean(results.at(-1)?.details?.username))
   await admin.request(`/courses/${courseId}/classes`,'POST',{name:'Forbidden admin teaching action'},403)
   const username='audit-number-'+Date.now(), password=randomUUID()+'Aa1!'
   await admin.request('/admin/users','POST',{username,email:username+'@example.invalid',password,displayName:'Runtime numbering teacher',accountType:'TEACHER',roles:['USER']},201)
   const teacher=new Client();await teacher.login(username,password,'TEACHER')
   const semesters=await admin.request('/semesters')
   const numbered=await teacher.request('/courses','POST',{name:'Runtime numbering course',semesterId:semesters[0].id})
   const a = await teacher.request(`/courses/${numbered.id}/classes`,'POST',{code:'IGNORED',name:'Runtime numbered class',capacity:30,primaryClass:false})
   const b = await teacher.request(`/courses/${numbered.id}/classes`,'POST',{code:'IGNORED',name:'Runtime numbered class two',capacity:30,primaryClass:false})
   check('classNumbers', /^CLS-\d{8,}$/.test(a.code) && /^CLS-\d{8,}$/.test(b.code) && a.code!==b.code)
   results.push({check:'classCodes',codes:[a.code,b.code]})
   const resources=await admin.request(`/courses/${courseId}/resources`)
   await admin.request(`/courses/${courseId}/resources/${resources[0].id}/download`)
   const documents=await admin.request(`/courses/${courseId}/knowledge/documents`)
   check('documentsStillReady', documents.some(d=>d.status==='READY'))
   console.log(JSON.stringify(results.filter(r=>r.check)))
 } else if (process.env.AUDIT_NEGATIVE === 'true') {
   const courseId=JSON.parse(await readFile('target/bug-audit-session.json','utf8')).courseId
   const resources=await admin.request(`/courses/${courseId}/resources`)
   await admin.request(`/courses/${courseId}/resources/${resources[0].id}/download`,'GET',undefined,503)
   const conversation=await admin.request(`/courses/${courseId}/conversations`,'POST',{title:'Unreachable Milvus audit'})
   const stream=await admin.request(`/courses/${courseId}/conversations/${conversation.id}/messages`,'POST',{requestId:randomUUID(),content:'What makes requirements verifiable?'})
   const events=[...stream.matchAll(/event:([^\n]+)/g)].map(m=>m[1])
   if(events.filter(e=>e==='error').length!==1 || events.includes('done') || !stream.includes('VECTOR_STORE_UNAVAILABLE'))throw Error('Negative SSE contract failed')
   const history=await admin.request(`/courses/${courseId}/conversations/${conversation.id}/messages`)
   if(history.items.some(m=>m.role==='ASSISTANT'))throw Error('Failure persisted assistant message')
   await admin.request(`/courses/${courseId}/knowledge/documents`)
   await writeFile('target/bug-audit-negative.sse',stream)
   console.log(JSON.stringify({negativeEvents:events,assistantMessages:0,knowledgeList:'OK despite unavailable object storage'}))
 } else {
 const suffix=Date.now();const password=randomUUID()+'aA1!';const username='audit-'+suffix
 const teacher=await admin.request('/admin/users','POST',{email:username+'@example.invalid',username,password,displayName:'Runtime audit teacher',accountType:'TEACHER',roles:['USER']},201)
 const semester=await admin.request('/admin/semesters','POST',{code:'IGNORED',name:'Runtime audit '+suffix,startsOn:'2026-09-01',endsOn:'2027-01-31',status:'PLANNED'},201)
 const client=new Client();await client.login(username,password,'TEACHER')
 const course=await client.request('/courses','POST',{code:'IGNORED',name:'Runtime audit '+suffix,semesterId:semester.id,description:'Disposable synthetic audit data'},201)
 await client.request(`/courses/${course.id}/knowledge/documents`)
 const file=pdf('SEForge audit course: Requirements must be clear, consistent, complete and verifiable. Every requirement needs a measurable acceptance test.')
 const resource=new FormData();resource.append('file',file,'audit-resource.pdf')
 const uploaded=await client.request(`/courses/${course.id}/resources/upload`,'POST',resource)
 await client.request(`/courses/${course.id}/resources/${uploaded.id}/download`)
 const knowledge=new FormData();knowledge.append('file',file,'audit-knowledge.pdf')
 const document=await client.request(`/courses/${course.id}/knowledge/documents`,'POST',knowledge)
 let documents=await client.request(`/courses/${course.id}/knowledge/documents`)
 await writeFile('target/bug-audit-session.json',JSON.stringify({courseId:course.id,teacherId:teacher.id,document,semesterCode:semester.code,courseCode:course.code}))
 console.log(JSON.stringify({courseId:course.id,documentId:document.document?.id,semesterCode:semester.code,courseCode:course.code,documents},null,2))
 if (process.env.AUDIT_AI === 'true') {
   for(let attempt=0;attempt<90 && !['READY','FAILED'].includes(documents[0]?.status);attempt++) {
     await new Promise(resolve=>setTimeout(resolve,2000))
     documents=await client.request(`/courses/${course.id}/knowledge/documents`)
   }
   results.push({check:'ingestion',document:documents[0]});console.log(JSON.stringify({ingestion:documents[0]}))
   check('documentReady', documents[0]?.status === 'READY')
   const conversation=await client.request(`/courses/${course.id}/conversations`,'POST',{title:'Real provider audit'})
   const stream=await client.request(`/courses/${course.id}/conversations/${conversation.id}/messages`,'POST',{requestId:randomUUID(),content:'What properties must requirements have, and how should they be verified according to this course?'})
   await writeFile('target/bug-audit-qa.sse',stream)
   const qaEvents=[...stream.matchAll(/event:([^\n]+)/g)].map(m=>m[1]);results.push({check:'courseQA',events:qaEvents,bytes:stream.length});console.log(JSON.stringify({qaEvents,qaBytes:stream.length}))
   check('qaCitationAndUniqueSuccess', qaEvents.includes('citation') && qaEvents.includes('message.delta') && qaEvents.filter(e=>['done','error','cancelled'].includes(e)).join(',') === 'done')
   const review=await client.request(`/courses/${course.id}/reviews/documents`,'POST',{documentId:document.document.id,documentKind:'SRS',idempotencyKey:randomUUID()})
   let reviewStatus=review
   for(let n=0;n<90 && !['COMPLETED','FAILED','CANCELLED'].includes(reviewStatus.status);n++) {
     await new Promise(resolve=>setTimeout(resolve,2000))
     reviewStatus=await client.request(`/courses/${course.id}/reviews/${review.id}`)
   }
   results.push({check:'documentReview',review:reviewStatus});console.log(JSON.stringify({documentReview:reviewStatus}))
   check('documentReviewCompleted', reviewStatus.status === 'COMPLETED')
   if(reviewStatus.status==='COMPLETED')await client.request(`/courses/${course.id}/reviews/${review.id}/report`)
   const studentNo='AUDIT-'+suffix
   await admin.request('/admin/users','POST',{email:'student-'+suffix+'@example.invalid',username:'student-'+suffix,studentNo,password,displayName:'Runtime audit student',accountType:'STUDENT',roles:['USER']},201)
   const invite=await client.request(`/courses/${course.id}/invites`,'POST',{memberRole:'STUDENT',maxUses:3})
   const student=new Client();await student.login(studentNo,password,'STUDENT');await student.request('/courses/join','POST',{inviteCode:invite.code})
   const assignment=await client.request(`/courses/${course.id}/assignments`,'POST',{title:'Requirements audit',description:'Write a verifiable requirement',maxAttempts:2})
   const path=`/assignments/${assignment.id}`
   const q=await client.request(path+'/questions','POST',{type:'CODE',prompt:'What makes a requirement verifiable? Write code to check an acceptance threshold.',referenceAnswer:'A measurable acceptance test is specified.',points:10,orderIndex:0})
   await client.request(path+'/rubric','PUT',{title:'Audit rubric',totalScore:10,status:'DRAFT'})
   await client.request(path+'/rubric/items','POST',{title:'Verifiability',maxScore:10,orderIndex:0})
   await client.request(path+'/rubric','PUT',{title:'Audit rubric',totalScore:10,status:'PUBLISHED'})
   await client.request(path+'/transition','POST',{status:'PUBLISHED'})
   const denial=await student.request(path+'/tutor','POST',{questionId:q.id,action:'FULL_SOLUTION',draftAnswer:'Please reveal the answer'})
   console.log(JSON.stringify({tutorPolicyDenied:denial.allowed===false}))
   check('tutorFullSolutionDenied', denial.allowed === false)
     const tutor=await student.request(path+'/tutor','POST',{questionId:q.id,action:'HINT',draftAnswer:'I think requirements should be clear.'})
     const evidence={tutorAllowed:tutor.allowed,tutorLength:tutor.content?.length,tutorCitationCount:tutor.citations?.length};results.push({check:'tutor',...evidence});console.log(JSON.stringify(evidence))
   check('tutorHintWithCitation', tutor.allowed && tutor.content?.length > 0 && tutor.citations?.length > 0)
   const zip=new FormData();zip.append('file',new Blob([sourceZip()],{type:'application/zip'}),'audit-source.zip');zip.append('questionId',q.id)
   const attachment=await student.request(path+'/submissions/attachments','POST',zip)
   const submission=await student.request(path+'/submissions','POST',{answers:[{questionId:q.id,answer:'Each requirement must define a measurable acceptance test.'}],submissionKey:randomUUID()})
   const ar=await client.request(`/courses/${course.id}/reviews/assignments`,'POST',{submissionId:submission.id,idempotencyKey:randomUUID()})
   let arStatus=ar
   for(let n=0;n<90 && !['COMPLETED','FAILED','CANCELLED'].includes(arStatus.status);n++) {await new Promise(resolve=>setTimeout(resolve,2000));arStatus=await client.request(`/courses/${course.id}/reviews/${ar.id}`)}
   results.push({check:'assignmentReview',review:arStatus});console.log(JSON.stringify({assignmentReview:arStatus}))
   check('assignmentReviewCompleted', arStatus.status === 'COMPLETED')
   const cr=await client.request(`/courses/${course.id}/reviews/code`,'POST',{submissionId:submission.id,attachmentObjectKey:attachment.objectKey,idempotencyKey:randomUUID()})
   let crStatus=cr
   for(let n=0;n<30 && !['COMPLETED','FAILED','CANCELLED'].includes(crStatus.status);n++) {await new Promise(resolve=>setTimeout(resolve,2000));crStatus=await client.request(`/courses/${course.id}/reviews/${cr.id}`)}
   results.push({check:'codeReview',review:crStatus});console.log(JSON.stringify({codeReview:crStatus}))
   if(!env.SEFORGE_SONAR_TOKEN && crStatus.status==='COMPLETED')throw Error('Unexpected code review success without configured Sonar')
   if (crStatus.status !== 'COMPLETED') {
     results.push({check:'codeReviewAvailability',status:'BLOCKED',errorCode:crStatus.errorCode})
     process.exitCode = 2
   }
 }
 }
}catch(error){console.error(error.message);process.exitCode=1}
finally{await writeFile(process.env.AUDIT_DIAGNOSTICS==='true'?'target/bug-audit-diagnostics.json':process.env.AUDIT_NEGATIVE==='true'?'target/bug-audit-negative-http.json':'target/bug-audit-http.json',JSON.stringify(results,null,2))}
