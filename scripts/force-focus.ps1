# 强制把前台焦点切到 Minecraft 窗口（绕过 Windows 前台锁定的 AttachThreadInput 技巧）
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class Fg {
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern IntPtr SetFocus(IntPtr hWnd);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern IntPtr GetShellWindow();
}
"@

$p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'Minecraft' } | Select-Object -First 1
if (-not $p) { Write-Output 'Minecraft window not found'; exit 1 }
$hwnd = $p.MainWindowHandle

$fgHwnd = [Fg]::GetForegroundWindow()
$fgThread = [Fg]::GetWindowThreadProcessId($fgHwnd, [ref]([uint32]0))
$myThread = [Fg]::GetCurrentThreadId()
$targetThread = [Fg]::GetWindowThreadProcessId($hwnd, [ref]([uint32]0))

# 把当前线程附加到目标线程和前台线程的输入队列，绕过前台锁定
[void][Fg]::AttachThreadInput($myThread, $targetThread, $true)
[void][Fg]::AttachThreadInput($myThread, $fgThread, $true)
[void][Fg]::BringWindowToTop($hwnd)
[void][Fg]::SetForegroundWindow($hwnd)
[void][Fg]::SetFocus($hwnd)
[void][Fg]::AttachThreadInput($myThread, $fgThread, $false)
[void][Fg]::AttachThreadInput($myThread, $targetThread, $false)

Start-Sleep -Milliseconds 300
$now = [Fg]::GetForegroundWindow()
Write-Output ("foreground now = " + $now + " | target = " + $hwnd + " | match = " + ($now -eq $hwnd))
