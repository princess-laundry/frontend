param([switch]$InstallSdk)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot '.tools'
New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null

# Official Gradle wrapper sources; binaries and dependencies are pinned.
$wrapperRoot = Join-Path $projectRoot 'gradle/wrapper'
New-Item -ItemType Directory -Force -Path $wrapperRoot | Out-Null
$gradleSource = 'https://raw.githubusercontent.com/gradle/gradle/v8.13.0'
foreach ($file in @('gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar')) {
    $target = Join-Path $projectRoot $file
    if (-not (Test-Path -LiteralPath $target)) {
        Invoke-WebRequest -Uri "$gradleSource/$file" -OutFile $target
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $wrapperRoot 'gradle-wrapper.properties'))) {
    @'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
'@ | Set-Content -LiteralPath (Join-Path $wrapperRoot 'gradle-wrapper.properties') -Encoding utf8
}

$assets = Join-Path $projectRoot 'app/src/main/assets'
New-Item -ItemType Directory -Force -Path $assets | Out-Null
$modelPath = Join-Path $assets 'pose_landmarker_lite.task'
if (-not (Test-Path -LiteralPath $modelPath)) {
    Invoke-WebRequest -Uri 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task' -OutFile $modelPath
}

if ($InstallSdk) {
    $sdkRoot = Join-Path $toolsRoot 'android-sdk'
    $sdkManager = Join-Path $sdkRoot 'cmdline-tools/latest/bin/sdkmanager.bat'
    if (-not (Test-Path -LiteralPath $sdkManager)) {
        $zipPath = Join-Path $toolsRoot 'commandline-tools.zip'
        Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip' -OutFile $zipPath
        if ((Get-FileHash -LiteralPath $zipPath -Algorithm SHA1).Hash -ne '57d04f2d75eb8e8fffc5000a987e5de4b5a63e9d') {
            throw 'Android command-line tools checksum mismatch'
        }
        $unpackRoot = Join-Path $toolsRoot 'cmdline-unpacked'
        Expand-Archive -LiteralPath $zipPath -DestinationPath $unpackRoot -Force
        New-Item -ItemType Directory -Force -Path (Join-Path $sdkRoot 'cmdline-tools') | Out-Null
        Move-Item -LiteralPath (Join-Path $unpackRoot 'cmdline-tools') -Destination (Join-Path $sdkRoot 'cmdline-tools/latest')
    }
    1..20 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" --licenses
    if ($LASTEXITCODE -ne 0) { throw 'SDK license setup failed' }
    & $sdkManager "--sdk_root=$sdkRoot" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
    if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed' }
    "sdk.dir=$($sdkRoot.Replace('\','/'))" | Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Encoding utf8
}
Write-Output 'Android bootstrap complete.'
