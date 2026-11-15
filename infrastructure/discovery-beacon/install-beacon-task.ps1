<#
DurianCare discovery beacon - start automatically at every Windows logon.

Why a host process and not a Docker service: on Docker Desktop for Windows every container runs inside
a Linux VM behind NAT. A container (bridge or "host" network) cannot see the laptop's Wi-Fi address
and cannot broadcast onto the Wi-Fi LAN, so ESP32 boards would never hear it. The beacon therefore
runs on Windows itself; it announces only while http://<LAN-IP>:3001 answers (i.e. while Docker's
iot-service is up), and re-reads the network interfaces every 3 s (new DHCP/hotspot address = new
announcement, no IP is configured anywhere).

Creates a per-user Scheduled Task (no administrator rights, no firewall change):
  - trigger: at logon of the current user (+30 s, so Wi-Fi/Docker get a head start)
  - action : node <repo>\duriancare-iot-service\src\discovery\hostBeacon.js (hidden window)
  - restart: every 1 min, up to 999 times, if the process exits
  - no time limit, runs on battery

Usage (PowerShell, from anywhere):
  powershell -ExecutionPolicy Bypass -File install-beacon-task.ps1          # install / update + start now
  powershell -ExecutionPolicy Bypass -File check-beacon.ps1                  # health
  powershell -ExecutionPolicy Bypass -File uninstall-beacon-task.ps1        # remove
#>
[CmdletBinding()]
param(
  [string]$TaskName = "DurianCare Discovery Beacon",
  [string]$NodePath = ""
)
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$beacon = Join-Path $repoRoot "duriancare-iot-service\src\discovery\hostBeacon.js"
if (-not (Test-Path $beacon)) { throw "Beacon script not found: $beacon" }

if (-not $NodePath) {
  $cmd = Get-Command node.exe -ErrorAction SilentlyContinue
  if (-not $cmd) { throw "node.exe not found on PATH; pass -NodePath 'C:\Program Files\nodejs\node.exe'" }
  $NodePath = $cmd.Source
}
if (-not (Test-Path $NodePath)) { throw "Node not found: $NodePath" }

$statusDir = Join-Path $env:USERPROFILE ".duriancare"
New-Item -ItemType Directory -Force -Path $statusDir | Out-Null
$statusFile = Join-Path $statusDir "discovery-beacon-status.json"
$logFile = Join-Path $statusDir "discovery-beacon.log"

# A launcher file (not an inline "cmd /c" with nested quotes, which Task Scheduler mangles) appends
# the beacon's own log lines; the beacon logs no secrets.
$launcher = Join-Path $statusDir "start-discovery-beacon.cmd"
@(
  "@echo off",
  "echo [%DATE% %TIME%] starting discovery beacon >> `"$logFile`"",
  "`"$NodePath`" `"$beacon`" >> `"$logFile`" 2>&1",
  "echo [%DATE% %TIME%] beacon exited with %ERRORLEVEL% >> `"$logFile`"",
  "exit /b %ERRORLEVEL%"
) | Set-Content -Path $launcher -Encoding ASCII
$action = New-ScheduledTaskAction -Execute $launcher -WorkingDirectory (Split-Path $beacon)
$trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$trigger.Delay = "PT30S"
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
  -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
  -MultipleInstances IgnoreNew -StartWhenAvailable -Hidden
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings `
  -Principal $principal -Description "Announces the DurianCare IoT server on UDP 4210 so ESP32 boards find it (no fixed IP)." `
  -Force | Out-Null

# Stop a running instance first so an update takes effect, then start now.
Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1
Start-ScheduledTask -TaskName $TaskName
Write-Host "[install] Task '$TaskName' registered and started."
Write-Host "[install] Status file: $statusFile"
Write-Host "[install] Log file   : $logFile"
Write-Host "[install] Check with : powershell -ExecutionPolicy Bypass -File `"$(Join-Path $PSScriptRoot 'check-beacon.ps1')`""
