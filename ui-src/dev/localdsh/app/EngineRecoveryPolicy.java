package dev.localdsh.app;

/** Foreground recovery budget. Never repeatedly launches the runtime while it boots. */
public final class EngineRecoveryPolicy {
    private int attempts;
    private long nextAllowedAt;
    public void enteredForeground(){ attempts=0; }
    public void healthy(){ attempts=0;nextAllowedAt=0; }
    public void retryNow(){ attempts=0;nextAllowedAt=0; }
    public boolean tryStart(long now){
        if(attempts>=3||now<nextAllowedAt)return false;
        attempts++;nextAllowedAt=now+20000;return true;
    }
    public boolean exhausted(){ return attempts>=3; }
}
