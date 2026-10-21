package dev.localdsh.app;

import android.content.Context;
import android.system.Os;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import org.json.*;

final class SetupFiles {
    static File home(Context c){return new File(c.getFilesDir(),"home");}
    static File setup(Context c){return new File(home(c),"dsh-setup");}
    static boolean ready(Context c){
        return new File(setup(c),"share-ready").isFile()
            &&new File(c.getFilesDir(),"usr/bin/node").isFile()
            &&new File(c.getFilesDir(),"usr/lib/node_modules/@deepseek-ai/dsh/lib/bin.js").isFile();
    }
    static boolean modelReady(Context c){return new File(setup(c),"model-env.sh").isFile();}
    static boolean patchResumeAvailable(Context c){
        if(!new File(c.getFilesDir(),"usr/lib/node_modules/@deepseek-ai/dsh/lib/bin.js").isFile())return false;
        if(sandboxUnavailable(c))return true;
        try{return DiagnosticLogs.tail(c.getFileStreamPath("harness-install.log")).contains("HARNESS_STAGE: 正在应用安卓兼容补丁");}catch(Exception ignored){return false;}
    }
    static boolean sandboxUnavailable(Context c){
        if(new File(setup(c),"sandbox-unavailable").isFile())return true;
        try{return DiagnosticLogs.tail(c.getFileStreamPath("harness-install.log")).contains("landlock is not enforced by this kernel");}catch(Exception ignored){return false;}
    }
    static void consentUnsandboxed(Context c)throws Exception{
        atomicPrivate(new File(setup(c),"unsandboxed-consent"),"confirmed-in-app-0.4.7\n");
    }
    static String modeLabel(Context c){
        try{if(new File(setup(c),"unsandboxed-consent").isFile()&&"danger-full-access".equals(new String(Files.readAllBytes(new File(setup(c),"permission-mode").toPath()),StandardCharsets.UTF_8).trim()))return "无沙箱模式 · 本 App 文件可被工具访问";}catch(Exception ignored){}
        return "工作区沙箱模式";
    }
    static void extract(Context c)throws Exception{
        File root=setup(c);if(!root.isDirectory()&&!root.mkdirs())throw new IOException("无法创建安装目录");
        String manifest;
        try(InputStream in=c.getAssets().open("harness/files.json")){manifest=new String(read(in),StandardCharsets.UTF_8);}
        JSONObject files=new JSONObject(manifest);
        java.util.Iterator<String> names=files.keys();
        while(names.hasNext()){
            String name=names.next();
            if(!name.matches("[A-Za-z0-9_./-]+")||name.contains("..")||name.startsWith("/"))throw new IOException("安装清单路径无效");
            byte[] data;try(InputStream in=c.getAssets().open("harness/"+name)){data=read(in);}
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder hex=new StringBuilder();
            for(byte b:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!hex.toString().equals(files.getString(name)))throw new IOException("安装文件校验失败");
            File target=new File(root,name);File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建安装子目录");
            Path temporary=new File(target.getPath()+".asset.tmp").toPath();Files.write(temporary,data);Os.chmod(temporary.toString(),0600);
            Files.move(temporary,target.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
    }
    static byte[] read(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>2000000)throw new IOException("安装文件过大");out.write(buf,0,n);}return out.toByteArray();
    }
    static void saveModel(Context c,String key,String model)throws Exception{
        if(key.isEmpty()||key.length()>4096||key.indexOf('\n')>=0||key.indexOf('\r')>=0||key.indexOf(0)>=0)throw new IOException("请填写有效的 API Key");
        if(!model.matches("[A-Za-z0-9._:/-]{1,160}"))throw new IOException("请填写有效的模型名称");
        // JSON is a YAML subset; no user value is interpolated into YAML syntax.
        JSONObject entry=new JSONObject().put("id","agent-default-model").put("name","@deepseek-ai/dsh-agent-default-model")
            .put("config",new JSONObject().put("provider","deepseek-official").put("model",model));
        File profile=new File(home(c),".dsh/profiles/mobile-share");if(!profile.isDirectory())throw new IOException("请先安装运行环境");
        atomicPrivate(new File(profile,"cordis.patch.yml"),new JSONArray().put(entry).toString()+"\n");
        String quoted="'"+key.replace("'","'\\''")+"'";
        atomicPrivate(new File(setup(c),"model-env.sh"),"export DEEPSEEK_API_KEY="+quoted+"\n");
    }
    private static void atomicPrivate(File target,String text)throws Exception{
        Path temp=new File(target.getPath()+".tmp").toPath();Files.write(temp,text.getBytes(StandardCharsets.UTF_8));Os.chmod(temp.toString(),0600);
        Files.move(temp,target.toPath(),StandardCopyOption.REPLACE_EXISTING);Os.chmod(target.getPath(),0600);
    }
}
