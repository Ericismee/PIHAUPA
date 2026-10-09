$ErrorActionPreference = "Stop"

if (!(Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

javac -encoding UTF-8 -d out src\piHAUPA\*.java test\piHAUPA\*.java
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed with exit code $LASTEXITCODE" }
java -ea -cp out piHAUPA.PiHaupaSelfTest
if ($LASTEXITCODE -ne 0) { throw "PIHAUPA tests failed with exit code $LASTEXITCODE" }
java -ea -cp out piHAUPA.ExportBundleTest
if ($LASTEXITCODE -ne 0) { throw "Export tests failed with exit code $LASTEXITCODE" }
java -ea -cp out piHAUPA.ArchiveDatasetReaderTest
if ($LASTEXITCODE -ne 0) { throw "Archive import tests failed with exit code $LASTEXITCODE" }
java -ea -cp out piHAUPA.LargeTextDatasetTest
if ($LASTEXITCODE -ne 0) { throw "Large TXT tests failed with exit code $LASTEXITCODE" }
