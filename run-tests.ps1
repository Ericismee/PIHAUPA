$ErrorActionPreference = 'Stop'
$testRunRootPrevious=$env:PIHAUPA_RUN_ROOT
$env:PIHAUPA_RUN_ROOT=Join-Path $PSScriptRoot 'target/test-runs'
Push-Location $PSScriptRoot
try {
    & ./scripts/build.ps1
    New-Item -ItemType Directory -Force target/test-classes | Out-Null
    $tests = @(Get-ChildItem src/test/java -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac -encoding UTF-8 --release 17 -cp target/classes -d target/test-classes @tests
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    foreach ($testClass in @('PiHaupaSelfTest','ExportBundleTest','ArchiveDatasetReaderTest','LargeTextDatasetTest','LabApplicationTest','RecoveryWriterTest')) {
        & java -ea -cp 'target/classes;target/test-classes' "piHAUPA.$testClass"
        if ($LASTEXITCODE -ne 0) { throw "$testClass failed." }
    }
    # Đối chiếu vét cạn cận dừng ghép, kể cả khi không có bộ lọc cặp hỗ trợ.
    & java -ea '-Dpihaupa.lookAheadJoin=true' '-Dpihaupa.pairFilter=false' -cp 'target/classes;target/test-classes' piHAUPA.PiHaupaSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Look-ahead join correctness tests failed.' }
} finally { Pop-Location; $env:PIHAUPA_RUN_ROOT=$testRunRootPrevious }
