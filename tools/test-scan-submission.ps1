# Contract, authenticated-client and private-storage tests. No network or real uploads.
param([string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$cache = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
$output = Join-Path $projectRoot 'app\build\scan-submission-host-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$libs = @('junit\junit\4.13.2', 'org.hamcrest\hamcrest-core\1.3',
    'androidx.annotation\annotation-jvm\1.9.1', 'com.google.code.gson\gson\2.11.0',
    'com.squareup.retrofit2\retrofit\2.11.0', 'com.squareup.okhttp3\okhttp\4.12.0',
    'com.squareup.okio\okio-jvm\3.9.0', 'org.jetbrains.kotlin\kotlin-stdlib\2.2.10') | ForEach-Object {
    $jar = Get-ChildItem -LiteralPath (Join-Path $cache $_) -Recurse -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | Select-Object -First 1
    if (!$jar) { throw "Missing dependency: $_" }
    $jar.FullName
}
$android = "$env:LOCALAPPDATA\Android\Sdk\platforms\android-36\android.jar"
$classpath = (@($android) + $libs) -join ';'
$main = Join-Path $projectRoot 'app\src\main\java\com\venussystem\venusmobile'
$sources = @('ScanSubmissionDraft','ScanPhotoQuality','ScanBackData','ScanBackIngredientCandidate',
    'ScanCosmeticClassification','ScanFrontData','ScanOcrResult','ScanOcrToken') | ForEach-Object { "$main\model\$_.java" }
$sources += @('ScanSubmissionMapper','ScanSubmissionValidator','ScanDraftStore','ScanPhotoUploadCoordinator') |
    ForEach-Object { "$main\repository\$_.java" }
$sources += @('ScanAuthSession','ScanSubmissionApi','ScanApiException','AuthenticatedScanClient','CloudinaryUploadClient','ScanUploadException') |
    ForEach-Object { "$main\repository\api\$_.java" }
$sources += @('ScanSessionRequest','ScanSessionResponse','ScanUploadSignaturesResponse','UserRequest','UserResponse','ScanUploadedPhoto') |
    ForEach-Object { "$main\repository\api\dto\$_.java" }
$testRoot = "$projectRoot\app\src\test\java\com\venussystem\venusmobile\repository"
$sources += "$testRoot\ScanSubmissionPreparationTest.java", "$testRoot\AuthenticatedScanClientTest.java",
    "$testRoot\ScanCatalogCharacterizationTest.java"
$sources += "$main\model\Produto.java", "$main\model\ScanFrontData.java", "$main\model\ScanProductMatch.java",
    "$main\view\util\ScanTextNormalizer.java"
$sources += @('ScanCatalogRules','ScanCatalogText','ScanCatalogScoring','ScanCatalogMatcher') |
    ForEach-Object { "$main\repository\$_.java" }
$sources += "$PSScriptRoot\scan-back-test-support\android\util\Log.java"
$sources += "$testRoot\CloudinaryUploadTest.java", "$testRoot\ScanIngredientCatalogTest.java",
    "$main\repository\ScanIngredientCatalog.java", "$main\repository\api\ScanIngredientApi.java"
$sources += Get-ChildItem -LiteralPath "$main\domain\scan" -Filter '*.java' |
    Select-Object -ExpandProperty FullName
& "$JavaHome\bin\javac.exe" --release 11 -encoding UTF-8 -cp $classpath -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Submission test compilation failed.' }
& "$JavaHome\bin\java.exe" -cp "$output;$classpath" org.junit.runner.JUnitCore com.venussystem.venusmobile.repository.ScanSubmissionPreparationTest com.venussystem.venusmobile.repository.AuthenticatedScanClientTest com.venussystem.venusmobile.repository.CloudinaryUploadTest com.venussystem.venusmobile.repository.ScanCatalogCharacterizationTest com.venussystem.venusmobile.repository.ScanIngredientCatalogTest
if ($LASTEXITCODE -ne 0) { throw 'Submission tests failed.' }
