const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const home = '/data/data/com.termux/files/home';
const args = process.argv.slice(2);
const action = args.shift();
async function request(route, body) {
  const token=fs.readFileSync(`${home}/dsh-setup/phone-bridge-token`,'utf8').trim();
  const json=body===undefined?null:JSON.stringify(body);
  return new Promise((resolve,reject)=>{
    const call=http.request({hostname:'127.0.0.1',port:3082,path:route,method:json===null?'GET':'POST',headers:{Authorization:`Bearer ${token}`,...(json===null?{}:{'Content-Type':'application/json','Content-Length':Buffer.byteLength(json)})}},response=>{
      const chunks=[]; let size=0;
      response.on('data',chunk=>{size+=chunk.length;if(size>5000000)call.destroy(new Error('Phone response too large'));else chunks.push(chunk);});
      response.on('end',()=>{
        try {const result=JSON.parse(Buffer.concat(chunks).toString('utf8'));if(response.statusCode!==200)throw Error(result.error||`Phone HTTP ${response.statusCode}`);resolve(result);}
        catch(error){reject(error);}
      });
      response.on('error',reject);
    });
    call.setTimeout(15000,()=>call.destroy(new Error('Phone control timed out')));call.on('error',reject);
    if(json!==null)call.write(json);call.end();
  });
}
const integer=(value)=>{const number=Number(value);if(!Number.isInteger(number))throw Error('Expected integer coordinate');return number;};
(async()=>{
  if(action==='status'){console.log(JSON.stringify(await request('/status')));return;}
  if(action==='sync-engine'){
    const log=fs.readFileSync(`${home}/dsh-setup/server.log`,'utf8');
    const url=log.match(/http:\/\/127\.0\.0\.1:3080\/\?token=[A-Za-z0-9_-]+/)?.[0];
    if(!url)throw Error('Local DSH URL is not ready');
    await request('/engine-url',{url});console.log('Native DSH app connected to the local engine.');return;
  }
  let command;
  switch(action){
    case 'screen':command={action:'screen'};break;
    case 'tap':
      command=args[0]==='--node'?{action:'tap',node:args[1],snapshot:args[2]==='--snapshot'?args[3]:undefined}:{action:'tap',x:integer(args[0]),y:integer(args[1])};break;
    case 'swipe':command={action:'swipe',x1:integer(args[0]),y1:integer(args[1]),x2:integer(args[2]),y2:integer(args[3]),duration:args[4]===undefined?400:integer(args[4])};break;
    case 'text':command={action:'text',text:args.join(' ')};break;
    case 'back':case 'home':case 'recents':case 'notifications':command={action:'key',key:action};break;
    case 'open':command={action:'open',package:args[0]};break;
    case 'screenshot':command={action:'screenshot'};if(!args[0])throw Error('Provide a new output .jpg path');break;
    default:throw Error('Usage: phone status | screen | tap X Y | tap --node ID --snapshot SNAPSHOT | swipe X1 Y1 X2 Y2 [MS] | text TEXT | back | home | recents | open PACKAGE | screenshot NEW_FILE.jpg');
  }
  const result=await request('/command',command);
  if(action==='screenshot'){
    const file=path.resolve(args[0]);fs.writeFileSync(file,Buffer.from(result.result.data,'base64'),{flag:'wx',mode:0o600});
    console.log(JSON.stringify({ok:true,file,format:result.result.format}));
  }else console.log(JSON.stringify(result));
})().catch(error=>{console.error(error.message);process.exitCode=1;});
