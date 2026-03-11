param(
  [int]$ImportLimit = 500,
  [string]$SourceFile = "..\datasets\high-frequency-vocabulary\30k-explained.txt"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

New-Item -ItemType Directory -Force -Path .runtime | Out-Null
$stdout = Join-Path $root ".runtime\h2-smoke-stdout.log"
$stderr = Join-Path $root ".runtime\h2-smoke-stderr.log"
if (Test-Path $stdout) { Remove-Item $stdout -Force -ErrorAction SilentlyContinue }
if (Test-Path $stderr) { Remove-Item $stderr -Force -ErrorAction SilentlyContinue }
$beforePids = @(
  Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique
)

$runArgs = @(
  "--app.import.enabled=true",
  "--app.import.source-file=$SourceFile",
  "--app.import.limit=$ImportLimit"
)
$runArgsJoined = $runArgs -join " "

$args = @(
  "-s",
  "settings.xml",
  "spring-boot:run",
  "-Dspring-boot.run.arguments=""$runArgsJoined"""
)

$proc = Start-Process `
  -FilePath ".\mvnw.cmd" `
  -ArgumentList $args `
  -WorkingDirectory $root `
  -PassThru `
  -RedirectStandardOutput $stdout `
  -RedirectStandardError $stderr

try {
  $healthStatus = "UNKNOWN"
  $ready = $false
  for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 2
    try {
      $resp = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -Method Get -UseBasicParsing -TimeoutSec 3
      if ($resp.StatusCode -eq 200 -or $resp.StatusCode -eq 503) {
        $healthStatus = "HTTP_$($resp.StatusCode)"
        $ready = $true
        break
      }
    } catch {
      $statusCode = $null
      if ($_.Exception -and $_.Exception.Response) {
        try { $statusCode = [int]$_.Exception.Response.StatusCode } catch {}
      }
      if ($statusCode -eq 503) {
        $healthStatus = "HTTP_503"
        $ready = $true
        break
      }
    }
  }
  if (-not $ready) {
    throw "Backend did not become healthy in time. See $stdout and $stderr"
  }

  $email = "smoke_$(Get-Date -Format yyyyMMdd_HHmmss)@example.com"
  $register = Invoke-RestMethod `
    -Uri "http://localhost:8080/v1/auth/register" `
    -Method Post `
    -ContentType "application/json" `
    -Body (@{
      email = $email
      password = "Passw0rd!"
      displayName = "Smoke User"
    } | ConvertTo-Json)

  $token = $register.data.accessToken
  if (-not $token) {
    throw "Register did not return access token"
  }
  $auth = @{ Authorization = "Bearer $token" }

  $createdList = Invoke-RestMethod `
    -Uri "http://localhost:8080/v1/lists" `
    -Method Post `
    -Headers $auth `
    -ContentType "application/json" `
    -Body (@{
      name = "Smoke List"
      sourceLanguage = "en"
      targetLanguage = "zh-Hans"
      isPublic = $false
    } | ConvertTo-Json)
  $listId = $createdList.data.id

  $added = Invoke-RestMethod `
    -Uri "http://localhost:8080/v1/lists/$listId/items" `
    -Method Post `
    -Headers $auth `
    -ContentType "application/json" `
    -Body (@{
      text = "resilient"
      partOfSpeech = "adj"
      definition = "able to recover quickly from difficulties"
      translation = "able to bounce back"
      example = "Children are often surprisingly resilient."
      ipa = "/ri-zil-yent/"
    } | ConvertTo-Json)
  $termId = $added.data.termId

  $term = Invoke-RestMethod -Uri "http://localhost:8080/v1/terms/$termId" -Method Get -Headers $auth

  $reviewQueue = Invoke-RestMethod -Uri "http://localhost:8080/v1/review/next?limit=5" -Method Get -Headers $auth
  $reviewCount = @($reviewQueue.data).Count
  $reviewSubmitted = $false
  if ($reviewCount -gt 0) {
    $reviewTermId = $reviewQueue.data[0].termId
    $null = Invoke-RestMethod `
      -Uri "http://localhost:8080/v1/review/$reviewTermId/result" `
      -Method Post `
      -Headers $auth `
      -ContentType "application/json" `
      -Body (@{ rating = 4; elapsedMs = 1200 } | ConvertTo-Json)
    $reviewSubmitted = $true
  }

  $aiCreate = Invoke-WebRequest `
    -Uri "http://localhost:8080/v1/ai/enrich" `
    -Method Post `
    -UseBasicParsing `
    -Headers $auth `
    -ContentType "application/json" `
    -Body (@{ termId = $termId; targetLang = "zh-Hans" } | ConvertTo-Json)
  $aiCreateJson = $aiCreate.Content | ConvertFrom-Json
  $jobId = $aiCreateJson.data.jobId
  $aiJob = Invoke-RestMethod -Uri "http://localhost:8080/v1/ai/jobs/$jobId" -Method Get -Headers $auth

  $preflight = Invoke-WebRequest `
    -Uri "http://localhost:8080/v1/lists" `
    -Method Options `
    -UseBasicParsing `
    -Headers @{
      Origin = "http://localhost:5173"
      "Access-Control-Request-Method" = "POST"
      "Access-Control-Request-Headers" = "authorization,content-type"
    }

  [ordered]@{
    health = $healthStatus
    registerCode = $register.code
    listId = $listId
    termId = $termId
    termText = $term.data.text
    reviewQueueCount = $reviewCount
    reviewSubmitted = $reviewSubmitted
    aiCreateHttpStatus = $aiCreate.StatusCode
    aiJobId = $jobId
    aiJobStatus = $aiJob.data.status
    preflightStatus = $preflight.StatusCode
    preflightAllowOrigin = $preflight.Headers["Access-Control-Allow-Origin"]
  } | ConvertTo-Json -Depth 5
}
finally {
  $afterPids = @(
    Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
      Select-Object -ExpandProperty OwningProcess -Unique
  )
  foreach ($processId in $afterPids) {
    if ($beforePids -notcontains $processId) {
      Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
  }
}
