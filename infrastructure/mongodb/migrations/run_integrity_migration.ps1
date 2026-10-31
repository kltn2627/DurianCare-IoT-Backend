param(
    [ValidateSet('DRY_RUN', 'APPLY')]
    [string]$Mode = 'DRY_RUN',
    [string]$BackupRoot = (Join-Path $env:USERPROFILE '.codex\backups')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..')).Path
$settings = @{}
Get-Content -LiteralPath (Join-Path $repoRoot '.env') | ForEach-Object {
    if ($_ -match '^(MONGO_INITDB_ROOT_USERNAME|MONGO_INITDB_ROOT_PASSWORD)=(.*)$') {
        $settings[$matches[1]] = $matches[2].Trim('"')
    }
}
if (-not $settings['MONGO_INITDB_ROOT_USERNAME'] -or -not $settings['MONGO_INITDB_ROOT_PASSWORD']) {
    throw 'MongoDB credentials are unavailable in the repository .env file.'
}

function Assert-Succeeded([string]$Step) {
    if ($LASTEXITCODE -ne 0) { throw "$Step failed with exit code $LASTEXITCODE" }
}

$container = 'duriancare-mongodb'
docker cp (Join-Path $PSScriptRoot 'repair_integrity.js') "${container}:/tmp/durian_repair_integrity.js"
Assert-Succeeded 'Copy migration script'

$username = $settings['MONGO_INITDB_ROOT_USERNAME']
$password = $settings['MONGO_INITDB_ROOT_PASSWORD']
if ($Mode -eq 'APPLY') {
    $backupDirectory = Join-Path $BackupRoot ('DurianCare-data-integrity-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    foreach ($database in @('duriancare_farm', 'duriancare_cultivation')) {
        $archivePath = "/tmp/${database}_integrity.archive"
        docker exec $container mongodump --quiet --username $username --password $password `
            --authenticationDatabase admin --db $database --archive=$archivePath
        Assert-Succeeded "Backup $database"
        $destination = Join-Path $backupDirectory "$database.archive"
        docker cp "${container}:$archivePath" $destination
        Assert-Succeeded "Copy backup $database"
        if ((Get-Item -LiteralPath $destination).Length -le 0) { throw "Backup $database is empty" }
    }
    Get-ChildItem -LiteralPath $backupDirectory -File | Get-FileHash -Algorithm SHA256 |
        Select-Object Path, Hash | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $backupDirectory 'sha256.json') -Encoding utf8
    Write-Output "Verified BSON backup: $backupDirectory"
    docker exec -e MIGRATION_MODE=APPLY -e BACKUP_CONFIRMED=true $container mongosh --quiet `
        --username $username --password $password --authenticationDatabase admin `
        /tmp/durian_repair_integrity.js
    Assert-Succeeded 'Apply migration'
} else {
    docker exec -e MIGRATION_MODE=DRY_RUN $container mongosh --quiet `
        --username $username --password $password --authenticationDatabase admin `
        /tmp/durian_repair_integrity.js
    Assert-Succeeded 'Dry-run migration'
}
