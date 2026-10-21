const http=require('node:http');
const request=http.get('http://127.0.0.1:3080/',response=>{
  response.resume();
  process.exitCode=[200,401].includes(response.statusCode)?0:1;
});
request.setTimeout(1500,()=>request.destroy());
request.on('error',()=>{process.exitCode=1;});
