const fs=require('node:fs'),path=require('node:path');
const root=__dirname,companion=root;
const dir=root+'/build/src/dev/localdsh/app';fs.mkdirSync(dir,{recursive:true});
for(const name of fs.readdirSync(companion+'/ui-src/dev/localdsh/app').filter(x=>x.endsWith('.java'))){
 let code=fs.readFileSync(companion+'/ui-src/dev/localdsh/app/'+name,'utf8');
 if(name==='MainActivity.java'){
  code=code.replace('super.onCreate(state);','super.onCreate(state);\n        if(!SetupFiles.ready(this)||!SetupFiles.modelReady(this)){startActivity(new Intent(this,SetupActivity.class));finish();return;}');
  code=code.replace('AppState.token(this);','IntegratedRuntime.prepare(this);\n        AppState.token(this);\n        IntegratedRuntime.sync(this);');
  code=code.replaceAll('checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED','false');
  code=code.replaceAll('checkSelfPermission("com.termux.permission.RUN_COMMAND")!=PackageManager.PERMISSION_GRANTED','false');
  code=code.replace('boolean hasUrl=AppState.validUrl','IntegratedRuntime.sync(this);openEngine();\n                boolean hasUrl=AppState.validUrl');
  const begin=code.indexOf('    private boolean runEngineCommand(){'),end=code.indexOf('    private void permissions()',begin);
  if(begin<0||end<0)throw Error('Integrated startup patch anchor missing');
  code=code.slice(0,begin)+'    private boolean runEngineCommand(){return IntegratedRuntime.start(this);}\n'+code.slice(end);
  code=code.replace('box.addView(button("允许启动 Termux 本地服务", v -> requestPermissions(new String[]{"com.termux.permission.RUN_COMMAND"}, 12)));','box.addView(button("打开内置终端", v -> startActivity(new Intent().setComponent(new ComponentName(getPackageName(),"com.termux.app.TermuxActivity")))));');
  code=code.replace('本地服务','内置服务').replaceAll('Termux 本地服务','内置运行环境').replaceAll('检查运行环境','检查内置运行环境');
  code=code.replace('如提示缺少启动权限，在上方「权限」中允许启动 内置运行环境。','首次启动需要稍候。如持续无法连接，可在权限页打开内置终端检查。');
  const guideAnchor='root.addView(status);';
  if(!code.includes(guideAnchor))throw Error('Persistent guide entry anchor missing');
  code=code.replace(guideAnchor,guideAnchor+'\n        TextView guideLink=new TextView(this);guideLink.setText(SetupFiles.modeLabel(this)+"\\n使用指南 · 安装、插件与常见问题");guideLink.setTextSize(14);guideLink.setTextColor(Color.rgb(36,92,175));guideLink.setPadding(dp(16),dp(8),dp(12),dp(8));guideLink.setOnClickListener(v->startActivity(new Intent(this,GuideActivity.class)));root.addView(guideLink);');
  code=code.replace('box.addView(button("返回聊天",','box.addView(button("安装与使用指南", v -> startActivity(new Intent(this,GuideActivity.class))));\n        box.addView(button("安装日志与诊断", v -> startActivity(new Intent(this,DiagnosticsActivity.class))));\n        box.addView(button("返回聊天",');
 }
 if(name==='LocalBridgeService.java'){
  code=code.replaceAll('3081','3082');
  const begin=code.indexOf('                    Intent run=new Intent("com.termux.RUN_COMMAND")'),end=code.indexOf('                    respond(client,200',begin);
  if(begin<0||end<0)throw Error('Integrated engine API patch anchor missing');
  code=code.slice(0,begin)+'                    if(!IntegratedRuntime.start(this))throw new IllegalStateException("内置运行环境未就绪");\n'+code.slice(end);
 }
 fs.writeFileSync(path.join(dir,name),code);
}
for(const name of fs.readdirSync(root+'/java').filter(x=>x.endsWith('.java')))fs.copyFileSync(root+'/java/'+name,dir+'/'+name);
console.log('Integrated Java sources prepared.');
