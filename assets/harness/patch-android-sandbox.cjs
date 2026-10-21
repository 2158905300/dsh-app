const fs=require('node:fs');
const {spawnSync}=require('node:child_process');
const root='/data/data/com.termux/files/usr/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai';
function patch(file, transform) {
  const original=fs.readFileSync(file,'utf8');
  const result=transform(original);
  if(result!==original) {
    if(!fs.existsSync(`${file}.before-android-landlock`)) fs.copyFileSync(file,`${file}.before-android-landlock`);
    fs.writeFileSync(file,result);
  }
}
patch(`${root}/node-addon-system/lib/index.js`,source=>{
  if(source.includes('// Android Termux native Landlock')) return source;
  const anchor='export function launcherPath(resolvePackageJson = createRequire(import.meta.url).resolve) {';
  if(!source.includes(anchor)) throw new Error('Landlock launcher anchor changed');
  return source.replace(anchor,anchor+`
    // Android Termux native Landlock: built from the bundled official C source.
    if (process.platform === 'android') return fileURLToPath(new URL('../bin/android/landlock-run', import.meta.url));`);
});
patch(`${root}/dsh-sandbox-local/lib/index.js`,source=>{
  if(source.includes('// Android Termux Landlock backend')) return source;
  const chain='const PLATFORM_CHAINS = {';
  const sole='if (rest.length === 0) return {';
  const tmp='readWrite.push("/tmp", policy.workspaceRoot);';
  for(const anchor of [chain,sole,tmp]) if(!source.includes(anchor)) throw new Error('Sandbox anchor changed');
  return source.replace(chain,chain+'\n\t// Android Termux Landlock backend; always run the functional probe.\n\tandroid: ["landlock"],')
    .replace(sole,'if (rest.length === 0 && (this.internals.platform ?? process.platform) !== "android") return {')
    .replace(tmp,'readWrite.push(process.platform === "android" ? "/data/data/com.termux/files/usr/tmp" : "/tmp", policy.workspaceRoot);');
});
const prefix='/data/data/com.termux/files/usr';
const workspace='/data/data/com.termux/files/home/projects/harness';
const outside='/data/data/com.termux/files/home/dsh-setup/sandbox-must-deny';
if(fs.existsSync(outside)) throw new Error('Outside-workspace test target unexpectedly exists');
const runner=`${root}/node-addon-system/bin/android/landlock-run`;
const args=['--ro','/','--rw','/dev/null','--rw',`${prefix}/tmp`,'--rw',workspace,'--',`${prefix}/bin/bash`,'-c'];
const environment={...process.env, PATH:`${prefix}/bin:/system/bin`};
delete environment.LD_PRELOAD;
const inside=spawnSync(runner,[...args,'printf SANDBOX_OK > sandbox-check.txt'],{cwd:workspace,env:environment,encoding:'utf8'});
if(inside.status!==0) throw new Error(`Workspace write failed: ${inside.stderr}`);
const denied=spawnSync(runner,[...args,`printf MUST_NOT_WRITE > ${outside}`],{cwd:workspace,env:environment,encoding:'utf8'});
if(denied.status===0 || fs.existsSync(outside)) throw new Error('Landlock did not block an outside-workspace write');
console.log('Android Landlock verified: workspace writes succeed; outside-workspace writes are denied.');
