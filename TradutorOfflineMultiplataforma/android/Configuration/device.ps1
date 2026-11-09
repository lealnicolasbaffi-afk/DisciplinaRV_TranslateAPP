[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [switch]$Prepare,
    [switch]$OfflineTest
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $projectDirectory 'work\sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adbCommand) { throw 'ADB ausente. Configure platform-tools no PATH.' }
    $adbPath = $adbCommand.Source
}
function Invoke-Device {
    param([string[]]$DeviceArguments)
    $output = & $adbPath -s $Serial @DeviceArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join [Environment]::NewLine) }
    return $output
}
function Invoke-Instrumentation {
    param([string[]]$RunnerArguments)
    $output = Invoke-Device (@('shell', 'am', 'instrument', '-w') + $RunnerArguments + 'br.edu.tradutoroffline.test/androidx.test.runner.AndroidJUnitRunner')
    $output
    if (($output -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw 'Instrumentação não terminou com sucesso. Confira a saída acima.' }
}

$appMetadataPath = Join-Path $projectDirectory 'build\outputs\apk\debug\output-metadata.json'
$testMetadataPath = Join-Path $projectDirectory 'build\outputs\apk\androidTest\debug\output-metadata.json'
foreach ($metadataPath in @($appMetadataPath, $testMetadataPath)) {
    if (-not (Test-Path -LiteralPath $metadataPath)) { throw 'Compile assembleDebug assembleDebugAndroidTest antes de instalar.' }
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $apkPath = Join-Path (Split-Path -Parent $metadataPath) $metadata.elements[0].outputFile
    Invoke-Device @('install', '-r', $apkPath)
}
if ($Prepare) {
    Invoke-Instrumentation @('-e', 'class', 'br.edu.tradutoroffline.tests.OnDeviceValidation#prepareRealAppResources', '-e', 'prepare', 'true')
} elseif ($OfflineTest) {
    $originalAirplane = (Invoke-Device @('shell', 'settings', 'get', 'global', 'airplane_mode_on') | Out-String).Trim()
    $originalWifi = (Invoke-Device @('shell', 'settings', 'get', 'global', 'wifi_on') | Out-String).Trim()
    try {
        Invoke-Device @('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
        Invoke-Device @('shell', 'cmd', 'wifi', 'set-wifi-enabled', 'disabled')
        Start-Sleep -Seconds 3
        Invoke-Instrumentation @('-e', 'class', 'br.edu.tradutoroffline.tests.OnDeviceValidation#translatesAndSpeaksBothDirectionsInAirplaneMode', '-e', 'offline', 'true')
    } finally {
        $airplaneSetting = if ($originalAirplane -eq '1') { 'enable' } else { 'disable' }
        $wifiSetting = if ($originalWifi -eq '1') { 'enabled' } else { 'disabled' }
        Invoke-Device @('shell', 'cmd', 'connectivity', 'airplane-mode', $airplaneSetting)
        Invoke-Device @('shell', 'cmd', 'wifi', 'set-wifi-enabled', $wifiSetting)
    }
} else {
    Invoke-Instrumentation @('-e', 'class', 'br.edu.tradutoroffline.tests.AndroidSmokeTest,br.edu.tradutoroffline.tests.VoskNativeTest')
}
