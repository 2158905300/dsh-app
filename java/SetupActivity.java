package dev.localdsh.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.lang.reflect.Method;

/** First-run setup; never reads any existing API key back into the UI. */
public final class SetupActivity extends Activity {
    private TextView progress,steps,timing;
    private ProgressBar spinner;
    private Button install,save,unsandboxed;
    private EditText key,model;
    private Spinner repository,npmRepository;
    private Button cancel;
    private boolean bootstrapBusy;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        android.view.Window window=getWindow();window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(36,36,36,36);scroll.addView(box);
        TextView title=new TextView(this);title.setText("DeepSeek Harness");title.setTextSize(27);box.addView(title);
        TextView note=new TextView(this);note.setText("分享试验版 · ARM64 / Android 11+\n\n首次联网安装 Node、DSH 和基础浏览器工具，可能耗时较长并占用较多存储空间，请保持联网。无需 root。\n\n本 App 使用 Termux 固定目录，不能与其他 Termux 安装共存。安装过程不读取或导入他人的密钥、聊天或文件。\n");box.addView(note);
        Button guide=new Button(this);guide.setText("第一次使用？打开完整安装与使用指南");guide.setOnClickListener(v->startActivity(new Intent(this,GuideActivity.class)));box.addView(guide);
        TextView first=new TextView(this);first.setText("第 1 步 · 安装运行环境");first.setTextSize(20);box.addView(first);
        progress=new TextView(this);progress.setTextSize(16);box.addView(progress);
        spinner=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);spinner.setIndeterminate(true);spinner.setVisibility(View.GONE);box.addView(spinner);
        steps=new TextView(this);steps.setTextSize(14);steps.setPadding(0,12,0,12);box.addView(steps);
        timing=new TextView(this);timing.setTextSize(13);box.addView(timing);
        TextView repoLabel=new TextView(this);repoLabel.setText("软件源（失败时可切换，不用卸载重装）");box.addView(repoLabel);
        repository=new Spinner(this);ArrayAdapter<String> repoChoices=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"自动：清华镜像 → 官方源","仅清华镜像","仅官方源","沿用现有软件源"});repoChoices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);repository.setAdapter(repoChoices);repository.setSelection(getSharedPreferences("harness-install",0).getInt("repositoryChoice",0));box.addView(repository);
        TextView npmLabel=new TextView(this);npmLabel.setText("npm 下载源（本次安装 DSH，与上面的系统软件源分开）");box.addView(npmLabel);
        npmRepository=new Spinner(this);ArrayAdapter<String> npmChoices=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"自动：npmmirror 镜像 → npm 官方","仅 npmmirror 镜像","仅 npm 官方源","沿用现有 npm 配置"});npmChoices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);npmRepository.setAdapter(npmChoices);npmRepository.setSelection(getSharedPreferences("harness-install",0).getInt("npmRepositoryChoice",0));box.addView(npmRepository);
        install=new Button(this);install.setText("安装运行环境 / 失败后重试");install.setOnClickListener(v->bootstrap());box.addView(install);
        unsandboxed=new Button(this);unsandboxed.setText("此内核不支持沙箱：选择无沙箱模式继续");
        unsandboxed.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("确认使用无沙箱模式")
            .setMessage("此手机内核无法使用 Landlock。继续后，DSH 工具可访问和修改本 App 有权访问的文件，包括本地配置与会话，不再限制于工作区；插件也拥有这些权限。\n\n这不会获得系统 root 权限，也不会自动开启无障碍或手机控制。确认后会保存此运行模式。若不接受，请取消并停止安装。")
            .setNegativeButton("取消",null).setPositiveButton("我理解，使用无沙箱模式",(d,w)->{
                try{SetupFiles.consentUnsandboxed(this);beginInstall(true);}catch(Exception e){toast("无法保存本机确认，请重试");}
            }).show());box.addView(unsandboxed);
        cancel=new Button(this);cancel.setText("停止本次安装（保留已下载内容）");cancel.setOnClickListener(v->startService(new Intent(this,InstallService.class).setAction("dev.localdsh.app.CANCEL_INSTALL")));box.addView(cancel);
        Button diagnostics=new Button(this);diagnostics.setText("查看安装日志与诊断");diagnostics.setOnClickListener(v->startActivity(new Intent(this,DiagnosticsActivity.class)));box.addView(diagnostics);
        Button terminal=new Button(this);terminal.setText("打开内置终端（排查问题）");terminal.setOnClickListener(v->startActivity(new Intent().setComponent(new ComponentName(getPackageName(),"com.termux.app.TermuxActivity"))));box.addView(terminal);
        TextView label=new TextView(this);label.setText("\n第 2 步 · 填写你自己的 Key 和模型\n可以先填写，环境安装完成后才能保存并启动。密钥只保存在本机私有目录。其他供应商可在进入聊天后的模型设置中配置。\n\n只附带基础手机/浏览器工具。其他插件请进入聊天后自行选择安装；本包不含原作者的插件、配置和会话。\n");box.addView(label);
        key=new EditText(this);key.setHint("API Key");key.setSingleLine(true);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);box.addView(key);
        model=new EditText(this);model.setHint("模型名称");model.setSingleLine(true);model.setText("deepseek-flash");box.addView(model);
        save=new Button(this);save.setText("保存并开始使用");save.setOnClickListener(v->save());box.addView(save);
        TextView next=new TextView(this);next.setText("\n第 3 步 · 聊天、附件与可选工具\n先发一句“你好”测试模型。需要传图时点附件入口选择相册；需要手机控制时，再去“权限”开启控制和系统无障碍。插件由你自行选择安装。\n\n指南和诊断入口在聊天页也能随时打开。\n");box.addView(next);
        Button licenses=new Button(this);licenses.setText("开源与许可");licenses.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("开源与许可").setMessage("基于 Termux v0.118.3（GPL-3.0）；本集成源码同样按 GPL-3.0 发布。DSH 及其他运行时依赖在首次安装时从各自官方软件源下载，并遵循各自许可证。分享包附带源码和构建说明。\n\nTermux: github.com/termux/termux-app\nDSH: npmjs.com/package/@deepseek-ai/dsh").setPositiveButton("知道了",null).show());box.addView(licenses);
        setContentView(scroll);refresh();
    }
    @Override protected void onResume(){super.onResume();handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    private void refresh(){
        if(progress==null)return;
        boolean ready=SetupFiles.ready(this),busy=bootstrapBusy||InstallService.running;
        android.content.SharedPreferences prefs=getSharedPreferences("harness-install",0);
        String state=prefs.getString("status","等待安装运行环境");
        boolean hasModel=SetupFiles.modelReady(this);
        progress.setText(ready?(hasModel?"运行环境和模型配置已就绪，可进入聊天。":"运行环境已就绪，请填写模型配置。"):state);
        int step=ready?7:prefs.getInt("stage",0);
        steps.setText(InstallSteps.checklist(step,ready,busy));spinner.setVisibility(busy&&!ready?View.VISIBLE:View.GONE);
        long now=System.currentTimeMillis(),started=prefs.getLong("startedAt",0),last=prefs.getLong("lastOutputAt",0),ended=prefs.getLong("endedAt",0);
        long clockEnd=busy?now:(ended>0?ended:(last>0?last:started));
        String clock=started>0?"本次安装已用 "+Math.max(0,(clockEnd-started)/1000/60)+" 分钟":"安装后这里会显示耗时和最近输出时间。";
        if(busy&&last>0)clock+=" · 最近输出 "+Math.max(0,(now-last)/1000)+" 秒前";
        if(busy)clock+="\n这是步骤进度，不是下载百分比；编译可能暂时没有输出。";
        String phase=prefs.getString("phase","");
        if(!busy&&!ready&&("installing".equals(phase)||"bootstrap".equals(phase)))clock+="\n安装任务当前未运行，可查看日志后重试。";
        timing.setText(clock);
        install.setEnabled(!busy&&!ready);save.setEnabled(ready&&!busy);
        unsandboxed.setVisibility(!ready&&!busy&&SetupFiles.sandboxUnavailable(this)?View.VISIBLE:View.GONE);
        repository.setEnabled(!busy&&!ready);npmRepository.setEnabled(!busy&&!ready);cancel.setVisibility(InstallService.running?View.VISIBLE:View.GONE);
        key.setEnabled(!hasModel);model.setEnabled(!hasModel);
        save.setText(hasModel?"进入聊天（保留已有配置）":"保存并开始使用");
    }
    private void bootstrap(){
        if(bootstrapBusy||InstallService.running)return;
        if(!java.util.Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a")){toast("本试验包只支持 ARM64 手机");return;}
        getSharedPreferences("harness-install",0).edit().putString("status","正在初始化基础环境…").putString("phase","bootstrap").putInt("stage",1).putLong("startedAt",System.currentTimeMillis()).putLong("lastOutputAt",0).putLong("endedAt",0).apply();
        final int choice=repository.getSelectedItemPosition();getSharedPreferences("harness-install",0).edit().putInt("repositoryChoice",choice).apply();
        final int npmChoice=npmRepository.getSelectedItemPosition();getSharedPreferences("harness-install",0).edit().putInt("npmRepositoryChoice",npmChoice).apply();
        bootstrapBusy=true;refresh();
        try{
            Class<?> installer=Class.forName("com.termux.app.TermuxInstaller");
            Method method=installer.getDeclaredMethod("setupBootstrapIfNeeded",Activity.class,Runnable.class);method.setAccessible(true);
            Runnable done=()->runOnUiThread(()->{
                bootstrapBusy=false;
                try{beginInstall(false);}catch(Exception e){toast("无法启动安装服务，请重新打开 App 重试");}
                refresh();
            });
            method.invoke(null,this,done);
        }catch(Exception e){bootstrapBusy=false;getSharedPreferences("harness-install",0).edit().putString("status","基础环境初始化未完成，请查看指南或重试。").putString("phase","failed").putLong("endedAt",System.currentTimeMillis()).apply();toast("基础环境初始化失败，请查看指南或打开内置终端检查");refresh();}
    }
    private void beginInstall(boolean allowUnsandboxed){
        if(InstallService.running)return;
        int choice=repository.getSelectedItemPosition(),npmChoice=npmRepository.getSelectedItemPosition();
        String[] choices={"auto","tuna","official","current"},npmChoices={"auto","mirror","official","current"};
        getSharedPreferences("harness-install",0).edit().putLong("startedAt",System.currentTimeMillis()).putLong("endedAt",0).putLong("lastOutputAt",0).apply();
        startForegroundService(new Intent(this,InstallService.class).putExtra("repository",choices[Math.max(0,Math.min(3,choice))]).putExtra("npmRepository",npmChoices[Math.max(0,Math.min(3,npmChoice))]).putExtra("allowUnsandboxed",allowUnsandboxed));
    }
    private void save(){
        try{
            if(!SetupFiles.modelReady(this))SetupFiles.saveModel(this,key.getText().toString().trim(),model.getText().toString().trim());key.setText("");
            IntegratedRuntime.prepare(this);startActivity(new Intent(this,MainActivity.class));finish();
        }catch(Exception e){toast(e.getMessage()==null?"配置保存失败":e.getMessage());}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
