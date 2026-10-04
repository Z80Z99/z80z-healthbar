package com.z80z99.z80zhealthbar.config;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.mobdisplay.EntityHealthStyle;
import com.z80z99.z80zhealthbar.platform.PlatformService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Z80ZHealthBarConfig config;
    /** 最近一次 saveConfig 写盘内容的 CRC（ConfigWatcher 用于识别"自己写的"并跳过重载，
     *  避免设置界面持有的配置对象被替换成孤立实例后丢失后续修改） */
    private static volatile long lastSavedCrc = -1;

    private ConfigManager() {}

    public static Z80ZHealthBarConfig getConfig() {
        if (config == null) {
            config = new Z80ZHealthBarConfig();
        }
        return config;
    }

    public static void loadConfig() {
        Path configPath = getConfigFilePath();
        boolean needsSave = false;
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = GSON.fromJson(json, Z80ZHealthBarConfig.class);
                if (config == null) {
                    config = new Z80ZHealthBarConfig();
                }
                needsSave = migrate(config);
                Z80ZHealthBar.LOGGER.info("Config loaded from {}", configPath);
            } catch (IOException | JsonSyntaxException e) {
                Z80ZHealthBar.LOGGER.error("Failed to load config, using defaults", e);
                config = new Z80ZHealthBarConfig();
                needsSave = true;
            }
        } else {
            config = new Z80ZHealthBarConfig();
            needsSave = true;
            Z80ZHealthBar.LOGGER.info("Default config created at {}", configPath);
        }
        // 校验会就地夹取非法数值（负缩放/超大距离等），不抛异常
        ConfigValidator.validate(config);
        // 黑名单/选择器解析依赖 MC 注册表，须在加载后、渲染前完成一次
        config.visibility.parseAll();
        if (needsSave) saveConfig();
    }

    /**
     * 旧版本配置迁移（任务书 10：旧配置尽可能迁移而非丢弃）。
     *
     * @return 迁移发生（需要回写文件）
     */
    static boolean migrate(Z80ZHealthBarConfig cfg) {
        boolean migrated = false;
        if (cfg.configVersion < 2) {
            if (cfg.mobDisplayMode != null) {
                cfg.entityStyle = EntityHealthStyle.fromLegacy(cfg.mobDisplayMode).name();
            }
            cfg.configVersion = 2;
            migrated = true;
        }
        if (cfg.configVersion < 3) {
            // v3：barHalfWidth 此前从未被渲染器读取（死配置），任何旧值都不代表真实意图
            // → 统一置 0（自动跟随血量），保持既有观感；此后用户设置的值正常保留
            cfg.barStyle.barHalfWidth = 0;
            cfg.configVersion = 3;
            migrated = true;
        }
        // 任何版本：遗留 mobDisplayMode 字段迁移后清除（Gson 输出时忽略 null 字段）
        if (cfg.mobDisplayMode != null) {
            if (cfg.entityStyle == null || cfg.entityStyle.isBlank()) {
                cfg.entityStyle = EntityHealthStyle.fromLegacy(cfg.mobDisplayMode).name();
            }
            cfg.mobDisplayMode = null;
            migrated = true;
        }
        return migrated;
    }

    /** 测试/导入用：直接替换当前配置实例 */
    public static void setConfig(Z80ZHealthBarConfig newConfig) {
        config = newConfig;
        ConfigValidator.validate(config);
        config.visibility.parseAll();
    }

    public static void saveConfig() {
        Path configPath = getConfigFilePath();
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(getConfig());
            lastSavedCrc = crc32(json);
            Files.writeString(configPath, json, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            Z80ZHealthBar.LOGGER.error("Failed to save config", e);
        }
    }

    /** 最近一次自身保存的 CRC；-1 = 本会话尚未保存过 */
    public static long getLastSavedCrc() {
        return lastSavedCrc;
    }

    static long crc32(String s) {
        CRC32 crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    /** 恢复全部默认配置（不落盘；调用方决定是否 saveConfig） */
    public static void resetToDefaults() {
        config = new Z80ZHealthBarConfig();
        config.visibility.parseAll();
    }

    public static Path getConfigFilePath() {
        return PlatformService.get().getConfigDir().resolve("z80zhealthbar.json");
    }
}
