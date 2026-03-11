param(
  [switch]$ImportSeed,
  [int]$Limit = 2000,
  [string]$SourceFile = "..\datasets\high-frequency-vocabulary\30k-explained.txt"
)

$ErrorActionPreference = "Stop"
$runArgs = @()
if ($ImportSeed.IsPresent) {
  $runArgs += "--app.import.enabled=true"
  $runArgs += "--app.import.source-file=$SourceFile"
  $runArgs += "--app.import.limit=$Limit"
}

$cmd = @("-s", "settings.xml", "spring-boot:run")
if ($runArgs.Count -gt 0) {
  $cmd += "-Dspring-boot.run.arguments=$($runArgs -join ' ')"
}

Write-Host "Starting backend with H2. ImportSeed=$($ImportSeed.IsPresent), Limit=$Limit"
& .\mvnw.cmd @cmd
