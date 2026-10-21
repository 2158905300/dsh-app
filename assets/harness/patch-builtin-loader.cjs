const fs = require('node:fs');
const dir = '/data/data/com.termux/files/usr/lib/node_modules/@deepseek-ai/dsh/node_modules/node-addon-require-builtin/lib';
// The published unrestricted package has no Android binary. On Termux, DSH
// already runs with --expose-internals, so verify its required internal modules
// and use Node's own CJS require instead of an unavailable native binding.
for (const id of ['internal/modules/esm/loader', 'internal/modules/cjs/loader', 'internal/modules/helpers', 'internal/modules/esm/utils', 'internal/modules/esm/resolve']) {
  require(id);
}
const target = `${dir}/index.js`;
if (!fs.existsSync(`${target}.before-termux`)) fs.copyFileSync(target, `${target}.before-termux`);
fs.writeFileSync(target, `"use strict";
// Termux compatibility: requires Node --expose-internals.
function requireBuiltin(id) { return require(id); }
function isAllowedInternalId() { return true; }
function getBindingInfo() { return { backend: 'javascript', source: 'termux-expose-internals' }; }
exports.requireBuiltin = requireBuiltin;
exports.isAllowedInternalId = isAllowedInternalId;
exports.getBindingInfo = getBindingInfo;
exports.default = { requireBuiltin, isAllowedInternalId, getBindingInfo };
`);
console.log('Verified required Node internals and installed Termux builtin-loader compatibility shim.');
