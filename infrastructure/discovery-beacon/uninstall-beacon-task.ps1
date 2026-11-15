<#
Removes the DurianCare discovery beacon Scheduled Task (the status/log files in %USERPROFILE%\.duriancare are kept).
#>
[CmdletBinding()]
param([string]$TaskName = "DurianCare Discovery Beacon")
$task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if (-not $task) { Write-Host "[uninstall] Task '$TaskName' is not installed."; exit 0 }
Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
Write-Host "[uninstall] Task '$TaskName' removed."
