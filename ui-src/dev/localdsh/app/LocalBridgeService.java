package dev.localdsh.app;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class LocalBridgeService extends Service {
    private ServerSocket server;
    private ExecutorService clients;
    private volatile boolean stopped;
    private String token;
    @Override public void onCreate() {
        super.onCreate(); token = AppState.token(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("local-dsh", "DSH 手机本地连接", NotificationManager.IMPORTANCE_LOW));
        clients = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.AbortPolicy());
        showNotification();
        new Thread(() -> {
            try {
                server = new ServerSocket(); server.setReuseAddress(true); server.bind(new InetSocketAddress("127.0.0.1", 3081));
                while (!stopped) {
                    Socket socket = server.accept();
                    try { clients.execute(() -> handle(socket)); } catch (RejectedExecutionException e) { socket.close(); }
                }
            } catch (IOException e) { if (!stopped) stopSelf(); }
        }, "dsh-loopback").start();
    }
    private void showNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notice = new Notification.Builder(this, "local-dsh").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("DSH 手机本地连接")
            .setContentText(AppState.prefs(this).getBoolean("controlEnabled", false) ? "手机控制已允许 · 可在 App 中关闭" : "聊天可用 · 手机控制已关闭")
            .setContentIntent(pending).setOngoing(true).build();
        startForeground(3081, notice);
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) { showNotification(); return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    private void handle(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(6000);
            InputStream in = new BufferedInputStream(client.getInputStream());
            String first = line(in, 2048);
            String[] request = first.split(" "); if (request.length != 3) throw new IOException("Bad request");
            Map<String,String> headers = new HashMap<>(); int headerSize = first.length();
            for (int n=0;n<40;n++) {
                String line = line(in, 4096); headerSize += line.length();
                if (headerSize > 16384) throw new IOException("Headers too large");
                if (line.isEmpty()) break;
                int colon = line.indexOf(':'); if (colon < 1) throw new IOException("Bad header");
                String key = line.substring(0,colon).toLowerCase(Locale.ROOT);
                if (headers.containsKey(key)) throw new IOException("Duplicate header");
                headers.put(key,line.substring(colon+1).trim());
            }
            if (headers.containsKey("origin") || headers.containsKey("transfer-encoding")) { respond(client,403,new JSONObject().put("error","Browser origins and transfer encodings are not allowed")); return; }
            String auth = headers.getOrDefault("authorization", "");
            if (!MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8), ("Bearer "+token).getBytes(StandardCharsets.UTF_8))) { respond(client,401,new JSONObject().put("error","unauthorized")); return; }
            if ("GET".equals(request[0]) && "/status".equals(request[1])) {
                respond(client,200,new JSONObject().put("app","DSH 手机端").put("version",getPackageManager().getPackageInfo(getPackageName(),0).versionName).put("controlEnabled",AppState.prefs(this).getBoolean("controlEnabled",false)).put("accessibilityEnabled",PhoneAccessibilityService.instance!=null)); return;
            }
            if (!"POST".equals(request[0]) || !("/command".equals(request[1]) || "/browser".equals(request[1]) || "/engine-url".equals(request[1]) || "/engine-start".equals(request[1]))) { respond(client,404,new JSONObject().put("error","not_found")); return; }
            if (!headers.getOrDefault("content-type", "").toLowerCase(Locale.ROOT).startsWith("application/json")) { respond(client,415,new JSONObject().put("error","JSON required")); return; }
            int length = Integer.parseInt(headers.getOrDefault("content-length","-1"));
            if (length < 1 || length > 65536) { respond(client,413,new JSONObject().put("error","Body size invalid")); return; }
            byte[] body = new byte[length]; int offset=0;
            while (offset<length) { int got=in.read(body,offset,length-offset); if (got<0) throw new EOFException(); offset+=got; }
            JSONObject command = new JSONObject(new String(body,StandardCharsets.UTF_8));
            if ("/engine-url".equals(request[1])) {
                String url=command.getString("url"); if (!AppState.validUrl(url)) { respond(client,400,new JSONObject().put("error","Only the phone localhost DSH URL is allowed")); return; }
                AppState.prefs(this).edit().putString("engineUrl",url).apply(); respond(client,200,new JSONObject().put("ok",true)); return;
            }
            if (!AppState.prefs(this).getBoolean("controlEnabled",false)) { respond(client,403,new JSONObject().put("error","手机控制已关闭，请在 DSH App 的权限页启用。")); return; }
            if ("/engine-start".equals(request[1])) {
                try {
                    Intent run=new Intent("com.termux.RUN_COMMAND");run.setComponent(new ComponentName("com.termux","com.termux.app.RunCommandService"));
                    run.putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/usr/bin/dsh-app-start");run.putExtra("com.termux.RUN_COMMAND_WORKDIR","/data/data/com.termux/files/home");run.putExtra("com.termux.RUN_COMMAND_BACKGROUND",true);startService(run);
                    respond(client,200,new JSONObject().put("ok",true));
                } catch(Exception e) { respond(client,422,new JSONObject().put("error","Termux 本地命令启动失败，请在 App 中检查权限。")); }
                return;
            }
            if ("/browser".equals(request[1])) {
                try { respond(client,200,new JSONObject().put("ok",true).put("result",BrowserActivity.execute(this,command))); }
                catch(Exception e) { Throwable reason=e.getCause()==null?e:e.getCause();respond(client,422,new JSONObject().put("error",reason.getMessage()==null?"网页操作未完成":reason.getMessage())); }
                return;
            }
            PhoneAccessibilityService access=PhoneAccessibilityService.instance;
            if(access==null) { respond(client,409,new JSONObject().put("error","请先在系统无障碍设置中启用 DSH 手机控制。")); return; }
            try { respond(client,200,new JSONObject().put("ok",true).put("result",access.execute(command))); }
            catch(Exception e) { respond(client,422,new JSONObject().put("error",e.getMessage()==null?"操作未完成":e.getMessage())); }
        } catch(Exception ignored) { /* No request bodies, tokens, or screen content are logged. */ }
    }
    private static String line(InputStream in,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int n=0;n<limit;n++) { int value=in.read(); if(value<0) throw new EOFException(); if(value=='\n') return new String(out.toByteArray(),StandardCharsets.ISO_8859_1).replace("\r",""); out.write(value); }
        throw new IOException("Line too long");
    }
    private static void respond(Socket socket,int status,JSONObject value) throws IOException {
        byte[] body=value.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream out=socket.getOutputStream();
        out.write(("HTTP/1.1 "+status+" Result\r\nContent-Type: application/json; charset=utf-8\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\nContent-Length: "+body.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); out.write(body); out.flush();
    }
    @Override public void onDestroy() { stopped=true; try { if(server!=null) server.close(); } catch(IOException ignored) {} if(clients!=null) clients.shutdownNow(); super.onDestroy(); }
}
