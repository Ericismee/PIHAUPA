$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & ./scripts/build.ps1
    New-Item -ItemType Directory -Force target/test-classes | Out-Null
    $tests = @(Get-ChildItem src/test/java -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac -encoding UTF-8 --release 17 -cp target/classes -d target/test-classes @tests
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    foreach ($testClass in @('PiHaupaSelfTest','ExportBundleTest','ArchiveDatasetReaderTest','LargeTextDatasetTest','LabApplicationTest')) {
        & java -ea -cp 'target/classes;target/test-classes' "piHAUPA.$testClass"
        if ($LASTEXITCODE -ne 0) { throw "$testClass failed." }
    }
} finally { Pop-Location }
