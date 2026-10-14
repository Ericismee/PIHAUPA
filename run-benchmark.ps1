$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot 'scripts/java-environment.ps1')

$rows = if ($args.Count -gt 0) { [int]$args[0] } else { 100000 }
$items = if ($args.Count -gt 1) { [int]$args[1] } else { 30 }
$width = if ($args.Count -gt 2) { [int]$args[2] } else { 4 }
$batches = if ($args.Count -gt 3) { [int]$args[3] } else { 10 }
$file = "examples\synthetic-$rows.txt"

if (!(Test-Path $file)) {
    .\generate-synthetic.ps1 $rows $items $width $batches $file
}

if (!(Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

javac -encoding UTF-8 -d out src\main\java\piHAUPA\*.java
java -Xmx2g -cp out piHAUPA.Main --summary $file
