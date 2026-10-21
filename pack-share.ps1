$ErrorActionPreference='Stop'
$project=$PSScriptRoot
& (Join-Path $project 'verify-apk.ps1')
$staging=Join-Path $project ('build\delivery-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
$source=Join-Path $staging 'source\dsh-share-app'
$bundle=Join-Path $staging 'bundle'
New-Item -ItemType Directory -Path $source,$bundle,(Join-Path $source 'upstream'),(Join-Path $source 'tools') -Force|Out-Null
# Never recursively package the project root: it contains signing credentials.
foreach($name in @('assets','java','ui-src','res','tests')){Copy-Item -LiteralPath (Join-Path $project $name) -Destination $source -Recurse}
foreach($name in @('README.md','build.ps1','patch-manifest.ps1','prepare-sources.cjs','check-assets.cjs','test-profile.cjs','verify-apk.ps1','pack-share.ps1')){
    Copy-Item -LiteralPath (Join-Path $project $name) -Destination $source
}
foreach($name in @('termux-arm64.apk','termux-source.zip','LICENSE-Termux.md')){Copy-Item -LiteralPath (Join-Path $project "upstream\$name") -Destination (Join-Path $source 'upstream')}
Copy-Item -LiteralPath (Join-Path $project 'tools\apktool_3.0.3.jar') -Destination (Join-Path $source 'tools')
$sourceZip=Join-Path $project 'DeepSeek-Harness-Share-0.4.7-source.zip'
Compress-Archive -Path (Join-Path $staging 'source\*') -DestinationPath $sourceZip -CompressionLevel Optimal -Force
Copy-Item -LiteralPath $sourceZip,(Join-Path $project 'DeepSeek-Harness-Share-0.4.7-alpha-arm64.apk'),(Join-Path $project 'README.md') -Destination $bundle
$guide=@'
DeepSeek Harness 0.4.7-alpha 分享试验版

适用：ARM64，Android 11 及以上；本机未安装 Termux / 旧 Harness。
请先小范围测试，尚未在全新手机上完成首次安装验证。

1. 安装同目录 APK，打开后点击“安装运行环境”，保持联网并等待下载、编译完成。
2. 自己填写 DeepSeek API Key 和模型名称，然后开始使用。
3. 其他插件自行选择安装、填写各自配置；未预装原作者的私人插件。
4. 需要操作手机时，才在 App 中启用手机控制和系统无障碍。无需 root。

没有附带原作者的密钥、聊天、附件、登录信息或签名私钥。
已有 Termux/旧 Harness 的设备请不要卸载强装，避免丢失原有数据。
本版与它们同包名但签名不同，不能直接覆盖升级或共存。

具体限制和故障处理见 README.md；对应源码和开源许可在 source.zip。
'@
[IO.File]::WriteAllText((Join-Path $bundle '先读我.txt'),$guide,[Text.UTF8Encoding]::new($true))
$hash=(Get-FileHash -LiteralPath (Join-Path $bundle 'DeepSeek-Harness-Share-0.4.7-alpha-arm64.apk') -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $bundle 'SHA256SUMS.txt'),"$hash  DeepSeek-Harness-Share-0.4.7-alpha-arm64.apk`n")
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($sourceZip)
try{
    foreach($entry in $zip.Entries){
        if($entry.FullName -match '(?i)(^|[/\\])(keystore|build|\.dsh)([/\\]|$)|\.(jks|keystore)$|(^|[/\\])(password\.txt|model-env\.sh|phone-bridge-token|bridge-token)$'){throw 'Private file found in source delivery'}
    }
}finally{$zip.Dispose()}
$output=Join-Path $project 'DeepSeek-Harness-Share-0.4.7-alpha.zip'
Compress-Archive -Path (Join-Path $bundle '*') -DestinationPath $output -CompressionLevel Optimal -Force
Get-Item -LiteralPath $output,$sourceZip|Select-Object FullName,Length
