# Avoid the Oracle javapath launcher shim; prefer JAVA_HOME or an installed JDK.
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
} elseif ((Get-Command javac -ErrorAction SilentlyContinue).Source -like '*javapath*') {
    $jdk = Get-ChildItem (Join-Path $env:ProgramFiles 'Java') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($jdk) { $env:PATH = (Join-Path $jdk.FullName 'bin') + ';' + $env:PATH }
}
if (!(Get-Command javac -ErrorAction SilentlyContinue)) { throw 'Install JDK 17+ and set JAVA_HOME.' }
