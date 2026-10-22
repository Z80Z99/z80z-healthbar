package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 创造/旁观隐藏状态条的规则测试（纯函数,不依赖 MC 运行时）。
 *
 * <p>集合对齐原版：{@code canHurtPlayer()}/{@code hasExperience()} 只在生存/冒险为 true,
 * 因此隐藏生命/饥饿/氧气/护甲/经验;坐骑生命、快捷栏、信息类组件保留。
 */
class HudVisibilityTest {

    @Test
    void creativeAndSpectatorHideSurvivalLikeDoesNot() {
        assertTrue(HudVisibility.hidesFor(HudVisibility.Mode.CREATIVE));
        assertTrue(HudVisibility.hidesFor(HudVisibility.Mode.SPECTATOR));
        assertFalse(HudVisibility.hidesFor(HudVisibility.Mode.SURVIVAL_LIKE));
        assertFalse(HudVisibility.hidesFor(HudVisibility.Mode.UNKNOWN), "unknown mode must not hide");
    }

    @Test
    void statusTypesCoverVanillaFiveAndCompatRows() {
        for (String t : new String[]{HudLayoutConfig.HEALTH, HudLayoutConfig.FOOD,
                HudLayoutConfig.AIR, HudLayoutConfig.ARMOR, HudLayoutConfig.EXPERIENCE}) {
            assertTrue(HudVisibility.isStatusType(t), t + " is a status bar");
        }
        // 兼容/数据源条（口渴等）
        assertTrue(HudVisibility.isStatusType("compat_thirst"));
        assertTrue(HudVisibility.isStatusType("compat_saturation"));
        assertTrue(HudVisibility.isStatusType("compat_custom_mana"));
        assertTrue(HudVisibility.isStatusType(HudLayoutConfig.COMPAT));
        assertTrue(HudVisibility.isStatusType("saturation_bar"));
        // 原子子组件（独立文本/图标）跟随父类型
        assertTrue(HudVisibility.isStatusType("health_icon"));
        assertTrue(HudVisibility.isStatusType("compat_thirst_text"));
    }

    @Test
    void mountHotbarAndInfoWidgetsAreKept() {
        // 原版 renderVehicleHealth 不受 canHurtPlayer 控制
        assertFalse(HudVisibility.isStatusType(HudLayoutConfig.MOUNT));
        // 快捷栏任意模式都显示
        assertFalse(HudVisibility.isStatusType(HudLayoutConfig.HOTBAR));
        // 信息类/自由文本
        assertFalse(HudVisibility.isStatusType("coords"));
        assertFalse(HudVisibility.isStatusType("fps"));
        assertFalse(HudVisibility.isStatusType("ping"));
        assertFalse(HudVisibility.isStatusType("gametime"));
        assertFalse(HudVisibility.isStatusType("text"));
        assertFalse(HudVisibility.isStatusType(null));
        assertFalse(HudVisibility.isStatusType(""));
    }
}
