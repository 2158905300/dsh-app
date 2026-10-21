package dev.localdsh.app;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public final class LauncherActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        startActivity(new Intent(this,SetupFiles.ready(this)&&SetupFiles.modelReady(this)?MainActivity.class:SetupActivity.class));
        finish();
    }
}
