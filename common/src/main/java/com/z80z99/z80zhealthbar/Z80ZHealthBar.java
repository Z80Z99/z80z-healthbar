package com.z80z99.z80zhealthbar;

import com.z80z99.z80zhealthbar.compat.CompatManager;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.ConfigWatcher;
import com.z80z99.z80zhealthbar.network.NetworkHandler;
import com.z80z99.z80zhealthbar.platform.PlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Z80ZHealthBar {
    public static final String MOD_ID = "z80zhealthbar";
    public static final String MOD_NAME = "Z80Z Health Bar";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        LOGGER.info("Initializing Z80ZHealthBar...");

        // 加载配置
        ConfigManager.loadConfig();

        // 初始化网络层
        NetworkHandler.init();

        // 客户端：启动配置热重载监视器（外部编辑 JSON 保存后自动生效，免重启调试）
        if (PlatformService.get().isClientSide()) {
            ConfigWatcher.start();
        }

        // Wave 9: 注册 V1 三个第三方兼容 overlay 到 OverlayManager；
        // 实际是否渲染由每个 CompatOverlay.shouldOverride() 检测目标 mod 已加载 + hook 配置开关决定
        CompatManager.init();

        LOGGER.info("Z80ZHealthBar initialized successfully.");
    }
}
