# 开发调试循环：反复运行 :forge:runClient，退出后自动重启。
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
