package dev.localdsh.app;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.*;

public final class DiagnosticsActivity extends Activity {
    private TextView info,log;
    private ScrollView scroll;
    private Spinner source;
    private Switch live;
    private final ArrayList<File> files=new ArrayList<>();
    private String shown="",body="",stamp="";
    private boolean errorsOnly;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){if(live.isChecked())refresh(false);handler.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(30,30,30,30);scroll.addView(box);
        TextView title=new TextView(this);title.setText("安装日志与诊断");title.setTextSize(25);box.addView(title);
        TextView note=new TextView(this);note.setText("默认暂停更新，方便向上翻看，不会自动跳到开头或末尾。可选择 npm 详细日志，再复制错误摘要。常见凭据会被遮住，分享前仍请检查。\n");box.addView(note);
        info=new TextView(this);box.addView(info);source=new Spinner(this);box.addView(source);reloadFiles();
        live=new Switch(this);live.setText("实时更新（默认关闭，手动刷新也可以）");live.setChecked(false);live.setOnCheckedChangeListener((v,on)->{if(on)refresh(false);});box.addView(live);
        button(box,"刷新并重新查找 npm 日志",()->{reloadFiles();refresh(true);});
        Button mode=new Button(this);mode.setText("只看错误上下文");mode.setOnClickListener(v->{errorsOnly=!errorsOnly;mode.setText(errorsOnly?"显示日志原文（最近约 96 KB）":"只看错误上下文");render();});box.addView(mode);
        button(box,"复制错误摘要",()->{String excerpt=DiagnosticLogs.errors(body);android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("Harness 错误摘要",excerpt));Toast.makeText(this,"已复制错误摘要；发送前请检查敏感信息。",Toast.LENGTH_LONG).show();});
        button(box,"到日志末尾",()->scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN)));
        button(box,"返回，安装继续",()->finish());
        button(box,"常见错误与排查",()->startActivity(new Intent(this,GuideActivity.class).putExtra("topic",9)));
        log=new TextView(this);log.setTextSize(12);log.setTypeface(android.graphics.Typeface.MONOSPACE);log.setTextIsSelectable(true);box.addView(log);
        source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){stamp="";refresh(true);}public void onNothingSelected(AdapterView<?> p){}});
        setContentView(scroll);refresh(true);
    }
    private void button(LinearLayout box,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());box.addView(b);}
    private void reloadFiles(){
        String selected=source.getSelectedItemPosition()>=0&&source.getSelectedItemPosition()<files.size()?files.get(source.getSelectedItemPosition()).getPath():"";
        files.clear();files.add(new File(getFilesDir(),"harness-install.log"));files.addAll(Arrays.asList(DiagnosticLogs.npmFiles(new File(SetupFiles.home(this),".npm/_logs"))));
        ArrayList<String> labels=new ArrayList<>();labels.add("安装总日志");int position=0;
        for(int i=1;i<files.size();i++){labels.add("npm："+files.get(i).getName());if(files.get(i).getPath().equals(selected))position=i;}
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);source.setAdapter(adapter);source.setSelection(position);
    }
    @Override protected void onResume(){super.onResume();handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    private void refresh(boolean force){
        if(log==null||files.isEmpty())return;int index=Math.max(0,Math.min(files.size()-1,source.getSelectedItemPosition()));File file=files.get(index);
        String status=getSharedPreferences("harness-install",0).getString("status","尚未开始安装");
        String nextInfo="状态："+status+"\n来源："+(index==0?"安装总日志":file.getName())+"\n更新："+(live.isChecked()?"实时 · 保持当前阅读位置":"暂停 · 可自由翻阅")+"\n";
        if(!info.getText().toString().equals(nextInfo))info.setText(nextInfo);
        String nextStamp=file.getPath()+":"+file.length()+":"+file.lastModified();if(!force&&nextStamp.equals(stamp))return;stamp=nextStamp;
        if(!file.isFile())body="尚无安装日志。初始化完成后这里会出现输出。若 npm 已报错，请点刷新，再从上方选择 npm 详细日志。";
        else try{body=DiagnosticLogs.tail(file);}catch(IOException e){body="暂时无法读取日志，可稍后手动刷新。无需卸载或清除数据。";}render();
    }
    private void render(){String text=errorsOnly?DiagnosticLogs.errors(body):body;if(text.equals(shown))return;shown=text;int y=scroll.getScrollY();log.setText(text);scroll.post(()->scroll.scrollTo(0,y));}
}
