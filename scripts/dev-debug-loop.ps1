# 开发调试循环：反复运行 :forge:runClient，仅异常退出（崩溃/被重启脚本终止）时自动重启。
# - 手动关闭客户端（正常退出 code=0）→ 循环结束,不再自动拉起（按用户要求）
# - 每轮输出覆盖写入 build\debug-last-run.log；轮次标记追加到 build\debug-loop.log
# - 停止：创建 build\debug-stop 文件（当前这轮跑完后不再重启）
# - 防崩溃死循环：单轮存活 <90s 记为失败，连续 5 次失败自动停止
$ErrorActionPreference = 'Continue'
$root    = Split-Path -Parent $PSScriptRoot
$build   = Join-Path $root 'build'
$loopLog = Join-Path $build 'debug-loop.log'
$runLog  = Join-Path $build 'debug-last-run.log'
$errLog  = Join-Path $build 'debug-last-run.err.log'
$stop    = Join-Path $build 'debug-stop'
if (-not (Test-Path $build)) { New-Item -ItemType Directory -Path $build | Out-Null }
if (Test-Path $stop) { Remove-Item $stop -Force }
# 配套看护：自动最大化每个新客户端窗口（独立进程,重复启动无害——同窗口重复最大化幂等）
Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass',
    '-File', (Join-Path $PSScriptRoot 'dev-maximize-watcher.ps1')

$run = 0
$fails = 0
while ($true) {
    if (Test-Path $stop) { Remove-Item $stop -Force; Add-Content $loopLog "=== stop file seen, loop ends ==="; break }
    $run++
    Add-Content $loopLog "=== [$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] run #$run START ==="
    $sw = [Diagnostics.Stopwatch]::StartNew()
    Push-Location $root
    & .\gradlew.bat :forge:runClient --console=plain *> $runLog
    $code = $LASTEXITCODE
    Pop-Location
    $sw.Stop()
    $dur = [int]$sw.Elapsed.TotalSeconds
    Add-Content $loopLog "=== [$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] run #$run EXIT code=$code dur=${dur}s ==="
    # 正常退出（用户手动关闭客户端）→ 循环结束,不再自动重启
    if ($code -eq 0) {
        Add-Content $loopLog "=== client closed normally (exit 0), loop ends ==="
        break
    }
    if ($dur -lt 90) {
        $fails++
        Add-Content $loopLog "    short run (fail streak $fails/5)"
        Copy-Item $runLog (Join-Path $build ("debug-crash-run{0}.log" -f $run)) -Force
        if ($fails -ge 5) { Add-Content $loopLog "=== 5 consecutive short runs, loop stops ==="; break }
        Start-Sleep -Seconds 20
    } else {
        $fails = 0
        Start-Sleep -Seconds 3
    }
}
