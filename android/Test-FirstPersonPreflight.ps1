param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.:-]+$')]
    [string]$Serial,

    [string]$Adb = 'adb',

    [string]$OutputDirectory = 'D:\codexdata\bem-first-person\acceptance'
)

$ErrorActionPreference = 'Stop'
$gamePackages = @('com.hypergryph.endfield', 'com.gryphline.endfield.gp')
$modulePackage = 'dev.betterendfield.next'

function Invoke-DeviceRead([string[]]$DeviceArguments, [switch]$AllowMissing) {
    $result = & $Adb -s $Serial @DeviceArguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        if ($AllowMissing -and -not $result) { return '' }
        throw "ADB read failed ($($DeviceArguments -join ' ')): $($result -join ' ')"
    }
    return (($result | Out-String).Trim())
}

if ((Invoke-DeviceRead -DeviceArguments @('get-state')) -ne 'device') {
    throw "ADB target is not ready: $Serial"
}

$device = [ordered]@{
    serial = $Serial
    captured_at_utc = (Get-Date).ToUniversalTime().ToString('o')
    model = Invoke-DeviceRead -DeviceArguments @('shell', 'getprop', 'ro.product.model')
    build_fingerprint = Invoke-DeviceRead -DeviceArguments @('shell', 'getprop', 'ro.build.fingerprint')
    sdk = Invoke-DeviceRead -DeviceArguments @('shell', 'getprop', 'ro.build.version.sdk')
    abi = Invoke-DeviceRead -DeviceArguments @('shell', 'getprop', 'ro.product.cpu.abi')
    shell_uid = Invoke-DeviceRead -DeviceArguments @('shell', 'id', '-u')
    packages = @()
}

foreach ($package in @($modulePackage) + $gamePackages) {
    $path = Invoke-DeviceRead -DeviceArguments @('shell', 'pm', 'path', $package) -AllowMissing
    $apkPaths = @($path -split '\r?\n' | Where-Object { $_ -match '^package:/' })
    $processes = Invoke-DeviceRead -DeviceArguments @('shell', 'sh', '-c', "pidof $package || true")
    $details = if ($apkPaths.Count) { Invoke-DeviceRead -DeviceArguments @('shell', 'dumpsys', 'package', $package) } else { '' }
    $versionCode = if ($details -match '(?m)^\s*versionCode=(\d+)') { $Matches[1] } else { $null }
    $versionName = if ($details -match '(?m)^\s*versionName=(\S+)') { $Matches[1] } else { $null }
    $device.packages += [ordered]@{
        name = $package
        installed = $apkPaths.Count -gt 0
        apk_paths = $apkPaths
        version_code = $versionCode
        version_name = $versionName
        pid = $processes
    }
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$output = Join-Path $OutputDirectory ("android-device-preflight-{0}.json" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
$device | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $output -Encoding utf8
Write-Output $output
