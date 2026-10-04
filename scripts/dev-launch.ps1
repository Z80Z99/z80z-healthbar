# Z80Z Health Bar — Dev Launch Script (1.20.1 Forge)
# 用法：在 E:\MCMOD\z80zhealthbar 目录下执行 .\scripts\dev-launch.ps1
# 启动 Forge dev 客户端，玩家名取自 gradle.properties 的 minecraft_username (默认 Developer)
# 第三方 compat 测试需手动把 AppleSkin/ThirstWasTaken/ParCool! jar 放入 forge\run\client\mods
# 本脚本不依赖外部 mod 即可启动，验收 V1 主功能：6 条 HUD + 头顶血条 + 配置界面

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $root 'gradlew.bat'

Write-Host "== Launching Forge dev client (1.20.1) ==" -ForegroundColor Cyan
& $gradlew -p $root :forge:runClient --no-daemon --console=plain
Write-Host "Exit code: $LASTEXITCODE"