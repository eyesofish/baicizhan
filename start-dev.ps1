param(
  [switch]$ForceImport,
  [int]$ImportLimit = 2000,
  [switch]$ResetData
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$demoDir = Join-Path $root "demo"
$frontendDir = Join-Path $root "frontend"
$runtimeDir = Join-Path $root ".runtime"
$stateFile = Join-Path $runtimeDir "dev-processes.json"

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

function Wait-ContainerHealthy {
  param(
    [string]$ContainerName,
    [int]$TimeoutSec = 180
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $status = docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $ContainerName 2>$null
    if ($LASTEXITCODE -eq 0) {
      $statusText = ($status | Select-Object -First 1).ToString().Trim()
      if ($statusText -eq "healthy" -or $statusText -eq "running") {
        return
      }
    }
    Start-Sleep -Seconds 2
  }

  throw "Container $ContainerName did not become ready in $TimeoutSec seconds."
}

function Wait-HttpReady {
  param(
    [string]$Url,
    [int]$TimeoutSec = 180
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $resp = Invoke-WebRequest -Uri $Url -Method Get -UseBasicParsing -TimeoutSec 3
      if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 600) {
        return
      }
    } catch {
      $statusCode = $null
      if ($_.Exception -and $_.Exception.Response) {
        try {
          $statusCode = [int]$_.Exception.Response.StatusCode
        } catch {
          $statusCode = $null
        }
      }
      if ($statusCode -ge 200 -and $statusCode -lt 600) {
        return
      }
    }
    Start-Sleep -Seconds 2
  }

  throw "HTTP endpoint not ready: $Url"
}

if (Test-Path $stateFile) {
  $state = Get-Content $stateFile -Raw | ConvertFrom-Json
  $existing = @()
  foreach ($processId in @($state.backendPid, $state.frontendPid)) {
    if ($processId -and (Get-Process -Id $processId -ErrorAction SilentlyContinue)) {
      $existing += $processId
    }
  }
  if ($existing.Count -gt 0) {
    throw "Detected running dev processes (PID: $($existing -join ', ')). Run .\\stop-dev.ps1 first."
  }
}

Write-Host "[1/6] Starting infrastructure (MySQL + Redis) ..."
if ($ResetData) {
  docker compose down -v --remove-orphans | Out-Null
}

# Clean up old containers created outside compose with fixed names.
foreach ($legacyName in @("baicizhan-mysql", "baicizhan-redis")) {
  $legacy = docker ps -a --format "{{.Names}}" | Where-Object { $_ -eq $legacyName }
  if ($legacy) {
    docker rm -f $legacyName | Out-Null
  }
}

docker compose up -d mysql redis | Out-Null

$mysqlContainerId = (docker compose ps -q mysql | Select-Object -First 1).ToString().Trim()
$redisContainerId = (docker compose ps -q redis | Select-Object -First 1).ToString().Trim()
if (-not $mysqlContainerId) {
  throw "Unable to find mysql container from docker compose."
}
if (-not $redisContainerId) {
  throw "Unable to find redis container from docker compose."
}

Wait-ContainerHealthy -ContainerName $mysqlContainerId -TimeoutSec 240
Wait-ContainerHealthy -ContainerName $redisContainerId -TimeoutSec 120

Write-Host "[2/6] Checking whether seed import is needed ..."
$termCount = 0
for ($attempt = 0; $attempt -lt 10; $attempt++) {
  try {
    $rawCount = docker exec $mysqlContainerId mysql -N -B -uroot -proot -D baicizhan -e "SELECT COUNT(*) FROM terms;" 2>$null
    if ($LASTEXITCODE -eq 0 -and $rawCount) {
      $termCount = [int](($rawCount | Select-Object -Last 1).ToString().Trim())
      break
    }
  } catch {
    $termCount = 0
  }
  Start-Sleep -Seconds 2
}

if ($ForceImport -or $termCount -eq 0) {
  Write-Host "[3/6] Importing vocabulary into MySQL ..."
  Push-Location $demoDir
  try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\import-high-frequency.ps1" -Profile mysql -Limit $ImportLimit
    if ($LASTEXITCODE -ne 0) {
      throw "Vocabulary import failed."
    }
  } finally {
    Pop-Location
  }
} else {
  Write-Host "[3/6] Seed data exists ($termCount terms), skipping import."
}

$backendOut = Join-Path $runtimeDir "backend-dev.out.log"
$backendErr = Join-Path $runtimeDir "backend-dev.err.log"
$frontendOut = Join-Path $runtimeDir "frontend-dev.out.log"
$frontendErr = Join-Path $runtimeDir "frontend-dev.err.log"
Remove-Item $backendOut, $backendErr, $frontendOut, $frontendErr -Force -ErrorAction SilentlyContinue

Write-Host "[4/6] Starting backend (Spring Boot, mysql profile) ..."
$mvnwPath = Join-Path $demoDir "mvnw.cmd"
$backendProc = Start-Process `
  -FilePath $mvnwPath `
  -ArgumentList @("-s", "settings.xml", "spring-boot:run", "-Dspring-boot.run.profiles=mysql") `
  -WorkingDirectory $demoDir `
  -PassThru `
  -RedirectStandardOutput $backendOut `
  -RedirectStandardError $backendErr

Wait-HttpReady -Url "http://localhost:8080/actuator/health" -TimeoutSec 240

Write-Host "[5/6] Starting frontend ..."
$viteCli = Join-Path $frontendDir "node_modules\vite\bin\vite.js"
$reactScriptsCli = Join-Path $frontendDir "node_modules\react-scripts\bin\react-scripts.js"

if (Test-Path $reactScriptsCli) {
  $oldPort = $env:PORT
  $oldHost = $env:HOST
  try {
    $env:PORT = "5173"
    $env:HOST = "0.0.0.0"
    $frontendProc = Start-Process `
      -FilePath "node.exe" `
      -ArgumentList @($reactScriptsCli, "start") `
      -WorkingDirectory $frontendDir `
      -PassThru `
      -RedirectStandardOutput $frontendOut `
      -RedirectStandardError $frontendErr
  } finally {
    if ($null -eq $oldPort) {
      Remove-Item Env:PORT -ErrorAction SilentlyContinue
    } else {
      $env:PORT = $oldPort
    }
    if ($null -eq $oldHost) {
      Remove-Item Env:HOST -ErrorAction SilentlyContinue
    } else {
      $env:HOST = $oldHost
    }
  }
} elseif (Test-Path $viteCli) {
  $frontendProc = Start-Process `
    -FilePath "node.exe" `
    -ArgumentList @($viteCli, "--host", "0.0.0.0", "--port", "5173") `
    -WorkingDirectory $frontendDir `
    -PassThru `
    -RedirectStandardOutput $frontendOut `
    -RedirectStandardError $frontendErr
} else {
  throw "Frontend dependencies not found. Run: cd frontend; npm install"
}

Wait-HttpReady -Url "http://localhost:5173" -TimeoutSec 120

if ($backendProc.HasExited) {
  throw "Backend process exited unexpectedly. See: $backendOut and $backendErr"
}
if ($frontendProc.HasExited) {
  throw "Frontend process exited unexpectedly. See: $frontendOut and $frontendErr"
}

[ordered]@{
  startedAt = (Get-Date).ToString("s")
  backendPid = $backendProc.Id
  frontendPid = $frontendProc.Id
  backendOut = $backendOut
  backendErr = $backendErr
  frontendOut = $frontendOut
  frontendErr = $frontendErr
} | ConvertTo-Json | Set-Content -Path $stateFile -Encoding UTF8

Write-Host "[6/6] Done."
Write-Host ""
Write-Host "Frontend: http://localhost:5173"
Write-Host "Backend : http://localhost:8080"
Write-Host "Swagger : http://localhost:8080/swagger-ui.html"
Write-Host ""
Write-Host "Stop all: powershell -NoProfile -ExecutionPolicy Bypass -File .\stop-dev.ps1"
