param([string]$Repository = (Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$results = @()
foreach ($module in @('config-server','duriancare-auth-service','duriancare-gateway',
        'duriancare-notification-service','duriancare-search-service','duriancare-cultivation-service')) {
    $container = if ($module -eq 'config-server') { 'duriancare-config-server' } else { $module }
    $target = Join-Path $Repository "$module/target/acceptance-deployed.jar"
    docker cp "${container}:/app/app.jar" $target 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect $container" }
    $zip = [IO.Compression.ZipFile]::OpenRead($target)
    try {
        $matched = 0
        $different = @()
        $root = Join-Path $Repository "$module/target/classes"
        foreach ($file in Get-ChildItem -LiteralPath $root -File -Recurse) {
            $relative = [IO.Path]::GetRelativePath($root, $file.FullName).Replace('\','/')
            if ($relative -notmatch '\.(class|yml|yaml)$') { continue }
            $entry = $zip.GetEntry("BOOT-INF/classes/$relative")
            if ($null -eq $entry) { $different += $relative; continue }
            $stream = $entry.Open()
            try { $deployed = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) }
            finally { $stream.Dispose() }
            if ($deployed -eq (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash) { $matched++ }
            else { $different += $relative }
        }
        $results += [pscustomobject]@{ service = $container; identical = $matched; differing = $different }
    } finally { $zip.Dispose() }
}
$results | ConvertTo-Json -Depth 5
