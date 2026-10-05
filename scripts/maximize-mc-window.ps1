# 把调试客户端（dev 启动的 MC）窗口最大化。不抢焦点——只改窗口状态。
# 进程识别与 dev-debug-restart.ps1 同款：命令行含 -Darchitectury.main.class 的 java 进程。
param([int]$TimeoutSec = 180)
$ErrorActionPreference = 'Continue'
$deadline = (Get-Date).AddSeconds($TimeoutSec)
$proc = $null
# 等窗口创建完成（进程刚启动时 MainWindowHandle 可能为 0）
while ((Get-Date) -lt $deadline) {
    $proc = Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
        Where-Object { $_.CommandLine -match 'architectury\.main\.class' } | Select-Object -First 1
    if ($proc) { break }
    Start-Sleep -Milliseconds 800
}
if (-not $proc) { Write-Output 'no dev client process'; exit 0 }

Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Win32 {
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
}
"@
# 进程对象 → 句柄需经 Get-Process 再取
$p = Get-Process -Id $proc.ProcessId -ErrorAction SilentlyContinue
while ((Get-Date) -lt $deadline) {
    $p = Get-Process -Id $proc.ProcessId -ErrorAction SilentlyContinue
    if ($p -and $p.MainWindowHandle -ne 0) { break }
    Start-Sleep -Milliseconds 800
}
if (-not $p -or $p.MainWindowHandle -eq 0) { Write-Output 'client window not found'; exit 0 }
[Win32]::ShowWindow($p.MainWindowHandle, 3) | Out-Null   # 3 = SW_MAXIMIZE
Write-Output ("maximized pid=" + $p.Id + " title='" + $p.MainWindowTitle + "'")
