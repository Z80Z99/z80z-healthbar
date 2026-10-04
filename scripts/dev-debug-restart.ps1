# 立即重启调试客户端：仅杀游戏本体进程（dev 启动的 MC，特征是命令行含本模组的 mixin 配置），
# 不动 gradle 守护进程。dev-debug-loop.ps1 检测到本轮退出后会自动重启 → gradle 重新编译 → 新代码生效。
# 用法：代码/资源更新后运行本脚本一次即可，无需手动关窗口。
$ErrorActionPreference = 'Continue'
$procs = Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'z80zhealthbar\.mixins\.json' }
if (-not $procs) { Write-Output 'no running dev client found'; exit 0 }
foreach ($p in $procs) {
    Write-Output ("stopping dev client pid=" + $p.ProcessId)
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
Write-Output 'dev client killed; debug loop will restart it with fresh compile'
