$ErrorActionPreference = "Stop"

if (!(Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

javac -encoding UTF-8 -d out src\piHAUPA\*.java
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed with exit code $LASTEXITCODE" }

$port = if ($args.Count -gt 0) { $args[0] } else { $null }
if ($null -eq $port) {
    foreach ($candidate in 8000..8010) {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $candidate)
        try {
            $listener.Start()
            $port = [string]$candidate
            break
        } catch [System.Net.Sockets.SocketException] {
            # Một server cũ đang dùng cổng này; thử cổng tiếp theo.
        } finally {
            $listener.Stop()
        }
    }
    if ($null -eq $port) { throw "Không có cổng trống trong khoảng 8000–8010." }
    Write-Host "PIHAUPA Web sẽ mở ở cổng $port."
}
java -cp out piHAUPA.WebServer $port
