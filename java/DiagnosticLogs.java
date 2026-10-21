package dev.localdsh.app;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class DiagnosticLogs {
    static File[] npmFiles(File directory){
        File[] all=directory.listFiles();if(all==null)return new File[0];ArrayList<File> files=new ArrayList<>();
        try{String base=directory.getCanonicalPath()+File.separator;
            for(File file:all)if(file.isFile()&&file.getName().endsWith(".log")&&file.getCanonicalPath().startsWith(base))files.add(file);
        }catch(IOException ignored){}
        files.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));return files.subList(0,Math.min(12,files.size())).toArray(new File[0]);
    }
    static String tail(File file)throws IOException{
        try(RandomAccessFile in=new RandomAccessFile(file,"r")){
            long length=in.length(),offset=Math.max(0,length-96000);in.seek(offset);byte[] bytes=new byte[(int)(length-offset)];in.readFully(bytes);
            String text=new String(bytes,StandardCharsets.UTF_8);if(offset>0){int split=text.indexOf('\n');if(split>=0)text=text.substring(split+1);}
            return (offset>0?"（只显示最近约 96 KB）\n":"")+LogRedactor.clean(text.replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]",""));
        }
    }
    static String errors(String text){
        String[] lines=text.split("\n");boolean[] keep=new boolean[lines.length];boolean found=false;
        for(int i=0;i<lines.length;i++){
            String line=lines[i].toLowerCase(Locale.ROOT);if(line.contains("a complete log of this run"))continue;
            if(line.matches(".*(?:\\berror\\b|gyp err!|cannot link|cannot locate symbol|fatal:|\\be: |enotfound|econnreset|etimedout|eai_again).*")){
                found=true;for(int j=Math.max(0,i-5);j<Math.min(lines.length,i+9);j++)keep[j]=true;
            }
        }
        if(!found)return "没有找到具体错误行，请选择最近的 npm 详细日志，或查看完整日志。\n（A complete log… 只是日志路径提示。）";
        StringBuilder out=new StringBuilder();boolean previous=false;
        for(int i=0;i<lines.length;i++){if(keep[i]){if(!previous&&out.length()>0)out.append("\n…\n");out.append(lines[i]).append('\n');}previous=keep[i];}
        String result=out.toString();return result.length()>24000?result.substring(result.length()-24000):result;
    }
}
