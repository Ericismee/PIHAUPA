$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-environment.ps1')
Push-Location (Join-Path $PSScriptRoot '..')
try {
    New-Item -ItemType Directory -Force target/classes | Out-Null
    $sources = @(Get-ChildItem src/main/java -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac -encoding UTF-8 --release 17 -d target/classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    Copy-Item src/main/resources/* target/classes -Recurse -Force
    $buildRevision = 'unavailable'
    $buildDirty = 'unknown'
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $buildCandidate = (& git rev-parse HEAD 2>$null)
        if ($LASTEXITCODE -eq 0 -and $buildCandidate) {
            $buildRevision = $buildCandidate
            $buildDirty = if ((& git status --porcelain 2>$null)) { 'true' } else { 'false' }
        }
    }
    @("revision=$buildRevision", "dirty=$buildDirty", 'version=2.0.0') | Set-Content -Encoding ascii target/classes/build-info.properties
} finally { Pop-Location }
