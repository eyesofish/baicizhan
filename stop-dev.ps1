param(
  [switch]$RemoveData
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$runtimeDir = Join-Path $root ".runtime"
$stateFile = Join-Path $runtimeDir "dev-processes.json"

if (Test-Path $stateFile) {
  $state = Get-Content $stateFile -Raw | ConvertFrom-Json
  foreach ($processId in @($state.backendPid, $state.frontendPid)) {
    if ($processId) {
      $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
      if ($proc) {
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
      }
    }
  }
  Remove-Item $stateFile -Force -ErrorAction SilentlyContinue
}

if ($RemoveData) {
  docker compose down -v --remove-orphans | Out-Null
  Write-Host "Stopped backend/frontend and removed MySQL/Redis containers + volumes."
} else {
  docker compose stop mysql redis | Out-Null
  Write-Host "Stopped backend/frontend and stopped MySQL/Redis containers (data kept)."
}
