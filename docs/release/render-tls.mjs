import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
export function renderTls() {
  return readFileSync(resolve(import.meta.dirname,'../../frontend/nginx.conf'),'utf8')
    .replace('listen 8080;','listen 8443 ssl;')
    .replace('listen [::]:8080;','listen [::]:8443 ssl;\n    ssl_certificate /etc/nginx/tls/cert.pem;\n    ssl_certificate_key /etc/nginx/tls/key.pem;\n    ssl_protocols TLSv1.2 TLSv1.3;')
}
if(process.argv[1] && resolve(process.argv[1])===resolve(import.meta.filename)) {
  if(!process.argv[2]) throw new Error('Specify the output nginx TLS config path')
  writeFileSync(resolve(process.argv[2]),renderTls())
}
