package dev.localdsh.app;

final class LogRedactor {
    static String clean(String value){
        return value.replaceAll("(?i)sk-[A-Za-z0-9_-]{8,}","[密钥已隐藏]")
            .replaceAll("(?i)(https?://)[^/\\s@]+@","$1[凭据已隐藏]@")
            .replaceAll("(?i)(Bearer\\s+)[^\\s\"'<>]+","$1[已隐藏]")
            .replaceAll("(?i)([?&](?:token|api_key|apikey|key|access_token)=)[^\\s&#\"'<>]+","$1[已隐藏]")
            .replaceAll("(?im)((?:DEEPSEEK_API_KEY|API_KEY|apiKey|api_key|access_token|authorization|bridge-token|phone-bridge-token|_authToken|npm_token|password|cookie)[\"']?\\s*[=:]\\s*)[^\\r\\n]+","$1[已隐藏]");
    }
}
