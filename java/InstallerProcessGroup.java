package dev.localdsh.app;

final class InstallerProcessGroup {
    static boolean matches(int pid,String stat){
        if(pid<=1)return false;
        try{
            int open=stat.indexOf('('),close=stat.lastIndexOf(')');
            if(open<1||close<=open||Integer.parseInt(stat.substring(0,open).trim())!=pid)return false;
            String[] fields=stat.substring(close+1).trim().split("\\s+");
            return fields.length>=4&&Integer.parseInt(fields[2])==pid&&Integer.parseInt(fields[3])==pid;
        }catch(RuntimeException e){return false;}
    }
}
