package dev.localdsh.app;

import android.content.*;
import android.system.Os;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/** Runs the existing DSH installation inside this same com.termux APK/UID. */
public final class IntegratedRuntime {
    private static final AtomicBoolean starting=new AtomicBoolean();
    private static File home(Context c){return new File(c.getFilesDir(),"home");}
    public static synchronized void prepare(Context c){
        File setup=new File(home(c),"dsh-setup");if(!setup.isDirectory())return;
        try{
            File pair=new File(setup,"phone-bridge-token");
            Files.write(pair.toPath(),AppState.token(c).getBytes(StandardCharsets.UTF_8));Os.chmod(pair.getAbsolutePath(),0600);
            patchPort(new File(setup,"phone.cjs"));
            patchPort(new File(setup,"plugin-port-20261019/node_modules/dsh-phone-browser/index.js"));
        }catch(Exception e){throw new IllegalStateException("本地工具连接初始化失败，请保留数据后重试。",e);}
    }
    private static void patchPort(File file)throws IOException{
        if(!file.isFile())return;
        String code=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
        String patched=code.replace("port:3081","port:3082").replace("port: 3081","port: 3082");
        if(patched.equals(code))return;
        Path backup=new File(file.getPath()+".before-integrated").toPath();if(!Files.exists(backup))Files.copy(file.toPath(),backup);
        Path temp=new File(file.getPath()+".integrated.tmp").toPath();Files.write(temp,patched.getBytes(StandardCharsets.UTF_8));Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    public static boolean start(Context context){
        final Context c=context.getApplicationContext();
        File script=new File(home(c),"dsh-setup/daemonize.sh"),prefix=new File(c.getFilesDir(),"usr");
        if(!script.isFile()||!new File(prefix,"bin/bash").isFile())return false;
        if(!starting.compareAndSet(false,true))return true;
        try{
            Intent hold=new Intent("com.termux.service_wake_lock").setComponent(new ComponentName(c.getPackageName(),"com.termux.app.TermuxService"));c.startForegroundService(hold);
        }catch(Exception ignored){ /* DSH's own foreground bridge remains active. */ }
        new Thread(()->{
            try{
                prepare(c);
                ProcessBuilder builder=new ProcessBuilder(new File(prefix,"bin/bash").getAbsolutePath(),script.getAbsolutePath());
                builder.directory(home(c));java.util.Map<String,String> env=builder.environment();
                env.put("HOME",home(c).getAbsolutePath());env.put("PREFIX",prefix.getAbsolutePath());env.put("PATH",prefix+"/bin:/system/bin");
                env.put("TMPDIR",prefix+"/tmp");env.put("SHELL",prefix+"/bin/bash");env.put("DSH_HOME",home(c)+"/.dsh");env.put("TZ","Asia/Shanghai");env.remove("LD_PRELOAD");
                env.remove("LD_LIBRARY_PATH");env.remove("NODE_OPTIONS");env.remove("NODE_PATH");
                builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(new File(home(c),"dsh-setup/integrated-start.log")));
                Process child=builder.start();if(!child.waitFor(12,TimeUnit.SECONDS))child.destroy();
                sync(c);
            }catch(Exception ignored){ /* MainActivity reports bounded connection failure, without secret logs. */ }
            finally{starting.set(false);}
        },"DSH-integrated-start").start();return true;
    }
    public static void sync(Context c){
        File log=new File(home(c),"dsh-setup/server.log");if(!log.isFile()||log.length()>2000000)return;
        try{
            String text=new String(Files.readAllBytes(log.toPath()),StandardCharsets.UTF_8),url=null;
            Matcher match=Pattern.compile("http://127\\.0\\.0\\.1:3080/\\?token=[A-Za-z0-9_-]+").matcher(text);while(match.find())url=match.group();
            if(url!=null&&AppState.validUrl(url))AppState.prefs(c).edit().putString("engineUrl",url).apply();
        }catch(IOException ignored){}
    }
}
