$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot 'scripts/java-environment.ps1')

if (!(Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

javac -encoding UTF-8 -d out src\main\java\piHAUPA\*.java src\test\java\piHAUPA\LargePaperScaleTest.java
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed with exit code $LASTEXITCODE" }

$inputFile = if ($args.Count -gt 0) { $args[0] } else { "examples\paper-scaled-9000.txt" }
java -cp out piHAUPA.LargePaperScaleTest $inputFile
if ($LASTEXITCODE -ne 0) { throw "Large verification failed with exit code $LASTEXITCODE" }
