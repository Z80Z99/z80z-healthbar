# Z80Z Health Bar - Dev Art Pack Builder (LOCAL DEBUG ONLY)
#
# Purpose: pack reference-mod art into a dev-client resource pack that serves
#          textures under the z80zhealthbar namespace (e.g. hpbar_original.png,
#          the original 14-type layout for style A debugging).
#
# LICENSE RED LINE (docs/third-party-licenses.md):
#   mobhealthbar & AsteorBar are All-Rights-Reserved. Their assets may only be
#   used inside the local run directory for debug comparison. NEVER copy them
#   into src/main/resources, NEVER ship them in the JAR or the repository.
#   Output goes to forge/run/client/resourcepacks/ (gitignored via run/).
#
# Usage: powershell -File scripts/make-dev-art-pack.ps1 [-AlsoEnable] [-AlsoSetOriginalMode]

param(
    [switch]$AlsoEnable,
    [switch]$AlsoSetOriginalMode
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$refRoot = Join-Path $root '..'

$map = @(
    @{ Src = Join-Path $refRoot 'mobhealthbar-forge-1.20.x-2.3.0\assets\mobhealthbar\textures\gui\hpbar.png';
       Dst = 'assets/z80zhealthbar/textures/gui/hpbar_original.png' },
    @{ Src = Join-Path $refRoot 'asteorbar-forge-1.20.1-1.5.3\assets\asteorbar\textures\ui\lightmap.png';
       Dst = 'assets/z80zhealthbar/textures/ui/lightmap_original.png' },
    @{ Src = Join-Path $refRoot 'MobPlaques-v8.0.1-1.20.1-Forge\assets\mobplaques\textures\gui\icons.png';
       Dst = 'assets/z80zhealthbar/textures/gui/icons_original.png' }
)

$packDir = Join-Path $root 'forge\run\client\resourcepacks\z80z-dev-original-art'
if (Test-Path $packDir) { Remove-Item -Recurse -Force $packDir }
New-Item -ItemType Directory -Force -Path (Join-Path $packDir 'assets\z80zhealthbar\textures\gui') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $packDir 'assets\z80zhealthbar\textures\ui') | Out-Null

@'
{
  "pack": {
    "pack_format": 15,
    "description": "DEV-ONLY original art reference pack (ARR assets - never redistribute)"
  }
}
'@ | ForEach-Object { [IO.File]::WriteAllText((Join-Path $packDir 'pack.mcmeta'), $_) }

foreach ($m in $map) {
    if (Test-Path $m.Src) {
        Copy-Item $m.Src (Join-Path $packDir $m.Dst) -Force
        Write-Host "copied: $($m.Src) -> $($m.Dst)"
    } else {
        Write-Warning "missing source: $($m.Src)"
    }
}

$zip = "$packDir.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
# NOTE: Compress-Archive writes backslash zip entry names, which Minecraft cannot
# resolve. Use the JDK jar tool (forward slashes, no manifest) instead.
$jdkBin = Split-Path -Parent (Get-Command java).Source
& (Join-Path $jdkBin 'jar.exe') --create --file $zip --no-manifest -C $packDir .
if ($LASTEXITCODE -ne 0) { Write-Error "jar.exe failed with exit code $LASTEXITCODE" }
Write-Host "pack: $zip"

if ($AlsoEnable) {
    $options = Join-Path $root 'forge\run\client\options.txt'
    if (Test-Path $options) {
        $lines = Get-Content $options
        $entry = '"file/z80z-dev-original-art.zip"'
        $found = $false
        $lines = $lines | ForEach-Object {
            if ($_ -match '^resourcePacks:(.*)$') {
                $found = $true
                $list = $Matches[1].Trim()
                if ($list -eq '[]' -or -not $list) { $list = "[$entry]" }
                elseif ($list -notmatch [regex]::Escape('z80z-dev-original-art')) {
                    $list = $list.TrimEnd(']') + ",$entry]"
                }
                "resourcePacks:$list"
            } else { $_ }
        }
        if (-not $found) { $lines += "resourcePacks:[$entry]" }
        [IO.File]::WriteAllText($options, ($lines -join "`r`n") + "`r`n")
        Write-Host "options.txt: pack enabled"
    } else { Write-Warning "options.txt not found" }
}

if ($AlsoSetOriginalMode) {
    $cfg = Join-Path $root 'forge\run\client\config\z80zhealthbar.json'
    if (Test-Path $cfg) {
        $json = Get-Content $cfg -Raw | ConvertFrom-Json
        if (-not $json.styleA) {
            $json | Add-Member -NotePropertyName styleA -NotePropertyValue ([pscustomobject]@{})
        }
        if ($json.styleA.PSObject.Properties['textureMode']) { $json.styleA.textureMode = 'ORIGINAL' }
        else { $json.styleA | Add-Member -NotePropertyName textureMode -NotePropertyValue 'ORIGINAL' }
        $jsonText = $json | ConvertTo-Json -Depth 12
        [IO.File]::WriteAllText($cfg, $jsonText)
        Write-Host "config: styleA.textureMode=ORIGINAL"
    } else { Write-Warning "dev config not found" }
}
