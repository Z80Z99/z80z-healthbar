package com.z80z99.z80zhealthbar.compat;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import com.z80z99.z80zhealthbar.overlay.HudStyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 兼容 HUD 接管决策逻辑测试（纯配置层,不触 MC/目标 MOD 类） */
class CompatHudTakeoverTest {

    private final com.z80z99.z80zhealthbar.config.Z80ZHealthBarConfig cfg = ConfigManager.getConfig();
    // 快照被修改的字段（避免污染同 JVM 其它测试）
    private final boolean hookThirst = cfg.compat.hookThirstWasTaken;
    private final boolean hookParcool = cfg.compat.hookParcool;
    private final boolean tkThirst = cfg.compat.takeoverThirst;
    private final boolean tkParcool = cfg.compat.takeoverParcool;
    private final String hudStyle = cfg.overlay.hudStyle;
    private final int layoutStyle = cfg.overlay.overlayLayoutStyle;
    private final boolean enableOverlay = cfg.overlay.enableOverlay;

    @AfterEach
    void restore() {
        cfg.compat.hookThirstWasTaken = hookThirst;
        cfg.compat.hookParcool = hookParcool;
        cfg.compat.takeoverThirst = tkThirst;
        cfg.compat.takeoverParcool = tkParcool;
        cfg.overlay.hudStyle = hudStyle;
        cfg.overlay.overlayLayoutStyle = layoutStyle;
        cfg.overlay.enableOverlay = enableOverlay;
        CompatHudTakeover.onConfigReload();
    }

    @Test
    void overlayIdMapping() {
        assertEquals("thirst", CompatHudTakeover.adapterForOverlay("thirst", "thirst_level"));
        assertEquals("parcool", CompatHudTakeover.adapterForOverlay("parcool", "hud.stamina.host"));
        assertNull(CompatHudTakeover.adapterForOverlay("thirst", "other"));
        assertNull(CompatHudTakeover.adapterForOverlay("minecraft", "food_level"));
        assertNull(CompatHudTakeover.adapterForOverlay("other", "thirst_level"));
    }

    @Test
    void componentKeyMapping() {
        assertEquals("compat_thirst", CompatHudTakeover.componentKey("thirst"));
        assertEquals("compat_stamina", CompatHudTakeover.componentKey("parcool"));
    }

    @Test
    void takeoverRequiresHookAndSwitchBothOn() {
        cfg.compat.hookThirstWasTaken = true;
        cfg.compat.takeoverThirst = false;
        assertFalse(CompatHudTakeover.takeoverEnabled("thirst"), "switch off -> no takeover");
        cfg.compat.takeoverThirst = true;
        assertTrue(CompatHudTakeover.takeoverEnabled("thirst"));
        cfg.compat.hookThirstWasTaken = false;
        assertFalse(CompatHudTakeover.takeoverEnabled("thirst"), "hook off -> no takeover");
    }

    @Test
    void rendersStatFollowsHudStyle() {
        // 长条模式:布局非 0 → 渲染兼容行;布局 0 = 关闭本模式绘制 → 不渲染
        cfg.overlay.hudStyle = HudStyle.ASTEORBAR.name();
        cfg.overlay.overlayLayoutStyle = 2;
        cfg.overlay.enableOverlay = true;
        assertTrue(CompatHudTakeover.rendersStat("thirst"));
        cfg.overlay.overlayLayoutStyle = 0;
        assertFalse(CompatHudTakeover.rendersStat("thirst"), "layout 0 -> no rows -> no takeover");
        // 总开关关 → 不渲染
        cfg.overlay.overlayLayoutStyle = 2;
        cfg.overlay.enableOverlay = false;
        assertFalse(CompatHudTakeover.rendersStat("thirst"));
        // VANILLA 模式不绘制自定义内容 → 不渲染（安全网:不能接管）
        cfg.overlay.enableOverlay = true;
        cfg.overlay.hudStyle = HudStyle.VANILLA.name();
        assertFalse(CompatHudTakeover.rendersStat("thirst"));
    }

    @Test
    void rendersStatCustomModeFollowsComponent() {
        cfg.overlay.hudStyle = HudStyle.CUSTOM.name();
        cfg.overlay.enableOverlay = true;
        String key = CompatHudTakeover.componentKey("thirst");
        var hadComponent = cfg.hudLayout.peek(key) != null;
        var saved = cfg.hudLayout.peek(key);
        try {
            // 组件缺失 → 不渲染（保证 tickEnsure 创建前的空窗期不接管）
            cfg.hudLayout.components.remove(key);
            assertFalse(CompatHudTakeover.rendersStat("thirst"));
            // 组件 BAR → 渲染
            var c = new HudLayoutConfig.ComponentLayout();
            c.type = key;
            c.mode = HudLayoutConfig.ComponentMode.BAR.name();
            cfg.hudLayout.components.put(key, c);
            assertTrue(CompatHudTakeover.rendersStat("thirst"));
            // 组件 OFF → 不渲染
            c.mode = HudLayoutConfig.ComponentMode.OFF.name();
            assertFalse(CompatHudTakeover.rendersStat("thirst"));
        } finally {
            if (hadComponent && saved != null) cfg.hudLayout.components.put(key, saved);
            else cfg.hudLayout.components.remove(key);
        }
    }
}
