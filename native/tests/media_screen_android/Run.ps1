param(
    [string]$JavaCompiler = 'javac',
    [string]$Java = 'java',
    [string]$JsonJar = $env:BEM_PORT_JSON_JAR,
    [string]$WorkDir = 'D:/codexdata/bem-media-host-check'
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$mediaOutput = Join-Path $WorkDir ([guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $mediaOutput -Force | Out-Null
$mediaJar = $JsonJar
if (!$mediaJar -or !(Test-Path -LiteralPath $mediaJar)) { throw 'Pass -JsonJar or set BEM_PORT_JSON_JAR to the real JSON-java dependency.' }
$mediaSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'stubs'), (Join-Path $PSScriptRoot 'src') -Filter '*.java' -Recurse | ForEach-Object FullName)
$mediaProduction = @('MediaScreenFiles.java','MediaScreenControl.java') | ForEach-Object { Join-Path $repoRoot "android/app/src/main/java/dev/betterendfield/next/$_" }
& $JavaCompiler -encoding UTF-8 --release 17 -cp $mediaJar -d $mediaOutput @mediaProduction @mediaSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $Java -cp ($mediaOutput + ';' + $mediaJar) dev.betterendfield.next.MediaScreenFilesTest (Join-Path $mediaOutput 'fixtures')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $Java -cp ($mediaOutput + ';' + $mediaJar) dev.betterendfield.next.MediaScreenControlTest
exit $LASTEXITCODE
