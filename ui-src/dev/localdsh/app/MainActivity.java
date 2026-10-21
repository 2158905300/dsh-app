package dev.localdsh.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    private WebView web;
    private TextView status;
    private String loadedUrl = "";
    private ValueCallback<Uri[]> fileCallback;
    private boolean foreground, healthChecking, pageFailed, needsPairing;
    private String connectionNotice="正在检查本地服务…";
    private final EngineRecoveryPolicy recovery=new EngineRecoveryPolicy();
    private final java.util.concurrent.ExecutorService healthWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable healthMonitor=()->checkEngine();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (p, key) -> {
        if ("engineUrl".equals(key)) handler.post(() -> openEngine());
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        AppState.token(this);
        acceptUrl(getIntent());
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        LinearLayout bar = new LinearLayout(this); bar.setPadding(dp(12), dp(4), dp(8), dp(4)); bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this); title.setText("DSH"); title.setTextColor(Color.rgb(25, 45, 75)); title.setTextSize(21); title.setTypeface(null, 1);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button connect = button("连接", v -> startEngine()); bar.addView(connect);
        Button browser = button("浏览器", v -> startActivity(new Intent(this, BrowserActivity.class))); bar.addView(browser);
        Button permissions = button("权限", v -> permissions()); bar.addView(permissions);
        root.addView(bar);
        status = new TextView(this); status.setTextSize(12); status.setPadding(dp(16), dp(2), dp(12), dp(6)); status.setTextColor(Color.DKGRAY); root.addView(status);
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(true); settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); settings.setUseWideViewPort(true); settings.setLoadWithOverviewMode(true);
        WebView.setWebContentsDebuggingEnabled(false);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
                boolean imageOnly=false,otherTypes=false;
                for(String type:params.getAcceptTypes())for(String value:type.split(",")){
                    value=value.trim();if(value.isEmpty())continue;
                    if(value.startsWith("image/"))imageOnly=true;else otherTypes=true;
                }
                if(imageOnly&&!otherTypes){launchAttachmentPicker(true,params,callback);return true;}
                new android.app.AlertDialog.Builder(MainActivity.this).setTitle("添加附件")
                    .setItems(new String[]{"从相册选图片","选择文件"},(dialog,which)->launchAttachmentPicker(which==0,params,callback))
                    .setOnCancelListener(dialog->cancelAttachmentPicker(callback)).show();
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){pageFailed=false;}
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !AppState.validUrl(request.getUrl().toString());
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if(request.isForMainFrame()){pageFailed=true;connectionNotice="连接已断开，正在自动恢复…";updateStatus();queueHealthCheck();}
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){
                if(request.isForMainFrame()&&(response.getStatusCode()==401||response.getStatusCode()==403)){
                    pageFailed=true;needsPairing=true;connectionNotice="正在更新本地连接…";updateStatus();queueHealthCheck();
                }
            }
            @Override public void onPageFinished(WebView view, String url) { updateStatus(); }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        AppState.prefs(this).registerOnSharedPreferenceChangeListener(listener);
        startBridge(); openEngine();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); acceptUrl(intent); openEngine(); }
    private void acceptUrl(Intent intent) {
        String url = intent.getStringExtra("engine_url");
        if (url != null && AppState.validUrl(url)) AppState.prefs(this).edit().putString("engineUrl", url).apply();
    }
    @Override protected void onResume() { super.onResume();foreground=true;recovery.enteredForeground(); if(status!=null){updateStatus();openEngine();queueHealthCheck();} }
    @Override protected void onPause(){foreground=false;handler.removeCallbacks(healthMonitor);super.onPause();}
    private void updateStatus() {
        boolean enabled = AppState.prefs(this).getBoolean("controlEnabled", false);
        String control = !enabled ? "手机控制已关闭" : PhoneAccessibilityService.instance == null ? "手机控制等待无障碍授权" : "手机控制已就绪";
        status.setText(connectionNotice!=null?connectionNotice:"手机本地运行 · "+control);
    }
    private void openEngine() {
        String url = AppState.prefs(this).getString("engineUrl", "");
        if (AppState.validUrl(url) && !url.equals(loadedUrl)) { loadedUrl = url; web.loadUrl(url); }
        else if (loadedUrl.isEmpty()) {
            web.loadData("<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='font-family:sans-serif;padding:30px;color:#24334c'><h1>你的手机，本地 DSH</h1><p>正在自动连接手机本地服务，首次启动请稍候。</p><p>如提示缺少启动权限，在上方「权限」中允许启动 Termux 本地服务。</p></body></html>", "text/html", "UTF-8");
        }
    }
    private void startBridge() {
        try { startForegroundService(new Intent(this, LocalBridgeService.class)); }
        catch (Exception e) { toast("本地连接服务启动失败，请重新打开 App。"); }
    }
    private void startEngine() {
        startBridge();
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"com.termux.permission.RUN_COMMAND"}, 12); return;
        }
        recovery.retryNow();needsPairing=true;connectionNotice="正在自动连接本地服务…";updateStatus();queueHealthCheck();
    }
    private void queueHealthCheck(){if(foreground){handler.removeCallbacks(healthMonitor);handler.post(healthMonitor);}}
    private void checkEngine(){
        if(!foreground||healthChecking||isDestroyed())return;
        healthChecking=true;
        healthWorker.execute(()->{
            boolean ready=false;java.net.HttpURLConnection probe=null;
            try{
                probe=(java.net.HttpURLConnection)new java.net.URL("http://127.0.0.1:3080/").openConnection();
                probe.setConnectTimeout(1500);probe.setReadTimeout(1500);probe.setInstanceFollowRedirects(false);
                int code=probe.getResponseCode();ready=code==200||code==401;
            }catch(Exception ignored){}finally{if(probe!=null)probe.disconnect();}
            final boolean available=ready;
            handler.post(()->{
                healthChecking=false;if(!foreground||isDestroyed())return;
                boolean hasUrl=AppState.validUrl(AppState.prefs(this).getString("engineUrl",""));
                if(available&&hasUrl&&!needsPairing){
                    recovery.healthy();connectionNotice=null;
                    if(pageFailed){pageFailed=false;web.loadUrl(loadedUrl);}
                }else if(checkSelfPermission("com.termux.permission.RUN_COMMAND")!=PackageManager.PERMISSION_GRANTED){
                    connectionNotice="需要启动权限：点「权限」允许启动 Termux 本地服务。";
                }else if(recovery.tryStart(SystemClock.elapsedRealtime())){
                    connectionNotice=runEngineCommand()?"正在自动唤醒本地服务，请稍候…":"无法启动本地服务，请点「权限」检查运行环境。";
                    if(available)needsPairing=false;
                }else if(recovery.exhausted()){
                    connectionNotice="自动连接未成功，点「连接」重试，或在「权限」检查运行环境。";
                }else connectionNotice="正在等待本地服务启动…";
                updateStatus();handler.postDelayed(healthMonitor,5000);
            });
        });
    }
    private boolean runEngineCommand(){
        try {
            Intent run = new Intent("com.termux.RUN_COMMAND");
            run.setComponent(new ComponentName("com.termux", "com.termux.app.RunCommandService"));
            run.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/dsh-app-start");
            run.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            run.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            startService(run);
            return true;
        }catch(Exception e){return false;}
    }
    private void permissions() {
        Dialog dialog = new Dialog(this); dialog.setTitle("DSH 手机能力");
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.WHITE); LinearLayout box = new LinearLayout(this); box.setBackgroundColor(Color.WHITE); box.setOrientation(1); box.setPadding(dp(20), dp(20), dp(20), dp(20)); scroll.addView(box);
        TextView heading = new TextView(this); heading.setText("手机能力与权限"); heading.setTextSize(23); heading.setTypeface(null, 1); box.addView(heading);
        TextView description = new TextView(this); description.setText("DSH 在手机本地运行。无障碍可读取界面、点击、滑动和输入；密码框内容会隐藏。本地控制只监听 127.0.0.1，并要求私有令牌。\n"); box.addView(description);
        Switch control = new Switch(this); control.setText("允许本地 DSH 操作手机"); control.setChecked(AppState.prefs(this).getBoolean("controlEnabled", false)); box.addView(control);
        control.setOnCheckedChangeListener((v, checked) -> { AppState.prefs(this).edit().putBoolean("controlEnabled", checked).apply(); startBridge(); updateStatus(); });
        TextView state = new TextView(this); state.setText(PhoneAccessibilityService.instance == null ? "无障碍：未启用" : "无障碍：已启用"); state.setPadding(0, dp(14), 0, dp(6)); box.addView(state);
        box.addView(button("打开系统无障碍设置", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        box.addView(button("允许启动 Termux 本地服务", v -> requestPermissions(new String[]{"com.termux.permission.RUN_COMMAND"}, 12)));
        if (Build.VERSION.SDK_INT >= 33) box.addView(button("配置运行通知", v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 13)));
        box.addView(button("打开后台电池设置", v -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))));
        box.addView(button("停止手机控制", v -> { control.setChecked(false); toast("手机控制已关闭，聊天仍可使用。"); }));
        box.addView(button("返回聊天", v -> dialog.dismiss()));
        dialog.setContentView(scroll); dialog.show(); dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.WHITE)); dialog.getWindow().setLayout(-1, -1);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 12 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startEngine();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=41||fileCallback==null)return;
        ValueCallback<Uri[]> callback=fileCallback;fileCallback=null;
        if(result!=RESULT_OK||data==null){callback.onReceiveValue(null);return;}
        java.util.LinkedHashSet<Uri> selected=new java.util.LinkedHashSet<>();
        ClipData clips=data.getClipData();
        if(clips!=null){for(int i=0;i<clips.getItemCount();i++)selected.add(clips.getItemAt(i).getUri());}
        else if(data.getData()!=null)selected.add(data.getData());
        java.util.ArrayList<Uri> readable=new java.util.ArrayList<>();
        for(Uri uri:selected){
            if(uri==null||!"content".equals(uri.getScheme()))continue;
            try(android.os.ParcelFileDescriptor descriptor=getContentResolver().openFileDescriptor(uri,"r")){
                if(descriptor!=null)readable.add(uri);
            }catch(Exception ignored){ /* Only user-selected document URIs are inspected; never logged. */ }
        }
        if(readable.isEmpty()){
            callback.onReceiveValue(null);toast("无法读取所选文件，请从系统文件选择器重新选择。");return;
        }
        callback.onReceiveValue(readable.toArray(new Uri[0]));
        if(readable.size()!=selected.size())toast("部分文件无法读取，已添加其余文件。");
    }
    private void cancelAttachmentPicker(ValueCallback<Uri[]> callback){
        if(fileCallback==callback){fileCallback=null;callback.onReceiveValue(null);}
    }
    private void launchAttachmentPicker(boolean photos,WebChromeClient.FileChooserParams params,ValueCallback<Uri[]> callback){
        if(fileCallback!=callback)return;
        boolean multiple=params.getMode()==WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
        if(photos&&Build.VERSION.SDK_INT>=33){
            Intent picker=new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(multiple)picker.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,MediaStore.getPickImagesMaxLimit());
            try{startActivityForResult(picker,41);return;}catch(android.content.ActivityNotFoundException ignored){ /* Fall back to an image-only provider chooser. */ }
        }
        Intent choose=new Intent(photos?Intent.ACTION_GET_CONTENT:Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType(photos?"image/*":"*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple);
        try{startActivityForResult(choose,41);}catch(Exception e){cancelAttachmentPicker(callback);toast(photos?"无法打开相册，请尝试选择文件。":"无法打开系统文件选择器");}
    }
    private Button button(String text, View.OnClickListener click) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(click); return b; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() {foreground=false;handler.removeCallbacks(healthMonitor);healthWorker.shutdownNow(); if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;} AppState.prefs(this).unregisterOnSharedPreferenceChangeListener(listener); super.onDestroy(); }
}
