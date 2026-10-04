# Offline characterization of front extraction/classification. Does not run ML Kit or the UI.
param([string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr', [switch]$PrintBaseline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$cache = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
$output = Join-Path $projectRoot 'app\build\scan-analysis-host-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$libs = @('junit\junit\4.13.2', 'org.hamcrest\hamcrest-core\1.3',
    'androidx.annotation\annotation-jvm\1.9.1', 'com.google.code.gson\gson\2.11.0',
    'org.jetbrains.kotlin\kotlin-stdlib\2.2.10') | ForEach-Object {
    $jar = Get-ChildItem -LiteralPath (Join-Path $cache $_) -Recurse -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | Select-Object -First 1
    if (!$jar) { throw "Missing cached dependency: $_" }
    $jar.FullName
}
$classpath = $libs -join ';'
$main = "$projectRoot\app\src\main\java\com\venussystem\venusmobile"
$sources = @('ScanFrontData','ScanCosmeticClassification','ScanOcrResult','ScanOcrToken') |
    ForEach-Object { "$main\model\$_.java" }
$sources += Get-ChildItem "$main\view\util" -Filter '*.java' |
    Where-Object { $_.Name -match '^Scan(Front|Cosmetic|Text)' } | Select-Object -ExpandProperty FullName
$sources += "$PSScriptRoot\scan-back-test-support\android\graphics\Rect.java",
    "$PSScriptRoot\scan-back-test-support\android\util\Log.java",
    "$projectRoot\app\src\test\java\com\venussystem\venusmobile\view\util\ScanAnalysisCharacterizationTest.java"
& "$JavaHome\bin\javac.exe" --release 11 -encoding UTF-8 -cp $classpath -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Scan analysis test compilation failed.' }
if ($PrintBaseline) {
    & "$JavaHome\bin\java.exe" -cp "$output;$classpath" com.venussystem.venusmobile.view.util.ScanAnalysisCharacterizationTest
} else {
    & "$JavaHome\bin\java.exe" -cp "$output;$classpath" org.junit.runner.JUnitCore com.venussystem.venusmobile.view.util.ScanAnalysisCharacterizationTest
}
if ($LASTEXITCODE -ne 0) { throw 'Scan analysis tests failed.' }
