param([string]$AndroidSdk='E:\deepseek\android',[string]$Jdk='E:\deepseek\android\jdk17')
$ErrorActionPreference='Stop'
$project=$PSScriptRoot
$env:JAVA_HOME=$Jdk
$java=Join-Path $Jdk 'bin\java.exe'
$platform=Join-Path $AndroidSdk 'platforms\android-35\android.jar'
$bt=Join-Path $AndroidSdk 'build-tools\34.0.0'
$apktool=Join-Path $project 'tools\apktool_3.0.3.jar'
$base=Join-Path $project 'upstream\termux-arm64.apk'
$decoded=Join-Path $project 'build\decoded'
function Assert-Step{if($LASTEXITCODE -ne 0){throw "Build step exited $LASTEXITCODE"}}
if((Get-FileHash $apktool -Algorithm SHA256).Hash-ne'DBF930B076C6B9BE08D57C449CACEFC3BDD6B71EBD59B3066FC0E1F5B14F9423'){throw 'Apktool digest mismatch'}
if((Get-FileHash $base -Algorithm SHA256).Hash-ne'72FDB596045116BF5BA1B5BDF5B26FDDB9ACC0BD074AD9F2DA9EB0AE85E83A4E'){throw 'Termux digest mismatch'}
& node (Join-Path $project 'check-assets.cjs');Assert-Step
& $java -jar $apktool d --no-src --force --frame-path (Join-Path $project 'build\framework') -o $decoded $base;Assert-Step
& (Join-Path $project 'patch-manifest.ps1') -Decoded $decoded
& node (Join-Path $project 'prepare-sources.cjs');Assert-Step
New-Item -ItemType Directory -Path (Join-Path $decoded 'assets') -Force|Out-Null
Copy-Item -LiteralPath (Join-Path $project 'assets\harness') -Destination (Join-Path $decoded 'assets') -Recurse -Force
New-Item -ItemType Directory -Path (Join-Path $project 'build\classes'),(Join-Path $project 'build\dex') -Force|Out-Null
$sources=@(Get-ChildItem (Join-Path $project 'build\src') -Recurse -Filter '*.java'|ForEach-Object FullName)
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 -source 8 -target 8 -classpath $platform -d (Join-Path $project 'build\classes') $sources;Assert-Step
& (Join-Path $Jdk 'bin\jar.exe') cf (Join-Path $project 'build\classes.jar') -C (Join-Path $project 'build\classes') .;Assert-Step
& (Join-Path $bt 'd8.bat') --min-api 30 --lib $platform --output (Join-Path $project 'build\dex') (Join-Path $project 'build\classes.jar');Assert-Step
Copy-Item (Join-Path $project 'build\dex\classes.dex') (Join-Path $decoded 'classes30.dex')
$unsigned=Join-Path $project 'build\unsigned.apk'
& $java -jar $apktool b --frame-path (Join-Path $project 'build\framework') -o $unsigned $decoded;Assert-Step
$aligned=Join-Path $project 'build\aligned.apk'
& (Join-Path $bt 'zipalign.exe') -p -f 4 $unsigned $aligned;Assert-Step
# 签名文件默认放在 <项目>\keystore\（已被 .gitignore 排除）。
# 公开构建或需要固定签名时，可用环境变量 HARNESS_KEYSTORE_DIR 指向仓库外的目录。
$ksDir=if($env:HARNESS_KEYSTORE_DIR){$env:HARNESS_KEYSTORE_DIR}else{Join-Path $project 'keystore'}
$store=Join-Path $ksDir 'share-release.jks'
$passwordFile=Join-Path $ksDir 'password.txt'
New-Item -ItemType Directory -Path (Split-Path $store) -Force|Out-Null
if(!(Test-Path -LiteralPath $passwordFile)){
    $bytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();$rng.GetBytes($bytes);$rng.Dispose()
    [IO.File]::WriteAllText($passwordFile,[Convert]::ToBase64String($bytes))
}
$env:HARNESS_KEYSTORE_PASSWORD=[IO.File]::ReadAllText($passwordFile).Trim()
try{
    if(!(Test-Path -LiteralPath $store)){
        & (Join-Path $Jdk 'bin\keytool.exe') -genkeypair -keystore $store -storepass:env HARNESS_KEYSTORE_PASSWORD -keypass:env HARNESS_KEYSTORE_PASSWORD -alias harness -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Harness Community Build' -storetype JKS;Assert-Step
    }
    $apk=Join-Path $project 'DeepSeek-Harness-Share-0.4.7-alpha-arm64.apk'
    & (Join-Path $bt 'apksigner.bat') sign --ks $store --ks-key-alias harness --ks-pass env:HARNESS_KEYSTORE_PASSWORD --key-pass env:HARNESS_KEYSTORE_PASSWORD --out $apk $aligned;Assert-Step
    & (Join-Path $bt 'apksigner.bat') verify --verbose --print-certs $apk;Assert-Step
    & (Join-Path $bt 'aapt.exe') dump badging $apk|Select-String 'package:|application-label:|launchable-activity:|sdkVersion:|targetSdkVersion:'
    Get-Item $apk|Select-Object FullName,Length
}finally{Remove-Item Env:\HARNESS_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue}
