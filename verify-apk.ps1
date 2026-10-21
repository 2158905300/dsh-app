$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project=$PSScriptRoot
$base=[IO.Compression.ZipFile]::OpenRead((Join-Path $project 'upstream\termux-arm64.apk'))
$merged=[IO.Compression.ZipFile]::OpenRead((Join-Path $project 'DeepSeek-Harness-Share-0.4.7-alpha-arm64.apk'))
function Digest($entry){$s=$entry.Open();$h=[Security.Cryptography.SHA256]::Create();try{return [BitConverter]::ToString($h.ComputeHash($s))}finally{$s.Dispose();$h.Dispose()}}
try{
    $count=0
    foreach($e in $base.Entries){
        if($e.FullName -match '^(classes\d*\.dex|lib/.+|assets/.+)$' -and !$e.FullName.EndsWith('/')){
            $copy=$merged.GetEntry($e.FullName)
            if(!$copy -or (Digest $e)-ne(Digest $copy)){throw "Upstream payload changed: $($e.FullName)"}
            $count++
        }
    }
    $names=@($merged.Entries|Where-Object {$_.FullName.StartsWith('assets/harness/') -and !$_.FullName.EndsWith('/')}|ForEach-Object {$_.FullName.Substring(15)})
    $manifest=Get-Content -LiteralPath (Join-Path $project 'assets\harness\files.json') -Raw|ConvertFrom-Json
    $expected=@($manifest.PSObject.Properties.Name)+@('files.json')
    if(!$names.Count){throw 'First-run assets/harness directory missing'}
    if(@(Compare-Object ($names|Sort-Object) ($expected|Sort-Object)).Count){throw 'Unexpected private or missing setup assets in APK'}
    foreach($name in $names){
        $stream=$merged.GetEntry('assets/harness/'+$name).Open();$hash=[Security.Cryptography.SHA256]::Create()
        try{$got=([BitConverter]::ToString($hash.ComputeHash($stream))).Replace('-','').ToLowerInvariant()}finally{$stream.Dispose();$hash.Dispose()}
        if($name -ne 'files.json' -and $got -ne $manifest.$name){throw "Setup asset digest mismatch: $name"}
    }
    if(!$merged.GetEntry('classes30.dex')){throw 'Harness code missing'}
    [xml]$xml=Get-Content -LiteralPath (Join-Path $project 'build\decoded\AndroidManifest.xml') -Raw
    $ns='http://schemas.android.com/apk/res/android'
    if($xml.DocumentElement.HasAttribute('sharedUserId',$ns)){throw 'Legacy shared UID still present'}
    foreach($name in @('dev.localdsh.app.SetupActivity','dev.localdsh.app.MainActivity','dev.localdsh.app.GuideActivity','dev.localdsh.app.DiagnosticsActivity','dev.localdsh.app.InstallService','dev.localdsh.app.LocalBridgeService')){
        $node=@($xml.manifest.application.ChildNodes|Where-Object {$_.GetAttribute('name',$ns)-eq$name})[0]
        if(!$node -or $node.GetAttribute('exported',$ns)-ne'false'){throw "Internal component exported: $name"}
    }
    Write-Output "APK verified: $count unchanged upstream payloads, $($names.Count) allowlisted install assets, private setup/control components."
}finally{$base.Dispose();$merged.Dispose()}
