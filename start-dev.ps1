param(
  [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$PowerShellExe = (Get-Command powershell.exe).Source
$RunnerDir = Join-Path $env:TEMP "contentflow-dev"
New-Item -ItemType Directory -Force -Path $RunnerDir | Out-Null

function Test-CommandAvailable {
  param([string]$Name)
  return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Assert-CommandAvailable {
  param(
    [string]$Name,
    [string]$InstallHint
  )

  if (-not (Test-CommandAvailable $Name)) {
    throw "Missing command '$Name'. $InstallHint"
  }
}

function Assert-EnvFile {
  param([string]$RelativePath)

  $path = Join-Path $Root $RelativePath
  if (-not (Test-Path $path)) {
    $example = "$RelativePath.example"
    Write-Warning "Missing $RelativePath. Copy $example and fill local values before the service can use custom config."
  }
}

function New-ServiceCommand {
  param(
    [string]$Name,
    [string]$EnvPath,
    [string[]]$SetupLines,
    [string]$RunLine
  )

  $skipInstallValue = if ($SkipInstall) { '$true' } else { '$false' }
  $setupBlock = ($SetupLines -join [Environment]::NewLine)

  return @"
`$ErrorActionPreference = "Stop"
`$SkipInstall = $skipInstallValue

function Import-DotEnv {
  param([string]`$Path)

  if (-not (Test-Path `$Path)) {
    return
  }

  Get-Content `$Path | ForEach-Object {
    `$line = `$_.Trim()
    if (-not `$line -or `$line.StartsWith("#") -or -not `$line.Contains("=")) {
      return
    }

    `$parts = `$line.Split("=", 2)
    `$name = `$parts[0].Trim()
    `$value = `$parts[1].Trim()

    if ((`$value.StartsWith('"') -and `$value.EndsWith('"')) -or (`$value.StartsWith("'") -and `$value.EndsWith("'"))) {
      `$value = `$value.Substring(1, `$value.Length - 2)
    }

    Set-Item -Path ("Env:" + `$name) -Value `$value
  }
}

Write-Host "Starting $Name..." -ForegroundColor Cyan
Import-DotEnv "$EnvPath"
$setupBlock
$RunLine
Read-Host "Service '$Name' stopped. Press Enter to close this window"
"@
}

function Start-ServiceWindow {
  param(
    [string]$Title,
    [string]$WorkingDirectory,
    [string]$Command
  )

  $scriptName = ($Title -replace '[^a-zA-Z0-9_-]', '_') + ".ps1"
  $scriptPath = Join-Path $RunnerDir $scriptName
  $scriptContent = "`$Host.UI.RawUI.WindowTitle = '$Title'" + [Environment]::NewLine + $Command
  Set-Content -Path $scriptPath -Value $scriptContent -Encoding UTF8

  Start-Process -FilePath $PowerShellExe -WorkingDirectory $WorkingDirectory -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    $scriptPath
  )
}

Assert-CommandAvailable "mvn" "Install Maven and make sure it is on PATH."
Assert-CommandAvailable "npm" "Install Node.js 20 or newer and make sure npm is on PATH."

Assert-EnvFile "backend\.env"
Assert-EnvFile "agent\.env"
Assert-EnvFile "web\.env"

$backendDir = Join-Path $Root "backend"
$agentDir = Join-Path $Root "agent"
$webDir = Join-Path $Root "web"
$agentBootstrapPython = "C:\Users\1\miniconda3\python.exe"
if (-not (Test-Path $agentBootstrapPython)) {
  throw "Python 3.12 was not found at $agentBootstrapPython. Install Python 3.12 or update start-dev.ps1."
}

$backendCommand = New-ServiceCommand `
  -Name "ContentFlow Backend" `
  -EnvPath ".env" `
  -SetupLines @() `
  -RunLine "mvn spring-boot:run"

$agentCommand = New-ServiceCommand `
  -Name "ContentFlow Agent" `
  -EnvPath ".env" `
  -SetupLines @(
    "if (-not `$SkipInstall -and -not (Test-Path '.venv312\\Scripts\\python.exe')) { & '$agentBootstrapPython' -m venv .venv312 }",
    'if (-not $SkipInstall -and (Test-Path ".venv312\Scripts\python.exe")) { .\.venv312\Scripts\python.exe -m pip install -e ".[test]" }'
  ) `
  -RunLine '.\.venv312\Scripts\python.exe -m uvicorn app.main:app --reload --port 8000'

$webCommand = New-ServiceCommand `
  -Name "ContentFlow Web" `
  -EnvPath ".env" `
  -SetupLines @(
    'if (-not $SkipInstall -and -not (Test-Path "node_modules")) { npm install }'
  ) `
  -RunLine "npm run dev"

Start-ServiceWindow -Title "ContentFlow Backend :8080" -WorkingDirectory $backendDir -Command $backendCommand
Start-ServiceWindow -Title "ContentFlow Agent :8000" -WorkingDirectory $agentDir -Command $agentCommand
Start-ServiceWindow -Title "ContentFlow Web :5173" -WorkingDirectory $webDir -Command $webCommand

Write-Host "ContentFlow services are starting in separate windows." -ForegroundColor Green
Write-Host "Backend: http://localhost:8080"
Write-Host "Agent:   http://localhost:8000"
Write-Host "Web:     http://localhost:5173"
