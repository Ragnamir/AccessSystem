Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Resolve project root (this script is in scripts/)
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# Local Postgres settings (see GeneralDocs/TestEnvironment/TestEnvironmentVariables)
$env:DB_URL = 'jdbc:postgresql://localhost:5432/postgres'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'postgres'

# Ensure target log directory exists
$logsDir = Join-Path $ProjectRoot 'target'
if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }

# If port already used, don't start another instance
$portBusy = $false
try { $portBusy = Test-NetConnection -ComputerName 'localhost' -Port 8080 -InformationLevel Quiet } catch { $portBusy = $false }

$webUiPortBusy = $false
try { $webUiPortBusy = Test-NetConnection -ComputerName 'localhost' -Port 8081 -InformationLevel Quiet } catch { $webUiPortBusy = $false }

$accessProc = $null
if (-not $portBusy) {
  Write-Host 'Starting Access System (port 8080)…'
  $accessOut = Join-Path $logsDir 'access-system.out.log'
  $accessErr = Join-Path $logsDir 'access-system.err.log'
  $accessArgs = '-q spring-boot:run'
  $accessProc = Start-Process -FilePath 'mvn' -ArgumentList $accessArgs -WorkingDirectory $ProjectRoot -NoNewWindow -RedirectStandardOutput $accessOut -RedirectStandardError $accessErr -PassThru
} else {
  Write-Host 'Port 8080 is busy; assuming Access System is already running.'
}

# Wait for health endpoint to be UP
$healthUrl = 'http://localhost:8080/actuator/health'
$maxAttempts = 60
$attempt = 0
$resp = $null
while ($attempt -lt $maxAttempts) {
  Start-Sleep -Seconds 1
  try {
    $resp = Invoke-RestMethod -Method GET -Uri $healthUrl -TimeoutSec 2
    if ($resp.status -and $resp.status -eq 'UP') { break }
  } catch { }
  $attempt++
  if ($accessProc -and $accessProc.HasExited) { break }
}
if ($attempt -ge $maxAttempts -and -not ($resp -and $resp.status -eq 'UP')) {
  Write-Warning 'Access System did not become healthy in time. Check logs in target/*.log'
}

# Start Web UI (port 8081)
$webUiProc = $null
if (-not $webUiPortBusy) {
  Write-Host 'Starting Web UI (port 8081)…'
  $webUiOut = Join-Path $logsDir 'web-ui.out.log'
  $webUiErr = Join-Path $logsDir 'web-ui.err.log'
  $webUiArgs = '-q spring-boot:run'
  $webUiProc = Start-Process -FilePath 'mvn' -ArgumentList $webUiArgs -WorkingDirectory (Join-Path $ProjectRoot 'web-ui') -NoNewWindow -RedirectStandardOutput $webUiOut -RedirectStandardError $webUiErr -PassThru
} else {
  Write-Host 'Port 8081 is busy; assuming Web UI is already running.'
}

# Wait for Web UI to be ready (simple HTTP check)
$webUiUrl = 'http://localhost:8081/'
$webUiMaxAttempts = 30
$webUiAttempt = 0
$webUiReady = $false
while ($webUiAttempt -lt $webUiMaxAttempts -and -not $webUiReady) {
  Start-Sleep -Seconds 1
  try {
    $webUiResp = Invoke-WebRequest -Method GET -Uri $webUiUrl -TimeoutSec 2 -UseBasicParsing
    if ($webUiResp.StatusCode -eq 200) { $webUiReady = $true; break }
  } catch { }
  $webUiAttempt++
  if ($webUiProc -and $webUiProc.HasExited) { break }
}
if (-not $webUiReady) {
  Write-Warning 'Web UI did not become ready in time. Check logs in target/web-ui.*.log'
}

Write-Host 'Starting Event Generator…'
$genOut = Join-Path $logsDir 'event-generator.out.log'
$genErr = Join-Path $logsDir 'event-generator.err.log'

# Build and run the generator JAR to avoid Maven CLI parsing issues
& mvn -q -f (Join-Path $ProjectRoot 'event-generator/pom.xml') -DskipTests package | Out-Null

$jarPath = Join-Path $ProjectRoot 'event-generator/target/event-generator-0.0.1-SNAPSHOT.jar'
$genCliArgs = @(
  "--generator.db-url=$($env:DB_URL)",
  "--generator.db-user=$($env:DB_USERNAME)",
  "--generator.db-password=$($env:DB_PASSWORD)",
  '--generator.ingest-url=http://localhost:8080/ingest/event',
  '--generator.rate-per-second=2'
)

$allArgs = @('-jar', $jarPath) + $genCliArgs
$generatorProc = Start-Process -FilePath 'java' -ArgumentList $allArgs -WorkingDirectory $ProjectRoot -NoNewWindow -RedirectStandardOutput $genOut -RedirectStandardError $genErr -PassThru

Write-Host 'All processes started.'
if ($accessProc) {
  Write-Host "Access System PID: $($accessProc.Id) (logs: $accessOut / $accessErr)"
} else {
  Write-Host 'Access System: assumed running on port 8080 (PID unknown)'
}
if ($webUiProc) {
  Write-Host "Web UI PID: $($webUiProc.Id) (logs: $webUiOut / $webUiErr)"
} else {
  Write-Host 'Web UI: assumed running on port 8081 (PID unknown)'
}
Write-Host "Event Generator PID: $($generatorProc.Id) (logs: $genOut / $genErr)"
Write-Host 'Access System Health: http://localhost:8080/actuator/health'
Write-Host 'Web UI: http://localhost:8081/'


