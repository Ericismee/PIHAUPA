param([int]$Port = 8000, [string]$DataRoot = '', [switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & ./scripts/build.ps1
    if ($DataRoot) { $env:PIHAUPA_DATA_ROOT = $DataRoot }
    elseif (!$env:PIHAUPA_DATA_ROOT) {
        $defaultData = Join-Path $env:USERPROFILE 'Downloads/Data-20261007T013338Z-1-001/Data'
        if (Test-Path -LiteralPath $defaultData) { $env:PIHAUPA_DATA_ROOT = $defaultData }
    }
    Write-Host "PIHAUPA Research Lab: http://localhost:$Port"
    if (!$NoBrowser) { Start-Process "http://localhost:$Port" }
    & java -Xmx4g -cp target/classes vn.edu.dlu.pihaupa.web.LabServer $Port
    if ($LASTEXITCODE -ne 0) { throw 'Web server stopped with an error.' }
} finally { Pop-Location }
