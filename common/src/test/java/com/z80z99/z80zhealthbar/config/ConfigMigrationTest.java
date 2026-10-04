package com.z80z99.z80zhealthbar.config;

import com.z80z99.z80zhealthbar.mobdisplay.EntityHealthStyle;
import com.z80z99.z80zhealthbar.mobdisplay.MobDisplayMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 旧配置迁移测试（任务书 10：旧配置尽可能迁移而非丢弃） */
class ConfigMigrationTest {

    @Test
    void v1MobDisplayModeMigrates() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.configVersion = 1;
        cfg.mobDisplayMode = MobDisplayMode.BARS;
        assertTrue(ConfigManager.migrate(cfg));
        assertEquals(EntityHealthStyle.ASTEORBAR.name(), cfg.entityStyle);
        assertEquals(3, cfg.configVersion);
        assertNull(cfg.mobDisplayMode);
    }

    @Test
    void eachLegacyModeMapsCorrectly() {
        assertEquals(EntityHealthStyle.ASTEORBAR, EntityHealthStyle.fromLegacy(MobDisplayMode.BARS));
        assertEquals(EntityHealthStyle.MOBPLAQUES, EntityHealthStyle.fromLegacy(MobDisplayMode.PLAQUES));
        // BOTH 属旧式重复绘制 → 迁移到 C（附加组件需玩家主动开启）
        assertEquals(EntityHealthStyle.ASTEORBAR, EntityHealthStyle.fromLegacy(MobDisplayMode.BOTH));
        assertEquals(EntityHealthStyle.ASTEORBAR, EntityHealthStyle.fromLegacy(null));
    }

    @Test
    void v2ConfigWithoutLegacyFieldIsUntouched() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.entityStyle = "MOBPLAQUES";
        assertFalse(ConfigManager.migrate(cfg));
        assertEquals("MOBPLAQUES", cfg.entityStyle);
        assertNull(cfg.mobDisplayMode);
    }

    @Test
    void legacyFieldAlwaysClearedEvenAtV2() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.mobDisplayMode = MobDisplayMode.PLAQUES;
        cfg.entityStyle = "";
        assertTrue(ConfigManager.migrate(cfg));
        assertEquals(EntityHealthStyle.MOBPLAQUES.name(), cfg.entityStyle);
        assertNull(cfg.mobDisplayMode);
    }
}
