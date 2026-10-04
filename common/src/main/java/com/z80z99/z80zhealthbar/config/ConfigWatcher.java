package com.z80z99.z80zhealthbar.config;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.platform.PlatformService;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.zip.CRC32;

/**
 * 配置文件监视器（开发调试用）：外部编辑 z80zhealthbar.json 保存后 ~300ms 自动重载，
 * 无需重启游戏。渲染路径每帧读取配置对象，重载即换引用，下一帧立即生效。
 *
 * <ul>
 *   <li>仅客户端启动（服务端无意义）；守护线程，游戏退出自动结束。</li>
 *   <li>CRC32 去重：本 MOD 自己的 saveConfig 也会触发 ENTRY_MODIFY，内容未变则跳过，
 *       避免自触发循环刷日志。</li>
 *   <li>loadConfig 在监视线程执行：config 为引用替换（原子），渲染线程最多滞后一帧旧值。</li>
 * </ul>
 */
public final class ConfigWatcher {
    private static volatile boolean started;
    private static volatile long lastCrc = -1;

    private ConfigWatcher() {}

    public static synchronized void start() {
        if (started) return;
        started = true;
        Path dir;
        try {
            dir = PlatformService.get().getConfigDir();
        } catch (Throwable t) {
            return; // 平台未就绪（异常环境）静默放弃
        }
        Path file = dir.resolve("z80zhealthbar.json");
        recordCrc(file);

        Thread t = new Thread(() -> watch(dir, file), "z80zhealthbar-config-watcher");
        t.setDaemon(true);
        t.start();
        Z80ZHealthBar.LOGGER.info("[Config] hot-reload watcher started on {}", file);
    }

    private static void watch(Path dir, Path file) {
        try (var ws = FileSystems.getDefault().newWatchService()) {
            dir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            while (true) {
                var key = ws.take();
                for (var event : key.pollEvents()) {
                    Object ctx = event.context();
                    if (ctx != null && ctx.toString().equalsIgnoreCase(file.getFileName().toString())) {
                        Thread.sleep(300); // 等编辑器写完
                        if (recordCrc(file)) {
                            // 自己 saveConfig 写出的内容不重载：重载会替换配置对象，
                            // 让仍持有旧引用的设置界面把后续修改写进孤立实例（丢设置）
                            if (ConfigManager.getLastSavedCrc() == lastCrc) {
                                continue;
                            }
                            ConfigManager.loadConfig();
                            Z80ZHealthBar.LOGGER.info("[Config] hot-reloaded (file changed)");
                        }
                    }
                }
                key.reset();
            }
        } catch (InterruptedException ignored) {
            // 游戏退出
        } catch (Exception e) {
            Z80ZHealthBar.LOGGER.warn("[Config] hot-reload watcher stopped: {}", e.toString());
        }
    }

    /** 计算文件 CRC32；内容与上次一致返回 false（跳过自触发的写事件） */
    private static boolean recordCrc(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            CRC32 crc = new CRC32();
            crc.update(bytes);
            long value = crc.getValue();
            if (value == lastCrc) return false;
            lastCrc = value;
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
