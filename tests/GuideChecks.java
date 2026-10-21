package dev.localdsh.app;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class GuideChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        String script=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
        int previous=2,count=1;
        check(InstallSteps.index("正在刷新软件源（清华镜像，单次最多约 2 分钟）")==2,"Repository progress milestone missing");
        for(String line:script.split("\n"))if(line.contains("printf 'HARNESS_STAGE: ")){
            int step=InstallSteps.index(line);check(step>previous,"Installer/UI milestone mismatch");previous=step;count++;
        }
        check(previous==7&&count==6,"Installer completion milestone missing");
        check(InstallSteps.index("安装未完成，请检查日志")==0,"Failure must not count as completion");
        check(InstallSteps.index("正在下载并编译 DSH 原生依赖")==3,"Native dependency substage missing");
        check(InstallSteps.index("正在编译 sharp 图片处理模块")==3,"Image compiler substage missing");
        check(InstallSteps.checklist(3,false,false).contains("3. 下载依赖并编译 DSH · 未完成"),"Stopped install must show unfinished");
        check(!InstallSteps.checklist(3,false,false).contains("✓ 7."),"Partial install cannot show success");
        check(GuideContent.TITLES.length==12&&GuideContent.BODIES.length==12,"Offline guide chapters missing");
        for(String body:GuideContent.BODIES)check(body.length()>90,"Guide chapter empty");
        String samples="DEEPSEEK_API_KEY=ENV_SECRET_SAMPLE\n{\"apiKey\":\"JSON_SECRET_SAMPLE\"}\nBearer BEARER_SECRET_SAMPLE\nhttp://127.0.0.1:3080/?token=TOKEN_SECRET_SAMPLE\nsk-TEST_SECRET_SAMPLE_12345\n";
        String clean=LogRedactor.clean(samples);
        for(String secret:new String[]{"ENV_SECRET_SAMPLE","JSON_SECRET_SAMPLE","BEARER_SECRET_SAMPLE","TOKEN_SECRET_SAMPLE","TEST_SECRET_SAMPLE_12345"})check(!clean.contains(secret),"Diagnostic credential redaction failed");
        check(LogRedactor.clean("clang: error: missing header\n").contains("missing header"),"Useful error was removed");
        check(InstallerProcessGroup.matches(123,"123 (bash installer) S 12 123 123 0 0"),"Installer own process group rejected");
        check(!InstallerProcessGroup.matches(123,"123 (bash) S 12 12 12 0 0"),"Parent process group must never be signalled");
        check(!InstallerProcessGroup.matches(123,"456 (bash) S 12 123 123 0 0"),"Mismatched process ID must be rejected");
        check(!InstallerProcessGroup.matches(1,"1 (init) S 0 1 1 0 0"),"System process must never be signalled");
        System.out.println("Offline guide, real install milestones, stopped-install states and diagnostic redaction checks passed.");
    }
}
