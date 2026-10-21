package dev.localdsh.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.UUID;
import java.util.concurrent.*;

/** Separate browsing surface. No JavaScript bridge, file access, or DSH URL/token. */
public class BrowserActivity extends Activity {
    public static volatile BrowserActivity instance;
    private WebView web;
    private TextView address;
    private String revision=UUID.randomUUID().toString();
    private boolean loading;
    private final java.util.ArrayList<String> consoleErrors=new java.util.ArrayList<>();
    private static final Handler main=new Handler(Looper.getMainLooper());
    public static boolean validUrl(String url) {
        try { Uri u=Uri.parse(url); return ("https".equals(u.getScheme())||"http".equals(u.getScheme()))&&u.getHost()!=null&&u.getUserInfo()==null; }
        catch(Exception e){return false;}
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); instance=this;
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.WHITE);
        LinearLayout bar=new LinearLayout(this);Button chat=new Button(this);chat.setText("返回 DSH");chat.setOnClickListener(v->finish());bar.addView(chat);
        address=new TextView(this);address.setText("DSH 工具浏览器");address.setTextSize(12);bar.addView(address,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);
        web=new WebView(this);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setSupportMultipleWindows(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){if(m.messageLevel()==ConsoleMessage.MessageLevel.ERROR){String text=m.message().replaceAll("token=[A-Za-z0-9_-]+","token=[redacted]");if(consoleErrors.size()>=6)consoleErrors.remove(0);consoleErrors.add(text.substring(0,Math.min(text.length(),350)));}return true;}});
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !validUrl(request.getUrl().toString());}
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){loading=true;consoleErrors.clear();revision=UUID.randomUUID().toString();address.setText(url);}
            @Override public void onPageFinished(WebView view,String url){loading=false;address.setText(url);}
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        String url=getIntent().getStringExtra("url");if(url!=null&&validUrl(url))web.loadUrl(url);
    }
    public static Object execute(android.content.Context context,JSONObject request) throws Exception {
        String action=request.getString("action");
        if("open".equals(action)) {
            String url=request.getString("url");if(!validUrl(url))throw new IllegalArgumentException("仅允许 http/https 网页地址");
            CompletableFuture<Object> future=new CompletableFuture<>();
            main.post(()->{try{BrowserActivity b=instance;if(b==null){Intent intent=new Intent(context,BrowserActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);intent.putExtra("url",url);context.startActivity(intent);}else{b.web.loadUrl(url);b.startActivity(new Intent(b,BrowserActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));}future.complete(new JSONObject().put("opening",url));}catch(Exception e){future.completeExceptionally(e);}});
            return future.get(5,TimeUnit.SECONDS);
        }
        CompletableFuture<Object> future=new CompletableFuture<>();
        main.post(()->{try{BrowserActivity b=instance;if(b==null)throw new IllegalStateException("请在 DSH App 点浏览器，或先调用 browser_phone_open");b.command(request,future);}catch(Exception e){future.completeExceptionally(e);}});
        return future.get(8,TimeUnit.SECONDS);
    }
    private void command(JSONObject request,CompletableFuture<Object> future) throws Exception {
        String action=request.getString("action");
        if("status".equals(action)){future.complete(new JSONObject().put("url",web.getUrl()).put("title",web.getTitle()).put("loading",loading).put("consoleErrors",new JSONArray(consoleErrors)));return;}
        if("back".equals(action)){if(web.canGoBack())web.goBack();future.complete(new JSONObject().put("performed",true));return;}
        if("scroll".equals(action)){int dy=request.optInt("dy",600);if(Math.abs((long)dy)>5000)throw new IllegalArgumentException("滚动距离过大");web.scrollBy(0,dy);future.complete(new JSONObject().put("performed",true));return;}
        String script;
        if("read".equals(action)) {
            String id=UUID.randomUUID().toString();
            script="(()=>{const nodes=Array.from(document.querySelectorAll('a,button,input,textarea,select,[role=button],[contenteditable=true]')).filter(e=>e.getClientRects().length&&!e.hidden).slice(0,120);window.__dshPhoneElements={id:"+JSONObject.quote(id)+",nodes};return {url:location.href,title:document.title,snapshot:"+JSONObject.quote(revision+":"+id)+",text:(document.body?.innerText||'').slice(0,16000),elements:nodes.map((e,i)=>({id:String(i),tag:e.tagName.toLowerCase(),role:e.getAttribute('role')||'',text:(e.innerText||e.getAttribute('aria-label')||e.getAttribute('placeholder')||'').slice(0,180),type:e.type||'',href:e.tagName==='A'?e.href:undefined,password:e.type==='password'}))};})()";
        } else if("click".equals(action)||"fill".equals(action)) {
            String expected=request.getString("snapshot");if(!expected.startsWith(revision+":"))throw new IllegalStateException("页面已变，请重新读取网页");
            String id=expected.substring(revision.length()+1);String node=request.getString("node");if(!node.matches("[0-9]{1,3}"))throw new IllegalArgumentException("网页元素编号无效");
            String op;
            if("fill".equals(action)) {
                String value=request.getString("text");if(value.length()>8000)throw new IllegalArgumentException("输入过长");
                op="if(e.type==='password')throw Error('不操作密码框，请手动输入');if(e.disabled||e.readOnly)throw Error('输入框不可编辑');e.focus();const value="+JSONObject.quote(value)+";if(e.isContentEditable){const r=document.createRange();r.selectNodeContents(e);const sel=window.getSelection();sel.removeAllRanges();sel.addRange(r);if(!document.execCommand('insertText',false,value))throw Error('网页编辑器拒绝输入');}else{const setter=Object.getOwnPropertyDescriptor(e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype,'value')?.set;if(!setter)throw Error('不是普通输入框');setter.call(e,value);}e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));";
            }else op="e.scrollIntoView({block:'center'});e.click();";
            script="(()=>{const s=window.__dshPhoneElements;if(!s||s.id!=="+JSONObject.quote(id)+")throw Error('网页快照过期');const e=s.nodes["+node+"];if(!e||!e.isConnected||!e.getClientRects().length)throw Error('元素已失效');"+op+"return {performed:true};})()";
        } else throw new IllegalArgumentException("不支持的浏览器操作");
        web.evaluateJavascript("JSON.stringify((()=>{try{return {ok:true,result:"+script+"};}catch(e){return {ok:false,error:String(e.message||e)};}})())",value->{try{Object decoded=new JSONTokener(value).nextValue();JSONObject result=new JSONObject(String.valueOf(decoded));if(!result.optBoolean("ok"))future.completeExceptionally(new IllegalStateException(result.optString("error")));else future.complete(result.get("result"));}catch(Exception e){future.completeExceptionally(e);}});
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(instance==this)instance=null;if(web!=null)web.destroy();super.onDestroy();}
}
