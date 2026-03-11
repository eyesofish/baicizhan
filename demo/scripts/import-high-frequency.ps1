param(
  [ValidateSet("mysql", "h2")]
  [string]$Profile = "mysql",
  [string]$SourceFile = "..\datasets\high-frequency-vocabulary\30k-explained.txt",
  [int]$Limit = 10000,
  [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$dryRunValue = if ($DryRun.IsPresent) { "true" } else { "false" }
$runArgs = @(
  "--app.import.enabled=true",
  "--app.import.source-file=$SourceFile",
  "--app.import.limit=$Limit",
  "--app.import.source-language=en",
  "--app.import.target-language=zh-Hans",
  "--app.import.dry-run=$dryRunValue",
  "--app.import.exit-on-finish=true"
)

$cmd = @("-s", "settings.xml", "spring-boot:run")
if ($Profile -eq "mysql") {
  $cmd += "-Dspring-boot.run.profiles=mysql"
}
$cmd += "-Dspring-boot.run.arguments=$($runArgs -join ' ')"

Write-Host "Running importer: profile=$Profile, limit=$Limit, dryRun=$dryRunValue, source=$SourceFile"
& .\mvnw.cmd @cmd
