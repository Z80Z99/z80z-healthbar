# UI automation helper for Minecraft dev client (local testing only)
# Usage:
#   ui-helper.ps1 shot <file.png>          - screenshot game client area
#   ui-helper.ps1 click <guiX> <guiY>      - move mouse & click at GUI-scaled coords
#   ui-helper.ps1 drag <gx1> <gy1> <gx2> <gy2> - press, move, release
#   ui-helper.ps1 key <ESC|E|...>          - send key to foreground window
param(
    [Parameter(Mandatory=$true)][string]$Action,
    [string]$Out,
    [int]$X, [int]$Y, [int]$X2, [int]$Y2,
    [string]$Key
)
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Windows.Forms,System.Drawing
if (-not ('U32' -as [type])) {
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public struct RECT { public int L; public int T; public int R; public int B; }
public struct PT { public int X; public int Y; }
public class U32 {
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref PT p);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, UIntPtr e);
}
"@
}

$proc = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'Minecraft' } | Select-Object -First 1
if (-not $proc) { Write-Error 'Minecraft window not found (java process with MainWindowTitle)'; exit 1 }
$hwnd = $proc.MainWindowHandle

$rect = New-Object RECT
[void][U32]::GetClientRect($hwnd, [ref]$rect)
$origin = New-Object PT
[void][U32]::ClientToScreen($hwnd, [ref]$origin)
$clientW = $rect.R; $clientH = $rect.B

# GUI scale: read options.txt; 0 = auto (same algorithm as vanilla Window)
$optFile = Join-Path $PSScriptRoot '..\forge\run\client\options.txt'
$scale = 3
if (Test-Path $optFile) {
    $line = (Get-Content $optFile | Where-Object { $_ -match '^guiScale:' } | Select-Object -First 1)
    if ($line -match 'guiScale:(\d+)') {
        $s = [int]$Matches[1]
        if ($s -gt 0) { $scale = $s }
        else {
            $scale = 1
            while (($scale + 1) * 320 -le $clientW -and ($scale + 1) * 240 -le $clientH) { $scale++ }
        }
    }
}

function Convert-Gui([int]$gx, [int]$gy) {
    return @{ px = $origin.X + [int]($gx * $scale); py = $origin.Y + [int]($gy * $scale) }
}

switch ($Action) {
    'shot' {
        [void][U32]::SetForegroundWindow($hwnd)
        Start-Sleep -Milliseconds 250
        $bmp = New-Object System.Drawing.Bitmap $clientW, $clientH
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.CopyFromScreen($origin.X, $origin.Y, 0, 0, (New-Object System.Drawing.Size($clientW, $clientH)))
        $bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose()
        Write-Host "shot: $Out ($clientW x $clientH, guiScale=$scale)"
    }
    'click' {
        [void][U32]::SetForegroundWindow($hwnd)
        Start-Sleep -Milliseconds 120
        $p = Convert-Gui $X $Y
        [void][U32]::SetCursorPos($p.px, $p.py)
        Start-Sleep -Milliseconds 80
        [U32]::mouse_event(0x02, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 60
        [U32]::mouse_event(0x04, 0, 0, 0, [UIntPtr]::Zero)
        Write-Host "click gui($X,$Y) -> px($($p.px),$($p.py)) scale=$scale"
    }
    'drag' {
        [void][U32]::SetForegroundWindow($hwnd)
        Start-Sleep -Milliseconds 120
        $a = Convert-Gui $X $Y
        $b = Convert-Gui $X2 $Y2
        [void][U32]::SetCursorPos($a.px, $a.py)
        Start-Sleep -Milliseconds 60
        [U32]::mouse_event(0x02, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 60
        $steps = 12
        for ($i = 1; $i -le $steps; $i++) {
            $cx = [int]($a.px + ($b.px - $a.px) * $i / $steps)
            $cy = [int]($a.py + ($b.py - $a.py) * $i / $steps)
            [void][U32]::SetCursorPos($cx, $cy)
            Start-Sleep -Milliseconds 25
        }
        [U32]::mouse_event(0x04, 0, 0, 0, [UIntPtr]::Zero)
        Write-Host "drag gui($X,$Y)->($X2,$Y2)"
    }
    'wheel' {
        [void][U32]::SetForegroundWindow($hwnd)
        Start-Sleep -Milliseconds 120
        $p = Convert-Gui $X $Y
        [void][U32]::SetCursorPos($p.px, $p.py)
        Start-Sleep -Milliseconds 60
        $d = [int]($Y2 * 120)
        $dw = if ($d -ge 0) { [uint32]$d } else { [uint32](4294967296 + $d) }
        [U32]::mouse_event(0x0800, 0, 0, $dw, [UIntPtr]::Zero)
        Write-Host "wheel at gui($X,$Y) delta=$Y2"
    }
    'key' {
        [void][U32]::SetForegroundWindow($hwnd)
        Start-Sleep -Milliseconds 120
        [System.Windows.Forms.SendKeys]::SendWait($Key)
        Write-Host "key: $Key"
    }
    'info' {
        Write-Host "client=${clientW}x${clientH} origin=($($origin.X),$($origin.Y)) guiScale=$scale title='$($proc.MainWindowTitle)'"
    }
}
