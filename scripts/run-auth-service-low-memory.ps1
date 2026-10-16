param(
    [string]$MaxHeap = "768m"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$env:JAVA_TOOL_OPTIONS = "-Xms128m -Xmx$MaxHeap -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=128m -Xss512k -XX:ActiveProcessorCount=4"

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $maven) {
    $knownMavenPaths = @(
        "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd",
        "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd",
        "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd"
    )
    $maven = $knownMavenPaths | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
} else {
    $maven = $maven.Source
}

if (-not $maven) {
    throw "Cannot find Maven. Add mvn.cmd to PATH or install Maven through IntelliJ."
}

Write-Host "Starting duriancare-auth-service with JAVA_TOOL_OPTIONS=$env:JAVA_TOOL_OPTIONS"
Push-Location $repoRoot
try {
    & $maven -pl duriancare-auth-service spring-boot:run
} finally {
    Pop-Location
}
