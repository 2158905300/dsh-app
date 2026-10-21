const fs = require('node:fs');
const path = '/data/data/com.termux/files/usr/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/node-addon-system/lib/flock.js';
let source = fs.readFileSync(path, 'utf8');
if (!source.includes('// Termux Android native flock')) {
  const anchor = '    const { platform, arch } = process;';
  if (!source.includes(anchor)) throw new Error('flock loader anchor changed');
  fs.copyFileSync(path, `${path}.before-termux`);
  source = source.replace(anchor, anchor + `
    // Termux Android native flock: compiled from this package's src/flock.c.
    if (platform === 'android') {
        binding = createRequire(import.meta.url)(new URL('../bin/android/system.node', import.meta.url).pathname);
        return binding;
    }`);
  fs.writeFileSync(path, source);
}
(async () => {
  const { tryLockExclusive } = await import(path);
  const filename='/data/data/com.termux/files/home/dsh-setup/flock-check.lock';
  const a=await fs.promises.open(filename,'a');
  const b=await fs.promises.open(filename,'a');
  await tryLockExclusive(a.fd);
  let contended=false;
  try { await tryLockExclusive(b.fd); }
  catch(error) { if (['EAGAIN','EWOULDBLOCK'].includes(error.code)) contended=true; else throw error; }
  if (!contended) throw new Error('flock failed to reject a competing descriptor');
  await a.close();
  await tryLockExclusive(b.fd);
  await b.close();
  console.log('Android native flock: acquisition, contention, and release verified.');
})().catch(error=>{console.error(error);process.exitCode=1;});
