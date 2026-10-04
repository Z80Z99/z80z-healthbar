# Force focus + click in one process (first click after refocus would be swallowed otherwise)
param(
    [Parameter(Mandatory=$true)][int]$Gx,
    [Parameter(Mandatory=$true)][int]$Gy,
    [int]$Repeat = 2
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms,System.Drawing
if (-not ('U32B' -as [type])) {
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public struct RECTB { public int L; public int T; public int R; public int B; }
public struct PTB { public int X; public int Y; }
public class U32B {
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECTB r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref PTB p);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint a, uint b, bool f);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr SetFocus(IntPtr h);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, UIntPtr e);
}
"@
}
$proc = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'Minecraft' } | Select-Object -First 1
if (-not $proc) { Write-Error 'Minecraft window not found'; exit 1 }
$hwnd = $proc.MainWindowHandle

# Force foreground (AttachThreadInput bypasses foreground lock)
$fg = [U32B]::GetForegroundWindow()
$fgT = [U32B]::GetWindowThreadProcessId($fg, [ref]([uint32]0))
$myT = [U32B]::GetCurrentThreadId()
$tgT = [U32B]::GetWindowThreadProcessId($hwnd, [ref]([uint32]0))
[void][U32B]::AttachThreadInput($myT, $tgT, $true)
[void][U32B]::AttachThreadInput($myT, $fgT, $true)
[void][U32B]::BringWindowToTop($hwnd)
[void][U32B]::SetForegroundWindow($hwnd)
[void][U32B]::SetFocus($hwnd)
[void][U32B]::AttachThreadInput($myT, $fgT, $false)
[void][U32B]::AttachThreadInput($myT, $tgT, $false)
Start-Sleep -Milliseconds 250

# GUI to screen coords
$optFile = Join-Path $PSScriptRoot '..\forge\run\client\options.txt'
$scale = 3
if (Test-Path $optFile) {
    $line = (Get-Content $optFile | Where-Object { $_ -match '^guiScale:' } | Select-Object -First 1)
    if ($line -match 'guiScale:(\d+)') { $s = [int]$Matches[1]; if ($s -gt 0) { $scale = $s } }
}
$rect = New-Object RECTB
[void][U32B]::GetClientRect($hwnd, [ref]$rect)
$origin = New-Object PTB
[void][U32B]::ClientToScreen($hwnd, [ref]$origin)
$px = $origin.X + $Gx * $scale
$py = $origin.Y + $Gy * $scale

[void][U32B]::SetCursorPos($px, $py)
Start-Sleep -Milliseconds 120
for ($i = 0; $i -lt $Repeat; $i++) {
    [U32B]::mouse_event(0x02, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 70
    [U32B]::mouse_event(0x04, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 280
}
Write-Output "clicked gui($Gx,$Gy) x$Repeat -> px($px,$py) scale=$scale"
