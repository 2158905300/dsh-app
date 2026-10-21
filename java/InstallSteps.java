package dev.localdsh.app;

/** Milestones, not a claimed percentage of download/compile work. */
final class InstallSteps {
    static final String[] LABELS={"初始化基础环境","刷新软件源","下载依赖并编译 DSH","应用安卓补丁并检查沙箱","配置基础手机和浏览器工具","验证运行环境","安装完成，填写自己的 Key"};
    static int index(String status){
        if(status.contains("安装完成")||status.contains("运行环境安装完成"))return 7;
        if(status.contains("验证运行环境"))return 6;
        if(status.contains("配置基础插件"))return 5;
        if(status.contains("安卓兼容补丁"))return 4;
        if(status.contains("安装 Node")||status.contains("编译 DSH")||status.contains("编译 sharp"))return 3;
        if(status.contains("刷新软件源"))return 2;
        if(status.contains("基础环境")||status.contains("准备运行环境"))return 1;
        return 0;
    }
    static String checklist(int step,boolean ready,boolean running){
        StringBuilder text=new StringBuilder();
        for(int i=1;i<=LABELS.length;i++){
            boolean done=ready||i<step;
            text.append(done?"✓ ":i==step?(running?"● ":"! "):"○ ").append(i).append(". ").append(LABELS[i-1]);
            if(i==step&&!ready)text.append(running?" · 进行中":" · 未完成");
            if(i<LABELS.length)text.append('\n');
        }
        return text.toString();
    }
}
