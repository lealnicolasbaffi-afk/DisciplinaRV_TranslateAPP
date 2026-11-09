[CmdletBinding()]
param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [switch]$Offline,
    [switch]$DeviceTests
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$wrapperHash = (Get-FileHash -LiteralPath (Join-Path $projectDirectory 'gradle\wrapper\gradle-wrapper.jar') -Algorithm SHA256).Hash
if ($wrapperHash -ne '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046') {
    throw 'O Gradle Wrapper não passou na verificação SHA-256.'
}
if (-not $Jdk) {
    $unityEditors = Join-Path ${env:ProgramFiles} 'Unity\Hub\Editor'
    if (Test-Path -LiteralPath $unityEditors) {
        $Jdk = Get-ChildItem -LiteralPath $unityEditors -Directory | Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK' } |
            Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe') } | Select-Object -First 1
    }
}
if (-not $AndroidSdk) { $AndroidSdk = Join-Path $projectDirectory 'work\sdk' }
if (-not $Jdk -or -not (Test-Path -LiteralPath (Join-Path $Jdk 'bin\javac.exe'))) {
    throw 'Informe um JDK completo 17: .\Configuration\check.ps1 -Jdk C:\caminho\jdk-17'
}
foreach ($relative in @('platforms\android-35\android.jar', 'build-tools\35.0.0\aapt2.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $AndroidSdk $relative))) {
        throw "SDK incompleto: $relative. Instale Platform 35 e Build Tools 35.0.0 e informe -AndroidSdk."
    }
}
$previousJava = $env:JAVA_HOME
$previousAndroid = $env:ANDROID_HOME
$previousGradle = $env:GRADLE_USER_HOME
Push-Location -LiteralPath $projectDirectory
try {
    $env:JAVA_HOME = $Jdk
    $env:ANDROID_HOME = (Resolve-Path -LiteralPath $AndroidSdk).Path
    $env:GRADLE_USER_HOME = Join-Path $projectDirectory 'work\gradle-home'
    $arguments = @('--no-daemon', '--console=plain', 'assembleDebug', 'testDebugUnitTest', 'lintDebug')
    if ($DeviceTests) { $arguments += 'assembleDebugAndroidTest' }
    if ($Offline) { $arguments += '--offline' }
    & .\gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw "Checks Android falharam (exit $LASTEXITCODE)." }
    $metadataPath = Join-Path $projectDirectory 'build\outputs\apk\debug\output-metadata.json'
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $apkPath = Join-Path (Split-Path -Parent $metadataPath) $metadata.elements[0].outputFile
    Write-Output "APK: $apkPath"
    Get-FileHash -LiteralPath $apkPath -Algorithm SHA256
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousAndroid
    $env:GRADLE_USER_HOME = $previousGradle
}
