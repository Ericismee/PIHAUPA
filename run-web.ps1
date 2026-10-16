param([int]$Port = 8000, [string]$DataRoot = '', [switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & (Join-Path $PSScriptRoot 'scripts/build.ps1')
    $classRoot = Join-Path $PSScriptRoot 'target/classes'
    $serverClass = Join-Path $classRoot 'vn/edu/dlu/pihaupa/web/LabServer.class'
    if (!(Test-Path -LiteralPath $serverClass -PathType Leaf)) {
        throw "Build chưa tạo LabServer.class tại $serverClass. Kiểm tra lỗi biên dịch phía trên."
    }
    # Dùng chính java.exe cạnh javac.exe đã chọn khi build, tránh launcher shim/JRE khác.
    $compilerPath = (Get-Command javac -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    $javaExecutable = Join-Path (Split-Path -Parent $compilerPath) 'java.exe'
    if (!(Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
        throw "Không tìm thấy java.exe trong JDK dùng để build: $compilerPath"
    }
    if ($DataRoot) { $env:PIHAUPA_DATA_ROOT = $DataRoot }
    elseif (!$env:PIHAUPA_DATA_ROOT) {
        $defaultData = Join-Path $env:USERPROFILE 'Downloads/Data-20261007T013338Z-1-001/Data'
        if (Test-Path -LiteralPath $defaultData) { $env:PIHAUPA_DATA_ROOT = $defaultData }
    }
    Write-Host "PIHAUPA Research Lab: http://localhost:$Port"
    if (!$NoBrowser) { Start-Process "http://localhost:$Port" }
    & $javaExecutable -Xmx4g -cp $classRoot vn.edu.dlu.pihaupa.web.LabServer $Port
    if ($LASTEXITCODE -ne 0) { throw 'Web server stopped with an error.' }
} finally { Pop-Location }
