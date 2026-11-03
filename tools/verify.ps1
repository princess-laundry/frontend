param([switch]$Bootstrap, [string]$JavaHome)
$ErrorActionPreference = 'Stop'
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
# Some managed Windows hosts expose AF_UNIX but cannot connect to it.
# A nonexistent UDS temp directory lets Java fall back to its supported TCP pipe.
$env:JAVA_TOOL_OPTIONS = '-Djava.net.preferIPv4Stack=true -Djdk.net.unixdomain.tmpdir=C:/sidefocus-no-uds/disabled'
if ($Bootstrap) { & "$PSScriptRoot/bootstrap-android.ps1" -InstallSdk }
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    & .\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers 2
    if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed ($LASTEXITCODE)" }
} finally { Pop-Location }
