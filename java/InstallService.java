package dev.localdsh.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.system.Os;
import android.system.OsConstants;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Installs public runtime packages only. No model credentials are read or logged. */
public final class InstallService extends Service {
    static volatile boolean running;
    private PowerManager.WakeLock wake;
    private volatile java.lang.Process child;
    private volatile boolean cancelled;
    @Override public void onCreate(){
        super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("harness-install","Harness 环境安装",NotificationManager.IMPORTANCE_LOW));
    }
    private void status(String value){
        android.content.SharedPreferences.Editor update=getSharedPreferences("harness-install",0).edit().putString("status",value);
        int step=InstallSteps.index(value);if(step>0)update.putInt("stage",step);update.apply();
        PendingIntent open=PendingIntent.getActivity(this,440,new Intent(this,SetupActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(440,new Notification.Builder(this,"harness-install").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("DeepSeek Harness").setContentText(value).setContentIntent(open).setOngoing(true).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"dev.localdsh.app.CANCEL_INSTALL".equals(intent.getAction())){
            if(!running){stopSelf();return START_NOT_STICKY;}
            cancelled=true;status("正在停止本次安装，请稍候…");
            new Thread(()->cancelChild(),"Harness-cancel-install").start();return START_NOT_STICKY;
        }
        if(running)return START_NOT_STICKY;
        running=true;cancelled=false;
        String selected=intent==null?"auto":intent.getStringExtra("repository");
        final String repo=java.util.Arrays.asList("auto","tuna","official","current").contains(selected)?selected:"auto";
        String npmSelected=intent==null?"auto":intent.getStringExtra("npmRepository");
        final String npmRepo=java.util.Arrays.asList("auto","mirror","official","current").contains(npmSelected)?npmSelected:"auto";
        final boolean resumePatches=SetupFiles.patchResumeAvailable(this);
        final boolean allowUnsandboxed=intent!=null&&intent.getBooleanExtra("allowUnsandboxed",false)&&new File(SetupFiles.setup(this),"unsandboxed-consent").isFile();
        getSharedPreferences("harness-install",0).edit().putString("phase","installing").apply();
        status("正在准备运行环境…");
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Harness:Install");wake.acquire(90*60*1000L);
        new Thread(()->{
            try{
                SetupFiles.extract(this);
                if(cancelled)throw new IOException("安装已取消");
                java.nio.file.Files.deleteIfExists(new File(SetupFiles.setup(this),"install.pid").toPath());
                ProcessBuilder builder=new ProcessBuilder("/data/data/com.termux/files/usr/bin/setsid","/data/data/com.termux/files/usr/bin/bash",new File(SetupFiles.setup(this),"install.sh").getPath());builder.directory(SetupFiles.home(this));builder.redirectErrorStream(true);
                Map<String,String> env=builder.environment();String prefix="/data/data/com.termux/files/usr";
                env.put("HOME","/data/data/com.termux/files/home");env.put("PREFIX",prefix);env.put("PATH",prefix+"/bin:/system/bin");env.put("TMPDIR",prefix+"/tmp");env.put("SHELL",prefix+"/bin/bash");env.put("TERM","xterm-256color");env.remove("LD_PRELOAD");
                env.put("HARNESS_REPOSITORY",repo);
                env.put("HARNESS_NPM_REPOSITORY",npmRepo);
                env.put("HARNESS_RESUME_PATCHES",resumePatches?"1":"0");
                env.put("HARNESS_ALLOW_UNSANDBOXED",allowUnsandboxed?"1":"0");
                env.remove("LD_LIBRARY_PATH");env.remove("NODE_OPTIONS");env.remove("NODE_PATH");
                child=builder.start();child.getOutputStream().close();
                if(cancelled)cancelChild();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8))){
                    BufferedWriter log=new BufferedWriter(new OutputStreamWriter(openFileOutput("harness-install.log",MODE_PRIVATE),StandardCharsets.UTF_8));
                    try {
                    String line;long size=0,lastHeartbeat=0;while((line=reader.readLine())!=null){
                        if(line.startsWith("HARNESS_STAGE: "))status(line.substring(15));
                        long now=System.currentTimeMillis();if(now-lastHeartbeat>=2000){getSharedPreferences("harness-install",0).edit().putLong("lastOutputAt",now).apply();lastHeartbeat=now;}
                        // Keep the latest output even after a verbose compiler fills the log.
                        if(size>=5000000){log.flush();log.close();java.nio.file.Files.move(getFileStreamPath("harness-install.log").toPath(),getFileStreamPath("harness-install.previous.log").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);log=new BufferedWriter(new OutputStreamWriter(openFileOutput("harness-install.log",MODE_PRIVATE),StandardCharsets.UTF_8));size=0;}
                        log.write(line);log.newLine();log.flush();size+=line.length();
                    }
                    } finally { log.close(); }
                }
                int code=child.waitFor();
                if(cancelled||code!=0||!SetupFiles.ready(this))throw new IOException("安装步骤失败");
                getSharedPreferences("harness-install",0).edit().putString("phase","done").putLong("endedAt",System.currentTimeMillis()).apply();
                status("安装完成，请回 App 配置模型");
            }catch(Exception e){getSharedPreferences("harness-install",0).edit().putString("phase",cancelled?"cancelled":"failed").putLong("endedAt",System.currentTimeMillis()).apply();status(cancelled?"本次安装已停止，可切换软件源后重试。":"安装未完成，请打开安装日志与诊断；索引下载失败时可切换软件源重试。");}
            finally{child=null;running=false;if(wake!=null&&wake.isHeld())wake.release();stopForeground(true);stopSelf();}
        },"Harness-first-install").start();return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}
    private void cancelChild(){
        if(child==null)return;
        // The installer has its own session/process group. Never signal the App's group.
        for(int attempt=0;attempt<10;attempt++){
            try{
                File file=new File(SetupFiles.setup(this),"install.pid");String value=new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8).trim();
                int pid=Integer.parseInt(value);
                String stat=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/proc/"+pid+"/stat")),StandardCharsets.UTF_8);
                if(InstallerProcessGroup.matches(pid,stat)){Os.kill(-pid,OsConstants.SIGTERM);return;}
            }catch(Exception ignored){}
            try{Thread.sleep(50);}catch(InterruptedException ignored){Thread.currentThread().interrupt();break;}
        }
        java.lang.Process active=child;if(active!=null)active.destroy();
    }
}
