package com.z80z99.z80zhealthbar.compat;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.overlay.HudRenderer;
import com.z80z99.z80zhealthbar.overlay.OverlayManager;
import com.z80z99.z80zhealthbar.overlay.OverlayPosition;
import com.z80z99.z80zhealthbar.overlay.parts.compat.CompatStatsOverlay;

/**
 * 兼容管理器：注册统一兼容状态条到 8 种 ASTEORBAR 布局。
 * 数据层见 {@link CompatAdapters}（CompatibilityAdapter 注册表）；
 * 渲染期由各适配器的 isAvailable/hook 自动 gate，未安装目标 MOD 时零渲染零反射热点。
 */
public final class CompatManager {
    private static boolean initialized = false;
    private static final CompatStatsOverlay STATS_OVERLAY = new CompatStatsOverlay();

    private CompatManager() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        // 锚定在玩家血条之后：布局 1/2（物品栏上方）下 compat 走"其他组件"分支，
        // 跟随血条堆叠而非跳到列表头部占掉顶部槽位
        OverlayManager.registerOverlay(STATS_OVERLAY, HudRenderer.PLAYER_HEALTH,
                OverlayPosition.CENTER, 1);

        Z80ZHealthBar.LOGGER.info("[Compat] adapters registered: {}",
                CompatAdapters.all().stream().map(CompatibilityAdapter::id).toList());
    }

    public static CompatStatsOverlay statsOverlay() {
        return STATS_OVERLAY;
    }
}
