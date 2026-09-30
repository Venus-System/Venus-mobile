# Compile-only smoke check against cached Android dependencies and the last app build.
# This does not build an APK or replace a Gradle/device test.
param([string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$cache = "$env:USERPROFILE\.gradle\caches"
$main = "$projectRoot\app\src\main\java\com\venussystem\venusmobile"
$output = "$projectRoot\app\build\scan-submission-android-compile"
New-Item -ItemType Directory -Force -Path $output | Out-Null
$artifacts = @('androidx.annotation\annotation-jvm\1.9.1',
    'org.jetbrains.kotlin\kotlin-stdlib\2.2.10', 'com.google.code.gson\gson\2.11.0',
    'com.squareup.retrofit2\retrofit\2.11.0', 'com.squareup.retrofit2\converter-gson\2.11.0',
    'com.squareup.okhttp3\okhttp\4.12.0', 'com.squareup.okhttp3\logging-interceptor\4.12.0',
    'com.squareup.okio\okio-jvm\3.9.0', 'androidx.lifecycle\lifecycle-common-jvm\2.9.4')
$jars = $artifacts | ForEach-Object {
    Get-ChildItem -LiteralPath "$cache\modules-2\files-2.1\$_" -Recurse -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
        Select-Object -First 1 -ExpandProperty FullName
}
$transformed = & rg --files "$cache\9.1.0\transforms" -g classes.jar
$needed = 'firebase-auth-24.2.0|firebase-auth-interop-20.0.0|firebase-common-22.2.0|play-services-tasks-18.4.0|play-services-basement-|appcompat-1.7.1|activity-1.13.0|core-1.18.0|fragment-1.8.9|lifecycle-.*release|lifecycle-livedata.*2.9.4|savedstate-release|loader-'
$jars += $transformed | Where-Object { $_ -match $needed } |
    Group-Object { Split-Path (Split-Path (Split-Path $_ -Parent) -Parent) -Leaf } |
    ForEach-Object { $_.Group[0] }
$jars += "$env:LOCALAPPDATA\Android\Sdk\platforms\android-36\android.jar"
$jars += "$projectRoot\app\build\scan-submission-host-tests"
$jars += "$projectRoot\app\build\intermediates\compile_app_classes_jar\debug\bundleDebugClassesToCompileJar\classes.jar"
$jars += "$projectRoot\app\build\intermediates\compile_and_runtime_r_class_jar\debug\processDebugResources\R.jar"
$classpath = $jars -join ';'
$sources = @("$main\repository\api\FirebaseScanAuthSession.java",
    "$main\repository\api\ScanApiException.java", "$main\repository\api\AuthenticatedScanClient.java",
    "$main\repository\api\dto\ScanUploadSignaturesResponse.java",
    "$main\repository\SessaoUsuario.java", "$main\repository\SessaoFirebase.java",
    "$main\repository\api\TokenFirebase.java", "$main\repository\api\VenusApi.java",
    "$main\repository\ProdutoRepository.java",
    "$main\repository\api\ClienteApi.java", "$main\repository\ScanSubmissionRepository.java",
    "$main\view\util\ScanPhotoQualityAnalyzer.java", "$main\model\ScanPhotoQuality.java",
    "$main\model\ScanSubmissionDraft.java",
    "$main\view\ScanFlowFinisher.java")
$sources += Get-ChildItem -LiteralPath "$main\repository\api\dto" -Filter '*.java' |
    Select-Object -ExpandProperty FullName
$sources = $sources | Select-Object -Unique
& "$JavaHome\bin\javac.exe" --release 11 -encoding UTF-8 -cp $classpath -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Android integration compilation failed.' }
Write-Output 'Auth, client, repository and finisher compiled. Full Activities/APK require Gradle.'
