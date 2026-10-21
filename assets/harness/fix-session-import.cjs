const fs = require('node:fs');
const file='/data/data/com.termux/files/usr/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-session-persistence-jsonl/lib/index.js';
let source=fs.readFileSync(file,'utf8');
const pattern=/import \{([^}]+)\} from "node:fs\/promises";/;
const match=source.match(pattern);
if(!match) throw new Error('Session fs import not found');
if(!match[1].split(',').some(x=>x.trim()==='rename')) {
  fs.copyFileSync(file, `${file}.before-rename-import`);
  source=source.replace(pattern, `import {${match[1]}, rename } from "node:fs/promises";`);
  fs.writeFileSync(file,source);
}
console.log('Session persistence rename import verified.');
