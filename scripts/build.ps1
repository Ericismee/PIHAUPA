$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-environment.ps1')
Push-Location (Join-Path $PSScriptRoot '..')
try {
    New-Item -ItemType Directory -Force target/classes | Out-Null
    $sources = @(Get-ChildItem src/main/java -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac -encoding UTF-8 --release 17 -d target/classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    Copy-Item src/main/resources/* target/classes -Recurse -Force
} finally { Pop-Location }
