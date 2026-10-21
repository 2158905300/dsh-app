package dev.localdsh.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

/** Native offline help, usable before bootstrap, network access or model setup. */
public final class GuideActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),dp(20),dp(20),dp(32));box.setBackgroundColor(Color.WHITE);scroll.addView(box);
        TextView title=new TextView(this);title.setText("安装与使用指南");title.setTextSize(26);title.setTextColor(Color.rgb(25,45,75));box.addView(title);
        TextView note=new TextView(this);note.setText("DeepSeek Harness · 可离线阅读\n点主题展开内容；查看指南不会停止安装或退出当前会话。\n");box.addView(note);
        Button back=new Button(this);back.setText("返回刚才的页面");back.setOnClickListener(v->finish());box.addView(back);
        int expanded=getIntent().getIntExtra("topic",0);
        final View[] jump=new View[1];
        for(int i=0;i<GuideContent.TITLES.length;i++){
            final TextView body=new TextView(this);body.setText(GuideContent.BODIES[i]);body.setTextSize(16);body.setTextColor(Color.rgb(35,45,60));body.setLineSpacing(dp(4),1);body.setPadding(dp(8),dp(10),dp(8),dp(18));body.setTextIsSelectable(true);body.setVisibility(i==expanded?View.VISIBLE:View.GONE);
            Button topic=new Button(this);topic.setAllCaps(false);topic.setText((i+1)+". "+GuideContent.TITLES[i]);topic.setOnClickListener(v->body.setVisibility(body.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));box.addView(topic);box.addView(body);if(i==expanded)jump[0]=topic;
        }
        action(box,"安装日志与诊断",()->startActivity(new Intent(this,DiagnosticsActivity.class)));
        action(box,"打开系统无障碍设置",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        action(box,"打开后台电池设置",()->startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        action(box,"打开 DeepSeek 官方控制台",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://platform.deepseek.com/"))));
        setContentView(scroll);
        if(expanded>0)scroll.post(()->{if(jump[0]!=null)scroll.scrollTo(0,jump[0].getTop());});
    }
    private void action(LinearLayout box,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->{try{action.run();}catch(Exception e){Toast.makeText(this,"当前系统无法打开这个入口，请到系统设置中手动查找。",Toast.LENGTH_LONG).show();}});box.addView(b);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
