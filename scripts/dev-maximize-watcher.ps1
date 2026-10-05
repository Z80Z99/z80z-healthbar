# 窗口最大化看护：监视调试客户端（dev MC）进程，每个新进程的窗口创建后自动最大化一次。
# 与 dev-debug-loop.ps1 配套：循环每次拉起新客户端,本看护负责把它最大化（含首次启动/崩溃重启/手动重启）。
# 退出条件：build\debug-stop 文件出现（与循环同一停止开关）。
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$stop = Join-Path $root 'build\debug-stop'
$done = @{}   # 已处理过的游戏 PID

Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Win32W {
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
}
"@

while ($true) {
    if (Test-Path $stop) { break }
    $procs = Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
        Where-Object { $_.CommandLine -match 'architectury\.main\.class' }
    foreach ($proc in $procs) {
        if ($done.ContainsKey($proc.ProcessId)) { continue }
        $p = Get-Process -Id $proc.ProcessId -ErrorAction SilentlyContinue
        if (-not $p -or $p.MainWindowHandle -eq 0) { continue }   # 窗口未创建,下轮再试
        [Win32W]::ShowWindow($p.MainWindowHandle, 3) | Out-Null   # 3 = SW_MAXIMIZE
        Write-Output ("[" + (Get-Date -Format 'HH:mm:ss') + "] maximized pid=" + $p.Id)
        $done[$proc.ProcessId] = $true
    }
    # 清理已退出进程的记录,防长跑膨胀
    foreach ($k in @($done.Keys)) {
        if (-not (Get-Process -Id $k -ErrorAction SilentlyContinue)) { $done.Remove($k) }
    }
    Start-Sleep -Seconds 3
}
