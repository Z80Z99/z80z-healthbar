package com.z80z99.z80zhealthbar.config;

import com.z80z99.z80zhealthbar.config.configs.ColorConfig;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import com.z80z99.z80zhealthbar.mobdisplay.EntityHealthStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 配置校验测试（任务书 10：数值范围校验、非法值回退） */
class ConfigValidatorTest {

    private Z80ZHealthBarConfig brokenConfig() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.overlay.overlayLayoutStyle = 99;
        cfg.overlay.overlayTextScale = -3;
        cfg.overlay.lowHealthRate = 5;
        cfg.barStyle.barScale = -1;
        cfg.barStyle.barAlpha = 9999;
        cfg.plaqueStyle.plaqueScale = 0;
        cfg.styleA.barType = 17;
        cfg.styleA.scaleName = 100;
        cfg.visibility.maxDistance = -5;
        cfg.visibility.maxConcurrentDisplays = -2;
        cfg.hudLayout.get(HudLayoutConfig.HEALTH).scale = 99;
        cfg.hudLayout.get(HudLayoutConfig.HEALTH).barWidth = 1;
        cfg.hudLayout.components.put("weird", null);
        return cfg;
    }

    @Test
    void stretchFieldsDefaultWhenMissing() {
        // Old configs deserialized by Gson leave new fields at 0 (field initializers skipped)
        // -> validator must restore 1.0 (no stretch).
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.styleA.scaleBarWidth = 0;
        cfg.styleA.scaleBarHeight = 0;
        ConfigValidator.validate(cfg);
        assertEquals(1.0, cfg.styleA.scaleBarWidth);
        assertEquals(1.0, cfg.styleA.scaleBarHeight);
    }

    @Test
    void stretchFieldsClampedAndPreserved() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.styleA.scaleBarWidth = 99;   // above range -> 4.0
        cfg.styleA.scaleBarHeight = 0.1; // below range -> 0.25
        ConfigValidator.validate(cfg);
        assertEquals(4.0, cfg.styleA.scaleBarWidth);
        assertEquals(0.25, cfg.styleA.scaleBarHeight);
        // Legal values preserved (single-axis stretch must survive validation)
        Z80ZHealthBarConfig ok = new Z80ZHealthBarConfig();
        ok.styleA.scaleBarWidth = 1.8;
        ok.styleA.scaleBarHeight = 0.6;
        ConfigValidator.validate(ok);
        assertEquals(1.8, ok.styleA.scaleBarWidth);
        assertEquals(0.6, ok.styleA.scaleBarHeight);
    }

    @Test
    void clampsOutOfRangeNumbers() {
        Z80ZHealthBarConfig cfg = brokenConfig();
        ConfigValidator.validate(cfg);
        assertTrue(cfg.overlay.overlayLayoutStyle >= 0 && cfg.overlay.overlayLayoutStyle <= 8);
        assertTrue(cfg.overlay.overlayTextScale >= 0.25);
        assertTrue(cfg.overlay.lowHealthRate >= 0.05 && cfg.overlay.lowHealthRate <= 0.95);
        assertTrue(cfg.barStyle.barScale >= 0.1);
        assertTrue(cfg.barStyle.barAlpha <= 255);
        assertTrue(cfg.plaqueStyle.plaqueScale >= 0.05);
        assertTrue(cfg.styleA.barType <= 12); // ORIGINAL 模式支持原版 13 型（0-12）
        assertTrue(cfg.styleA.scaleName <= 4.0);
        assertTrue(cfg.visibility.maxDistance >= 4);
        assertTrue(cfg.visibility.maxConcurrentDisplays >= 0);
    }

    @Test
    void hudLayoutComponentClampedAndNullsSurvive() {
        Z80ZHealthBarConfig cfg = brokenConfig();
        ConfigValidator.validate(cfg);
        var cl = cfg.hudLayout.get(HudLayoutConfig.HEALTH);
        assertTrue(cl.scale <= 2.0);
        assertTrue(cl.barWidth >= 40);
        // null 组件条目不抛异常
        assertDoesNotThrow(() -> ConfigValidator.validate(cfg));
    }

    @Test
    void invalidColorsFallBackToDefaults() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.colors.healthNormal = "not-a-color";
        cfg.colors.absorption = "#12345";       // 长度非法
        cfg.colors.foodEmpty = null;             // null 非法
        ColorConfig def = new ColorConfig();
        ConfigValidator.validate(cfg);
        assertEquals(def.healthNormal, cfg.colors.healthNormal);
        assertEquals(def.absorption, cfg.colors.absorption);
        assertEquals(def.foodEmpty, cfg.colors.foodEmpty);
    }

    @Test
    void invalidEnumStringsFallBack() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.entityStyle = "GARBAGE";
        cfg.overlay.hudStyle = "NOT_A_STYLE";
        cfg.damagePopup.theme = "NOT_A_THEME";
        ConfigValidator.validate(cfg);
        assertEquals(EntityHealthStyle.ASTEORBAR, cfg.entityStyleParsed());
        assertEquals("ASTEORBAR", cfg.overlay.hudStyle);
        assertEquals("APEX", cfg.damagePopup.theme);
    }

    @Test
    void nullSectionsRecreated() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.overlay = null;
        cfg.visibility = null;
        cfg.hudLayout = null;
        cfg.damagePopup = null;
        assertDoesNotThrow(() -> ConfigValidator.validate(cfg));
        assertNotNull(cfg.overlay);
        assertNotNull(cfg.visibility);
        assertNotNull(cfg.hudLayout);
        assertNotNull(cfg.damagePopup);
    }

    @Test
    void damagePopupClampedAndColorsFixed() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.damagePopup.scale = 9;
        cfg.damagePopup.lifetimeTicks = 1000;
        cfg.damagePopup.maxPerEntity = 0;
        cfg.damagePopup.mergeWindowTicks = -5;
        cfg.damagePopup.launchAngleDegrees = 999;
        cfg.damagePopup.colorHealth = "bogus";
        ConfigValidator.validate(cfg);
        assertTrue(cfg.damagePopup.scale <= 2.0);
        assertTrue(cfg.damagePopup.lifetimeTicks >= 10 && cfg.damagePopup.lifetimeTicks <= 60);
        assertTrue(cfg.damagePopup.maxPerEntity >= 1);
        assertEquals(0, cfg.damagePopup.mergeWindowTicks);
        assertEquals(180.0, cfg.damagePopup.launchAngleDegrees);
        assertEquals(new com.z80z99.z80zhealthbar.config.configs.DamagePopupConfig().colorHealth,
                cfg.damagePopup.colorHealth);
    }

    @Test
    void damagePopupLaunchAngleDefaultAndFloor() {
        // 缺省值应为随机散布(60°),负值归 0(垂直向上=旧行为)
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        assertEquals(60.0, cfg.damagePopup.launchAngleDegrees);
        cfg.damagePopup.launchAngleDegrees = -30;
        ConfigValidator.validate(cfg);
        assertEquals(0.0, cfg.damagePopup.launchAngleDegrees);
    }
}
