package dev.localdsh.app;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class DiagnosticLogChecks {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        File directory=new File(args[0]);directory.mkdirs();
        File older=new File(directory,"old-debug.log"),newer=new File(directory,"new-debug.log");
        Files.write(older.toPath(),"older\n".getBytes(StandardCharsets.UTF_8));older.setLastModified(1000);
        String detail="build context\ngyp ERR! missing compiler header\nnpm error code 1\n_authToken=NPM_SECRET_SAMPLE\nhttps://USER_SECRET:PASS_SECRET@registry.example/package\nnpm error A complete log of this run can be found in: /home/.npm/_logs/debug.log\n";
        Files.write(newer.toPath(),detail.getBytes(StandardCharsets.UTF_8));newer.setLastModified(2000);
        Files.write(new File(directory,"not-a-log.txt").toPath(),"excluded".getBytes(StandardCharsets.UTF_8));
        File[] files=DiagnosticLogs.npmFiles(directory);check(files.length==2&&files[0].equals(newer),"Newest npm log selection failed");
        String body=DiagnosticLogs.tail(newer),error=DiagnosticLogs.errors(body);
        check(error.contains("missing compiler header"),"Real error hidden behind footer");
        check(!error.contains("NPM_SECRET_SAMPLE")&&!error.contains("PASS_SECRET"),"npm credentials not redacted");
        check(DiagnosticLogs.errors("npm error A complete log of this run can be found in: debug.log").contains("没有找到具体错误行"),"Log footer must not be mistaken for cause");
        System.out.println("npm log selection, actual error context and credential-safe copying checks passed.");
    }
}
