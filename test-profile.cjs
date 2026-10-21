const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.join(__dirname,'build','profile-test-'+Date.now()),home=path.join(root,'home'),prefix=path.join(root,'usr');
const setup=path.join(home,'dsh-setup'),core=path.join(prefix,'lib/node_modules/@deepseek-ai/dsh/node_modules');
fs.mkdirSync(path.join(core,'@deepseek-ai'),{recursive:true});fs.mkdirSync(path.join(core,'yaml'));
fs.mkdirSync(path.join(prefix,'bin'),{recursive:true});
fs.mkdirSync(setup,{recursive:true});
fs.cpSync(path.join(__dirname,'assets/harness/browser-plugin'),path.join(setup,'browser-plugin'),{recursive:true});
fs.copyFileSync(path.join(__dirname,'assets/harness/PHONE-SKILL.md'),path.join(setup,'PHONE-SKILL.md'));
// Fake private data belongs to the recipient and must survive retries unchanged.
const privateFile=path.join(setup,'model-env.sh'),privateValue='PRIVATE_CONFIG_SENTINEL\n';fs.writeFileSync(privateFile,privateValue);
const compatibleFs={...fs,symlinkSync:(target,dest)=>fs.symlinkSync(target,dest,process.platform==='win32'?'junction':'dir')};
const source=fs.readFileSync(path.join(__dirname,'assets/harness/configure-runtime.cjs'),'utf8');
function run(){vm.runInNewContext(source,{require:name=>name==='node:fs'?compatibleFs:require(name),process:{env:{HOME:home,PREFIX:prefix}},console:{log(){}}},{filename:'configure-runtime.cjs'});}
run();
const profile=path.join(home,'.dsh/profiles/mobile-share'),patch=path.join(profile,'cordis.patch.yml');
const pkg=JSON.parse(fs.readFileSync(path.join(profile,'package.json'),'utf8'));
assert.deepEqual(pkg.dsh.profile.bundles,['@deepseek-ai/dsh-base','@deepseek-ai/dsh-web-app','dsh-phone-browser']);
assert.equal(fs.readFileSync(patch,'utf8'),'[]\n');
const recipientSettings='[{"id":"recipient-custom-setting","config":{"value":42}}]\n';fs.writeFileSync(patch,recipientSettings);
run();
assert.equal(fs.readFileSync(patch,'utf8'),recipientSettings);
assert.equal(fs.readFileSync(privateFile,'utf8'),privateValue);
assert.ok(fs.readFileSync(path.join(prefix,'bin/phone'),'utf8').includes('phone.cjs'));
assert.ok(fs.existsSync(path.join(home,'projects/harness/.agents/skills/phone-control/SKILL.md')));
console.log('Fresh profile creation and install retry preserve recipient model config/settings; phone/browser tools installed.');
