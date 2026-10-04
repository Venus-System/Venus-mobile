# Parser-only regression tests. Uses isolated Rect/Log doubles, NOT an Android build.
param(
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$GradleCache = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1",
    [switch]$TraceParser
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$outputDir = Join-Path $projectRoot 'app\build\scan-back-host-tests'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$libraries = @(
    'junit\junit\4.13.2',
    'org.hamcrest\hamcrest-core\1.3',
    'androidx.annotation\annotation-jvm\1.9.1'
) | ForEach-Object {
    $jar = Get-ChildItem -LiteralPath (Join-Path $GradleCache $_) -Recurse -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
        Select-Object -First 1
    if (!$jar) { throw "Missing cached test dependency: $_" }
    $jar.FullName
}
$classpath = $libraries -join ';'
$main = Join-Path $projectRoot 'app\src\main\java\com\venussystem\venusmobile'
$sources = @(
    "$main\model\ScanBackData.java",
    "$main\model\ScanBackIngredientCandidate.java",
    "$main\model\ScanOcrResult.java",
    "$main\model\ScanPhotoQuality.java",
    "$main\model\ScanOcrToken.java",
    "$PSScriptRoot\scan-back-test-support\android\graphics\Rect.java",
    "$PSScriptRoot\scan-back-test-support\android\util\Log.java",
    "$projectRoot\app\src\androidTest\java\com\venussystem\venusmobile\view\util\ScanBackExtractorInstrumentedTest.java",
    "$projectRoot\app\src\androidTest\java\com\venussystem\venusmobile\view\util\ScanIngredientNormalizerTest.java"
)
$sources += Get-ChildItem -LiteralPath "$main\view\util" -Filter 'ScanBack*.java' |
    Select-Object -ExpandProperty FullName
$sources += "$main\view\util\ScanIngredientNormalizer.java"
$sources += "$main\repository\ScanSubmissionValidator.java",
    "$main\repository\api\dto\ScanSessionRequest.java"
$sources += Get-ChildItem -LiteralPath "$main\domain\scan" -Filter '*.java' |
    Select-Object -ExpandProperty FullName
& "$JavaHome\bin\javac.exe" --release 11 -encoding UTF-8 -cp $classpath -d $outputDir @sources
if ($LASTEXITCODE -ne 0) { throw 'Parser test compilation failed.' }
& "$JavaHome\bin\java.exe" "-Dscan.trace=$($TraceParser.IsPresent.ToString().ToLowerInvariant())" -cp "$outputDir;$classpath" org.junit.runner.JUnitCore com.venussystem.venusmobile.view.util.ScanBackExtractorInstrumentedTest com.venussystem.venusmobile.view.util.ScanIngredientNormalizerTest
if ($LASTEXITCODE -ne 0) { throw 'Parser regression tests failed.' }
