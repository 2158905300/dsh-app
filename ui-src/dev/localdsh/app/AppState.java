package dev.localdsh.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;

final class AppState {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("local-dsh", Context.MODE_PRIVATE); }
    static synchronized String token(Context c) {
        File file = new File(c.getFilesDir(), "bridge-token");
        try {
            if (file.exists()) return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            String value = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            try (java.io.FileOutputStream out = c.openFileOutput("bridge-token", Context.MODE_PRIVATE)) { out.write(value.getBytes(StandardCharsets.UTF_8)); }
            return value;
        } catch (Exception e) { throw new IllegalStateException("无法初始化本地连接", e); }
    }
    static boolean validUrl(String value) {
        try {
            android.net.Uri uri = android.net.Uri.parse(value);
            return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort() == 3080 && uri.getUserInfo() == null;
        } catch (Exception e) { return false; }
    }
}
