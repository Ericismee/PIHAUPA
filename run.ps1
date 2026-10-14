$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & ./scripts/build.ps1
    & java -cp target/classes piHAUPA.Main @args
    if ($LASTEXITCODE -ne 0) { throw 'CLI failed.' }
} finally { Pop-Location }
