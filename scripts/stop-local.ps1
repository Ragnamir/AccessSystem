Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Resolve project root (this script is in scripts/)
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

Write-Host 'Stopping Access System services...' -ForegroundColor Yellow

# Function to stop process gracefully
function Stop-ProcessGracefully {
    param(
        [int]$ProcessId,
        [string]$ProcessName
    )
    
    if (-not $ProcessId) {
        Write-Host "  $ProcessName : Process not found" -ForegroundColor Gray
        return $false
    }
    
    try {
        $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if (-not $proc) {
            Write-Host "  $ProcessName (PID $ProcessId) : Already stopped" -ForegroundColor Gray
            return $false
        }
        
        Write-Host "  Stopping $ProcessName (PID $ProcessId)..." -ForegroundColor Cyan
        Stop-Process -Id $ProcessId -Force -ErrorAction Stop
        
        # Wait a bit to ensure process stopped
        Start-Sleep -Milliseconds 500
        
        # Verify it's stopped
        $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "  Warning: $ProcessName (PID $ProcessId) is still running, forcing kill..." -ForegroundColor Yellow
            Stop-Process -Id $ProcessId -Force -ErrorAction Stop
        }
        
        Write-Host "  $ProcessName (PID $ProcessId) : Stopped" -ForegroundColor Green
        return $true
    } catch {
        Write-Host "  Error stopping $ProcessName (PID $ProcessId): $_" -ForegroundColor Red
        return $false
    }
}

# Stop processes by finding Java processes listening on specific ports
# Find all processes (including child Java processes) listening on the ports

# Stop Access System (port 8080)
$accessConnections = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($accessConnections) {
    $stopped = $false
    foreach ($conn in $accessConnections) {
        $pid = $conn.OwningProcess
        
        # Stop the process and all its Java children
        $pidsToStop = @($pid)
        
        # Find child Java processes
        $childProcs = Get-WmiObject Win32_Process | Where-Object { $_.ParentProcessId -eq $pid }
        foreach ($child in $childProcs) {
            if ($child.CommandLine -like '*java*') {
                $pidsToStop += $child.ProcessId
            }
        }
        
        # Stop all found processes
        foreach ($pidToStop in ($pidsToStop | Sort-Object -Unique)) {
            if (Stop-ProcessGracefully -ProcessId $pidToStop -ProcessName 'Access System') {
                $stopped = $true
            }
        }
    }
    if (-not $stopped) {
        Write-Host '  Access System : Not running on port 8080' -ForegroundColor Gray
    }
} else {
    Write-Host '  Access System : Not running on port 8080' -ForegroundColor Gray
}

# Stop Web UI (port 8081)
$webUiConnections = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
if ($webUiConnections) {
    $stopped = $false
    foreach ($conn in $webUiConnections) {
        $pid = $conn.OwningProcess
        
        # Stop the process and all its Java children
        $pidsToStop = @($pid)
        
        # Find child Java processes
        $childProcs = Get-WmiObject Win32_Process | Where-Object { $_.ParentProcessId -eq $pid }
        foreach ($child in $childProcs) {
            if ($child.CommandLine -like '*java*') {
                $pidsToStop += $child.ProcessId
            }
        }
        
        # Stop all found processes
        foreach ($pidToStop in ($pidsToStop | Sort-Object -Unique)) {
            if (Stop-ProcessGracefully -ProcessId $pidToStop -ProcessName 'Web UI') {
                $stopped = $true
            }
        }
    }
    if (-not $stopped) {
        Write-Host '  Web UI : Not running on port 8081' -ForegroundColor Gray
    }
} else {
    Write-Host '  Web UI : Not running on port 8081' -ForegroundColor Gray
}

# Stop Event Generator (find by java -jar event-generator)
Write-Host '  Searching for Event Generator...' -ForegroundColor Cyan
$eventGenProcs = Get-WmiObject Win32_Process | Where-Object { 
    $_.CommandLine -like '*java*' -and 
    $_.CommandLine -like '*-jar*' -and 
    $_.CommandLine -like '*event-generator*'
}

if ($eventGenProcs) {
    foreach ($proc in $eventGenProcs) {
        Stop-ProcessGracefully -ProcessId $proc.ProcessId -ProcessName 'Event Generator'
    }
} else {
    Write-Host '  Event Generator : Not running' -ForegroundColor Gray
}

Write-Host ''
Write-Host 'All services stopped.' -ForegroundColor Green

