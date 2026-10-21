param([Parameter(Mandatory=$true)][string]$Decoded)
$ErrorActionPreference='Stop'
$project=$PSScriptRoot
$androidNs='http://schemas.android.com/apk/res/android'
[xml]$manifest=Get-Content -LiteralPath (Join-Path $Decoded 'AndroidManifest.xml') -Raw
$root=$manifest.DocumentElement
$root.RemoveAttribute('sharedUserId',$androidNs)
$root.RemoveAttribute('sharedUserLabel',$androidNs)
function Attr($node,$name,$value){$null=$node.SetAttribute($name,$androidNs,$value)}
$app=$root.SelectSingleNode('application')
Attr $app 'label' 'DeepSeek Harness'
Attr $app 'icon' '@drawable/dsh_whale'
Attr $app 'roundIcon' '@drawable/dsh_whale'
Attr $app 'usesCleartextTraffic' 'true'
Attr $app 'allowBackup' 'false'
foreach($filter in @($app.SelectNodes('.//intent-filter'))){
    if(@($filter.SelectNodes('category')|Where-Object {$_.GetAttribute('name',$androidNs)-in @('android.intent.category.LAUNCHER','android.intent.category.LEANBACK_LAUNCHER')}).Count -gt 0){$null=$filter.ParentNode.RemoveChild($filter)}
}
function AddComponent($kind,$name){$n=$manifest.CreateElement($kind);Attr $n 'name' $name;$null=$app.AppendChild($n);return $n}
$main=AddComponent 'activity' 'dev.localdsh.app.LauncherActivity'
Attr $main 'exported' 'true';Attr $main 'theme' '@android:style/Theme.Material.Light.NoActionBar';Attr $main 'windowSoftInputMode' 'adjustResize';Attr $main 'configChanges' 'orientation|screenSize|keyboardHidden'
$filter=$manifest.CreateElement('intent-filter');$null=$main.AppendChild($filter)
$action=$manifest.CreateElement('action');Attr $action 'name' 'android.intent.action.MAIN';$null=$filter.AppendChild($action)
$category=$manifest.CreateElement('category');Attr $category 'name' 'android.intent.category.LAUNCHER';$null=$filter.AppendChild($category)
$chat=AddComponent 'activity' 'dev.localdsh.app.MainActivity';Attr $chat 'exported' 'false';Attr $chat 'theme' '@android:style/Theme.Material.Light.NoActionBar';Attr $chat 'windowSoftInputMode' 'adjustResize';Attr $chat 'configChanges' 'orientation|screenSize|keyboardHidden'
$setup=AddComponent 'activity' 'dev.localdsh.app.SetupActivity';Attr $setup 'exported' 'false';Attr $setup 'theme' '@android:style/Theme.Material.Light.NoActionBar'
foreach($name in @('dev.localdsh.app.GuideActivity','dev.localdsh.app.DiagnosticsActivity')){$page=AddComponent 'activity' $name;Attr $page 'exported' 'false';Attr $page 'theme' '@android:style/Theme.Material.Light.NoActionBar'}
$installer=AddComponent 'service' 'dev.localdsh.app.InstallService';Attr $installer 'exported' 'false';Attr $installer 'foregroundServiceType' 'dataSync'
$browser=AddComponent 'activity' 'dev.localdsh.app.BrowserActivity';Attr $browser 'exported' 'false';Attr $browser 'launchMode' 'singleTask';Attr $browser 'theme' '@android:style/Theme.Material.Light.NoActionBar'
$bridge=AddComponent 'service' 'dev.localdsh.app.LocalBridgeService';Attr $bridge 'exported' 'false';Attr $bridge 'foregroundServiceType' 'specialUse'
$property=$manifest.CreateElement('property');Attr $property 'name' 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE';Attr $property 'value' 'User enabled single-app local DSH runtime and authenticated loopback tools';$null=$bridge.AppendChild($property)
$access=AddComponent 'service' 'dev.localdsh.app.PhoneAccessibilityService';Attr $access 'exported' 'true';Attr $access 'label' 'DeepSeek Harness 手机控制';Attr $access 'permission' 'android.permission.BIND_ACCESSIBILITY_SERVICE'
$accessFilter=$manifest.CreateElement('intent-filter');$null=$access.AppendChild($accessFilter);$accessAction=$manifest.CreateElement('action');Attr $accessAction 'name' 'android.accessibilityservice.AccessibilityService';$null=$accessFilter.AppendChild($accessAction)
$metadata=$manifest.CreateElement('meta-data');Attr $metadata 'name' 'android.accessibilityservice';Attr $metadata 'resource' '@xml/dsh_accessibility_service';$null=$access.AppendChild($metadata)
foreach($permission in @('android.permission.FOREGROUND_SERVICE_SPECIAL_USE','android.permission.FOREGROUND_SERVICE_DATA_SYNC','android.permission.POST_NOTIFICATIONS')){
    $node=$manifest.CreateElement('uses-permission');Attr $node 'name' $permission;$null=$root.InsertBefore($node,$app)
}
$settings=[Xml.XmlWriterSettings]::new();$settings.Encoding=[Text.UTF8Encoding]::new($false);$settings.Indent=$true
$writer=[Xml.XmlWriter]::Create((Join-Path $Decoded 'AndroidManifest.xml'),$settings);$manifest.Save($writer);$writer.Close()
$res=Join-Path $Decoded 'res';New-Item -ItemType Directory -Path (Join-Path $res 'drawable'),(Join-Path $res 'xml'),(Join-Path $res 'values') -Force|Out-Null
Copy-Item -LiteralPath (Join-Path $project 'res\drawable\ic_launcher.png') -Destination (Join-Path $res 'drawable\dsh_whale.png')
$accessXml=[IO.File]::ReadAllText((Join-Path $project 'res\xml\accessibility_service.xml')).Replace('@string/accessibility_description','@string/dsh_accessibility_description')
[IO.File]::WriteAllText((Join-Path $res 'xml\dsh_accessibility_service.xml'),$accessXml,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $res 'values\dsh_strings.xml'),'<resources><string name="dsh_accessibility_description">按你的指令读取界面、点击和输入，可在 DSH 中关闭。</string></resources>',[Text.UTF8Encoding]::new($false))
$yaml=Join-Path $Decoded 'apktool.yml';$text=[IO.File]::ReadAllText($yaml)
$text=$text -replace '(?m)^(\s*versionCode:)\s*.*$','$1 2007' -replace '(?m)^(\s*versionName:)\s*.*$','$1 0.118.3-harness.0.4.7-alpha' -replace '(?m)^(\s*minSdkVersion:)\s*.*$','$1 30'
[IO.File]::WriteAllText($yaml,$text,[Text.UTF8Encoding]::new($false))
