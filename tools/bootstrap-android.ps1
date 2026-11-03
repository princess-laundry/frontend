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
if ((Get-FileHash -LiteralPath $modelPath -Algorithm SHA256).Hash -ne '59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a') { throw 'Pose model checksum mismatch' }
$licenseDirectory = Join-Path $projectRoot 'licenses'
New-Item -ItemType Directory -Force -Path $licenseDirectory | Out-Null
$licensePath = Join-Path $licenseDirectory 'Apache-2.0.txt'
if (-not (Test-Path -LiteralPath $licensePath)) {
    Invoke-WebRequest -Uri 'https://www.apache.org/licenses/LICENSE-2.0.txt' -OutFile $licensePath
}

if ($InstallSdk) {
    $sdkRoot = Join-Path $toolsRoot 'android-sdk'
    $sdkManager = Join-Path $sdkRoot 'cmdline-tools/19.0/bin/sdkmanager.bat'
    if (-not (Test-Path -LiteralPath $sdkManager)) {
        $zipPath = Join-Path $toolsRoot 'commandline-tools.zip'
        Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' -OutFile $zipPath
        if ((Get-FileHash -LiteralPath $zipPath -Algorithm SHA1).Hash -ne '54a582f3bf73e04253602f2d1c80bd5868aac115') {
            throw 'Android command-line tools checksum mismatch'
        }
        $unpackRoot = Join-Path $toolsRoot 'cmdline-19-unpacked'
        Expand-Archive -LiteralPath $zipPath -DestinationPath $unpackRoot -Force
        New-Item -ItemType Directory -Force -Path (Join-Path $sdkRoot 'cmdline-tools') | Out-Null
        $sourcePath = [IO.Path]::GetFullPath((Join-Path $unpackRoot 'cmdline-tools'))
        $targetPath = [IO.Path]::GetFullPath((Join-Path $sdkRoot 'cmdline-tools/19.0'))
        if (-not $sourcePath.StartsWith([IO.Path]::GetFullPath($toolsRoot)) -or -not $targetPath.StartsWith([IO.Path]::GetFullPath($toolsRoot))) { throw 'Invalid SDK path' }
        Move-Item -LiteralPath $sourcePath -Destination $targetPath
    }
    1..20 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" --licenses *> (Join-Path $toolsRoot 'sdk-licenses.log')
    if ($LASTEXITCODE -ne 0) { throw 'SDK license setup failed' }
    & $sdkManager "--sdk_root=$sdkRoot" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
    if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed' }
    "sdk.dir=$($sdkRoot.Replace('\','/'))" | Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Encoding utf8
}
Write-Output 'Android bootstrap complete.'
