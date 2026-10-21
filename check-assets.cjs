const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.join(__dirname,'assets/harness');
// Explicit allowlist: never copy a phone HOME, profile, env file, key or history.
const allowed=['LICENSE-installer.txt','PHONE-SKILL.md','browser-plugin/cordis.patch.yml','browser-plugin/index.js','browser-plugin/package.json','configure-runtime.cjs','daemonize.sh','fix-session-import.cjs','install-dsh-reviewed.sh','install.sh','is-running.cjs','npm-repository.sh','patch-android-flock.cjs','patch-android-sandbox.cjs','patch-builtin-loader.cjs','phone.cjs','repository.sh','start.sh'];
function list(dir,prefix=''){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?list(path.join(dir,e.name),prefix+e.name+'/'):[prefix+e.name]);}
allowed.push('sandbox-mode.sh');
const files=list(root).filter(x=>x!=='files.json').sort();
if(JSON.stringify(files)!==JSON.stringify(allowed.sort()))throw Error('Unexpected or missing distributable asset');
const hashes={};for(const name of files){
 let data=fs.readFileSync(path.join(root,name));
 if(/\.(?:sh|cjs|js|md|yml|json|txt)$/.test(name)){data=Buffer.from(data.toString('utf8').replaceAll('\r\n','\n'));fs.writeFileSync(path.join(root,name),data);}
 const text=data.toString('utf8');
 if(/sk-[A-Za-z0-9_-]{16,}|Bearer [A-Za-z0-9_-]{24,}|BEGIN (?:RSA |EC )?PRIVATE KEY/.test(text))throw Error('Potential credential found in '+name);
 hashes[name]=crypto.createHash('sha256').update(data).digest('hex');
}
fs.writeFileSync(path.join(root,'files.json'),JSON.stringify(hashes,null,2)+'\n');
console.log('Share assets allowlist and credential-pattern checks passed: '+files.length+' public files.');
