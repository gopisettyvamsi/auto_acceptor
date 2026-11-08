# Builds public/auto-acceptor.apk without Gradle. Run: powershell -File build.ps1
$ErrorActionPreference = "Stop"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$bt = "$sdk\build-tools\35.0.0"
$jar = "$sdk\platforms\android-36\android.jar"
Remove-Item build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory build\classes | Out-Null
& "$bt\aapt2.exe" compile --dir app\res -o build\res.zip
New-Item -ItemType Directory build\gen | Out-Null
& "$bt\aapt2.exe" link --java build\gen -A app\assets -o build\base.apk -I $jar --manifest app\AndroidManifest.xml --min-sdk-version 24 --target-sdk-version 34 build\res.zip
javac --release 8 -Xlint:-options -encoding UTF-8 -cp $jar -d build\classes (Get-ChildItem app\src, build\gen -Recurse -Filter *.java).FullName
if ($LASTEXITCODE) { throw "javac failed" }
Remove-Item public\auto-acceptor.apk -ErrorAction SilentlyContinue
& "$bt\d8.bat" --lib $jar --output build (Get-ChildItem build\classes -Recurse -Filter *.class).FullName
Push-Location build; jar uf base.apk classes.dex; Pop-Location
& "$bt\zipalign.exe" -f 4 build\base.apk build\aligned.apk
if (-not (Test-Path debug.keystore)) {
  keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias k -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=AutoAcceptor"
}
& "$bt\apksigner.bat" sign --ks debug.keystore --ks-pass pass:android --out public\auto-acceptor.apk build\aligned.apk
Write-Host "OK public\auto-acceptor.apk"
Remove-Item public\*.idsig -ErrorAction SilentlyContinue
