<#
Health of the DurianCare discovery beacon (exit 0 = announcing). Read-only.
Checks: Scheduled Task state, beacon status file freshness, iot-service health on the announced address.
#>
[CmdletBinding()]
param([string]$TaskName = "DurianCare Discovery Beacon")

$ok = $true
$task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if (-not $task) {
  Write-Host "[check] Task '$TaskName': NOT INSTALLED (run install-beacon-task.ps1)"; $ok = $false
} else {
  $info = Get-ScheduledTaskInfo -TaskName $TaskName
  Write-Host ("[check] Task '{0}': {1} | last run {2} | last result {3}" -f $TaskName, $task.State, $info.LastRunTime, $info.LastTaskResult)
  if ($task.State -ne "Running") { $ok = $false }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$beacon = Join-Path $repoRoot "duriancare-iot-service\src\discovery\hostBeacon.js"
$node = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
if ($node) {
  & $node $beacon --health
  if ($LASTEXITCODE -ne 0) { $ok = $false }
} else {
  Write-Host "[check] node.exe not on PATH; cannot read beacon health"; $ok = $false
}

$statusFile = Join-Path $env:USERPROFILE ".duriancare\discovery-beacon-status.json"
if (Test-Path $statusFile) {
  $status = Get-Content $statusFile -Raw | ConvertFrom-Json
  if ($status.ip) {
    try {
      $health = Invoke-RestMethod -Uri ("http://{0}:{1}/actuator/health" -f $status.ip, $status.httpPort) -TimeoutSec 5
      Write-Host ("[check] iot-service at {0}:{1}: {2}" -f $status.ip, $status.httpPort, $health.status)
      if ($health.status -ne "UP") { $ok = $false }
    } catch {
      Write-Host ("[check] iot-service at {0}:{1}: UNREACHABLE ({2})" -f $status.ip, $status.httpPort, $_.Exception.Message); $ok = $false
    }
  }
}

if ($ok) { Write-Host "[check] RESULT: OK"; exit 0 } else { Write-Host "[check] RESULT: PROBLEM"; exit 1 }
