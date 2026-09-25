// Controllable engineering provider, never a production model or semantic benchmark.
import http from 'node:http'
let mode = 'normal'
let activeStreams = 0, peakStreams = 0, activeEmbeddings = 0, peakEmbeddings = 0
let activeHeavyEmbeddings = 0, peakHeavyEmbeddings = 0
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
http.createServer(async (req, res) => {
  if (req.url === '/health') { res.end('ok'); return }
  if (req.url === '/stats') { res.setHeader('Content-Type','application/json'); res.end(JSON.stringify({ mode, activeStreams, peakStreams, activeEmbeddings, peakEmbeddings, activeHeavyEmbeddings, peakHeavyEmbeddings })); return }
  if (req.url === '/reset') { peakStreams=activeStreams;peakEmbeddings=activeEmbeddings;peakHeavyEmbeddings=activeHeavyEmbeddings;res.end('ok');return }
  if (req.url.startsWith('/mode/')) { mode=req.url.slice(6); res.end('ok'); return }
  let body=''; for await (const part of req) body+=part
  let data; try { data=JSON.parse(body) } catch { res.writeHead(400); res.end(); return }
  if (mode==='fail') { res.writeHead(503); res.end('{"error":{"message":"Controlled unavailable provider"}}'); return }
  if (req.url.endsWith('/embeddings')) {
    activeEmbeddings++; peakEmbeddings=Math.max(peakEmbeddings,activeEmbeddings)
    const input=Array.isArray(data.input)?data.input:[data.input]
    const heavy=input.join(' ').length>1000
    if(heavy){activeHeavyEmbeddings++;peakHeavyEmbeddings=Math.max(peakHeavyEmbeddings,activeHeavyEmbeddings)}
    try {
      if (mode==='heavy') await sleep(2500)
      res.setHeader('Content-Type','application/json')
      res.end(JSON.stringify({object:'list',model:data.model,data:input.map((_,index)=>({object:'embedding',index,embedding:[1,...Array(31).fill(0)]})),usage:{prompt_tokens:input.length*10,total_tokens:input.length*10}}))
    } finally { activeEmbeddings--;if(heavy)activeHeavyEmbeddings-- }
    return
  }
  if(data.stream) {
    res.writeHead(200,{'Content-Type':'text/event-stream','Cache-Control':'no-cache'})
    activeStreams++; peakStreams=Math.max(peakStreams,activeStreams)
    try {
      for(let i=0;i<30;i++) {
        if(res.destroyed) break
        res.write(`data: ${JSON.stringify({id:'stub',object:'chat.completion.chunk',model:data.model,choices:[{index:0,delta:{content:i===0?'依据课程资料 [C1]。':'工程验证内容。'},finish_reason:null}]})}\n\n`)
        await sleep(1000)
      }
      res.end(`data: ${JSON.stringify({id:'stub',object:'chat.completion.chunk',model:data.model,choices:[{index:0,delta:{},finish_reason:'stop'}]})}\n\ndata: [DONE]\n\n`)
    } finally { activeStreams-- }
  } else {
    res.setHeader('Content-Type','application/json')
    res.end(JSON.stringify({id:'stub',object:'chat.completion',model:data.model,choices:[{index:0,message:{role:'assistant',content:'Controlled provider response'},finish_reason:'stop'}],usage:{prompt_tokens:10,completion_tokens:5,total_tokens:15}}))
  }
}).listen(8090,'0.0.0.0')
