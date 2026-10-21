package dev.localdsh.app;

import android.accessibilityservice.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.util.Base64;
import android.view.Display;
import android.view.accessibility.*;
import org.json.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;

public class PhoneAccessibilityService extends AccessibilityService {
    public static volatile PhoneAccessibilityService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String,AccessibilityNodeInfo> snapshotNodes = new HashMap<>();
    private String snapshot = "";
    private long snapshotTime;
    private int snapshotWindow;
    @Override protected void onServiceConnected() { instance=this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { if(instance==this) instance=null; clearSnapshot(); super.onDestroy(); }
    private <T> T onMain(Callable<T> action) throws Exception {
        FutureTask<T> task=new FutureTask<>(action); main.post(task);
        try { return task.get(5,TimeUnit.SECONDS); }
        catch(ExecutionException e) { throw new IllegalStateException(e.getCause().getMessage()); }
    }
    public Object execute(JSONObject request) throws Exception {
        switch(request.getString("action")) {
            case "screen": return onMain(() -> screen());
            case "tap":
                if(request.has("node")) return onMain(() -> tapNode(request.getString("node"),request.getString("snapshot")));
                return gesture(request.getInt("x"),request.getInt("y"),request.getInt("x"),request.getInt("y"),80);
            case "swipe": return gesture(request.getInt("x1"),request.getInt("y1"),request.getInt("x2"),request.getInt("y2"),request.optInt("duration",400));
            case "text": return onMain(() -> setText(request.getString("text")));
            case "key": return onMain(() -> {
                int action;
                switch(request.getString("key")) {
                    case "back": action=GLOBAL_ACTION_BACK; break;
                    case "home": action=GLOBAL_ACTION_HOME; break;
                    case "recents": action=GLOBAL_ACTION_RECENTS; break;
                    case "notifications": action=GLOBAL_ACTION_NOTIFICATIONS; break;
                    default: throw new IllegalArgumentException("不支持的系统按键");
                }
                return new JSONObject().put("performed",performGlobalAction(action));
            });
            case "open": return onMain(() -> {
                String pkg=request.getString("package");
                if(!pkg.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")) throw new IllegalArgumentException("应用包名无效");
                Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
                if(launch==null) throw new IllegalStateException("应用未安装或没有可启动入口");
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch);
                return new JSONObject().put("performed",true);
            });
            case "screenshot": return screenshot();
            default: throw new IllegalArgumentException("不支持的手机操作");
        }
    }
    private void clearSnapshot() { for(AccessibilityNodeInfo node:snapshotNodes.values()) node.recycle(); snapshotNodes.clear(); }
    private JSONObject screen() throws Exception {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) throw new IllegalStateException("当前没有可读取的界面");
        clearSnapshot(); snapshot=UUID.randomUUID().toString(); snapshotTime=SystemClock.elapsedRealtime(); snapshotWindow=root.getWindowId();
        JSONArray nodes=new JSONArray();
        try {
            walk(root,"0",0,nodes);
            return new JSONObject().put("snapshot",snapshot).put("package",String.valueOf(root.getPackageName()))
                .put("width",getResources().getDisplayMetrics().widthPixels).put("height",getResources().getDisplayMetrics().heightPixels).put("nodes",nodes);
        } finally { root.recycle(); }
    }
    private void walk(AccessibilityNodeInfo node,String id,int depth,JSONArray result) throws Exception {
        if(depth>22 || result.length()>=250) return;
        if(node.isVisibleToUser()) {
            Rect bounds=new Rect(); node.getBoundsInScreen(bounds);
            String text=node.isPassword()?"[密码内容隐藏]":String.valueOf(node.getText()==null?"":node.getText());
            String description=node.isPassword()?"":String.valueOf(node.getContentDescription()==null?"":node.getContentDescription());
            JSONObject value=new JSONObject().put("id",id).put("class",String.valueOf(node.getClassName())).put("text",shortText(text))
                .put("description",shortText(description)).put("clickable",node.isClickable()).put("editable",node.isEditable()&&!node.isPassword())
                .put("bounds",new JSONArray(new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom}));
            result.put(value); snapshotNodes.put(id,AccessibilityNodeInfo.obtain(node));
        }
        for(int i=0;i<node.getChildCount() && result.length()<250;i++) {
            AccessibilityNodeInfo child=node.getChild(i); if(child==null) continue;
            try { walk(child,id+"."+i,depth+1,result); } finally { child.recycle(); }
        }
    }
    private String shortText(String value) { return value.length()>300?value.substring(0,300):value; }
    private JSONObject tapNode(String id,String expected) throws Exception {
        if(!snapshot.equals(expected)||SystemClock.elapsedRealtime()-snapshotTime>45000) throw new IllegalStateException("界面快照已过期，请重新读取 screen");
        AccessibilityNodeInfo root=getRootInActiveWindow();
        try { if(root==null||root.getWindowId()!=snapshotWindow) throw new IllegalStateException("界面已切换，请重新读取 screen"); }
        finally { if(root!=null) root.recycle(); }
        AccessibilityNodeInfo cached=snapshotNodes.get(id);
        if(cached==null||!cached.refresh()||!cached.isVisibleToUser()||cached.isPassword()) throw new IllegalStateException("目标已失效，请重新读取 screen");
        AccessibilityNodeInfo node=AccessibilityNodeInfo.obtain(cached);
        for(int depth=0;node!=null && depth<8;depth++) {
            if(node.isClickable()&&node.isEnabled()) { boolean done=node.performAction(AccessibilityNodeInfo.ACTION_CLICK); node.recycle(); return new JSONObject().put("performed",done); }
            AccessibilityNodeInfo parent=node.getParent(); node.recycle(); node=parent;
        }
        if(node!=null) node.recycle(); throw new IllegalStateException("目标没有可点击的控件，请使用当前画面坐标");
    }
    private JSONObject setText(String value) throws Exception {
        if(value.length()>8000) throw new IllegalArgumentException("输入内容过长");
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null) throw new IllegalStateException("没有当前窗口");
        try {
            AccessibilityNodeInfo focus=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if(focus==null) throw new IllegalStateException("请先聚焦输入框");
            try {
                if(!focus.isEditable()||focus.isPassword()) throw new IllegalStateException("不是可输入的普通文本框；不操作密码框");
                Bundle args=new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
                return new JSONObject().put("performed",focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));
            } finally { focus.recycle(); }
        } finally { root.recycle(); }
    }
    private JSONObject gesture(int x1,int y1,int x2,int y2,int duration) throws Exception {
        int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;
        if(x1<0||x1>=width||x2<0||x2>=width||y1<0||y1>=height||y2<0||y2>=height||duration<40||duration>3000) throw new IllegalArgumentException("手势参数超出范围");
        CompletableFuture<Boolean> done=new CompletableFuture<>();
        main.post(() -> {
            Path path=new Path(); path.moveTo(x1,y1); if(x1!=x2||y1!=y2) path.lineTo(x2,y2);
            GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
            boolean accepted=dispatchGesture(gesture,new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription value) { done.complete(true); }
                @Override public void onCancelled(GestureDescription value) { done.complete(false); }
            },null);
            if(!accepted) done.complete(false);
        });
        return new JSONObject().put("performed",done.get(5,TimeUnit.SECONDS));
    }
    private JSONObject screenshot() throws Exception {
        if(Build.VERSION.SDK_INT<30) throw new IllegalStateException("截图需要 Android 11 或更新系统");
        CompletableFuture<Bitmap> image=new CompletableFuture<>();
        main.post(() -> takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                try {
                    Bitmap hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());
                    if(hardware==null) throw new IllegalStateException("无法读取截图");
                    Bitmap copy=hardware.copy(Bitmap.Config.ARGB_8888,false); hardware.recycle(); image.complete(copy);
                } catch(Exception e) { image.completeExceptionally(e); }
                finally { result.getHardwareBuffer().close(); }
            }
            @Override public void onFailure(int error) { image.completeExceptionally(new IllegalStateException("系统拒绝截图（"+error+"）；受保护窗口不能截图")); }
        }));
        Bitmap bitmap=image.get(5,TimeUnit.SECONDS);
        try {
            int width=Math.min(bitmap.getWidth(),1080); Bitmap scaled=Bitmap.createScaledBitmap(bitmap,width,Math.max(1,bitmap.getHeight()*width/bitmap.getWidth()),true);
            try {
                ByteArrayOutputStream out=new ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG,82,out);
                return new JSONObject().put("format","jpeg").put("data",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));
            } finally { if(scaled!=bitmap) scaled.recycle(); }
        } finally { bitmap.recycle(); }
    }
}
