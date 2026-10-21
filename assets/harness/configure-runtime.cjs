const fs=require('node:fs'),path=require('node:path');
const home=process.env.HOME, prefix=process.env.PREFIX;
const core=prefix+'/lib/node_modules/@deepseek-ai/dsh/node_modules';
const modules=home+'/dsh-setup/share-modules/node_modules';
fs.mkdirSync(modules,{recursive:true,mode:0o700});
for(const name of fs.readdirSync(core)){
 const destination=path.join(modules,name);
 if(!fs.existsSync(destination))fs.symlinkSync(path.join(core,name),destination);
}
const browser=modules+'/dsh-phone-browser';
fs.mkdirSync(browser,{recursive:true});
for(const file of ['package.json','index.js','cordis.patch.yml'])fs.copyFileSync(home+'/dsh-setup/browser-plugin/'+file,browser+'/'+file);
const profile=home+'/.dsh/profiles/mobile-share';fs.mkdirSync(profile,{recursive:true,mode:0o700});
const pkg={name:'harness-mobile-share-profile',private:true,dependencies:{'dsh-phone-browser':'file:'+browser},dsh:{profile:{bundles:['@deepseek-ai/dsh-base','@deepseek-ai/dsh-web-app','dsh-phone-browser']}}};
fs.writeFileSync(profile+'/package.json',JSON.stringify(pkg,null,2),{mode:0o600});
if(!fs.existsSync(profile+'/node_modules'))fs.symlinkSync(modules,profile+'/node_modules');
// Existing model settings are never replaced when retrying installation.
if(!fs.existsSync(profile+'/cordis.patch.yml'))fs.writeFileSync(profile+'/cordis.patch.yml','[]\n',{mode:0o600});
const workspace=home+'/projects/harness';fs.mkdirSync(workspace,{recursive:true,mode:0o700});
const skills=workspace+'/.agents/skills/phone-control';fs.mkdirSync(skills,{recursive:true});
fs.copyFileSync(home+'/dsh-setup/PHONE-SKILL.md',skills+'/SKILL.md');
const phone=prefix+'/bin/phone';
fs.writeFileSync(phone,'#!'+prefix+'/bin/bash\nexec "'+prefix+'/bin/node" "'+home+'/dsh-setup/phone.cjs" "$@"\n',{mode:0o700});
fs.chmodSync(phone,0o700);
console.log('Clean mobile-share profile and phone/browser tools configured.');
