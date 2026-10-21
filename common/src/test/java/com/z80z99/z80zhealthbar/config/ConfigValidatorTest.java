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

    @Test
    void barStyleSegmentFieldsClamped() {
        // 分段刻度:格数钳 1-64,每格血量负值归 0(关闭)
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        assertEquals(10, cfg.barStyle.segmentCount);
        assertEquals(0, cfg.barStyle.segmentHp);
        cfg.barStyle.segmentCount = 0;
        cfg.barStyle.segmentHp = -5;
        ConfigValidator.validate(cfg);
        assertEquals(1, cfg.barStyle.segmentCount);
        assertEquals(0, cfg.barStyle.segmentHp);
        cfg.barStyle.segmentCount = 999;
        cfg.barStyle.segmentHp = 50;
        ConfigValidator.validate(cfg);
        assertEquals(64, cfg.barStyle.segmentCount);
        assertEquals(50, cfg.barStyle.segmentHp);
        assertFalse(cfg.barStyle.segmentWholeOnly);
    }

    @Test
    void hudLayoutSubElementKeysPruned() {
        // 子元素键（*.text/*.icon）不是合法的布局条目——旧编辑器读取路径误插入过,
        // 渲染端会按独立元素画出重复文本/图标 → 加载时清除,真实组件不受影响
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.hudLayout.components.put("health.text", new HudLayoutConfig.ComponentLayout());
        cfg.hudLayout.components.put("health.icon", new HudLayoutConfig.ComponentLayout());
        cfg.hudLayout.components.put("food#2.text", new HudLayoutConfig.ComponentLayout());
        // 类型键本身带 _text 后缀的原子组件必须保留（health_text ≠ health.text）
        cfg.hudLayout.components.put("health_text", new HudLayoutConfig.ComponentLayout());
        ConfigValidator.validate(cfg);
        assertFalse(cfg.hudLayout.components.containsKey("health.text"));
        assertFalse(cfg.hudLayout.components.containsKey("health.icon"));
        assertFalse(cfg.hudLayout.components.containsKey("food#2.text"));
        assertTrue(cfg.hudLayout.components.containsKey("health_text"));
        assertTrue(cfg.hudLayout.components.containsKey(HudLayoutConfig.HEALTH));
    }

    @Test
    void hudLayoutDetachedOffsetsKeepEditorRange() {
        // 拆分子件的偏移可达 ±1000（拖拽）/±500（图标）——校验不得把它们拉回 ±50,
        // 否则"放到 50px 外"的文本下次加载被静默搬回
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        var c = cfg.hudLayout.get(HudLayoutConfig.HEALTH);
        c.textOffsetX = 800;
        c.textOffsetY = -999;
        c.iconOffsetX = 400;
        c.iconOffsetY = -480;
        ConfigValidator.validate(cfg);
        assertEquals(800, c.textOffsetX);
        assertEquals(-999, c.textOffsetY);
        assertEquals(400, c.iconOffsetX);
        assertEquals(-480, c.iconOffsetY);
        // 越界仍钳到编辑器上限
        c.textOffsetX = 5000;
        c.iconOffsetY = -5000;
        ConfigValidator.validate(cfg);
        assertEquals(1000, c.textOffsetX);
        assertEquals(-500, c.iconOffsetY);
    }

    @Test
    void saturationModeClampedForBothScopes() {
        // 越界值会让编辑器的循环行从错误档位起跳（存 7 显示"关闭",点一下跳到"底部细条"）
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        cfg.hudLayout.get(HudLayoutConfig.FOOD).saturationMode = 7;
        var bar = new com.z80z99.z80zhealthbar.config.configs.OverlayConfig.BarFreePos();
        bar.saturationMode = 9;
        cfg.overlay.barFreePos.put("food", bar);
        ConfigValidator.validate(cfg);
        assertEquals(4, cfg.hudLayout.get(HudLayoutConfig.FOOD).saturationMode);
        assertEquals(4, cfg.overlay.barFreePos.get("food").saturationMode);
    }

    @Test
    void hudLayoutCopyIsFaithfulAndDoesNotResurrectDefaults() {
        // 快照/放弃依赖 copy()：预置默认组件会把用户删除的组件在"放弃"时复活,并改写堆叠顺序
        HudLayoutConfig live = new HudLayoutConfig();
        live.components.remove(HudLayoutConfig.FOOD);
        live.components.remove(HudLayoutConfig.MOUNT);
        live.components.get(HudLayoutConfig.HEALTH).barWidth = 222;
        live.panelX = 40;
        HudLayoutConfig snap = live.copy();
        assertEquals(live.components.keySet(), snap.components.keySet());
        assertFalse(snap.components.containsKey(HudLayoutConfig.FOOD));
        assertEquals(222, snap.components.get(HudLayoutConfig.HEALTH).barWidth);
        assertEquals(40, snap.panelX);
        // 深拷贝：改快照不影响源,反之亦然
        snap.components.get(HudLayoutConfig.HEALTH).barWidth = 111;
        assertEquals(222, live.components.get(HudLayoutConfig.HEALTH).barWidth);
    }

    @Test
    void hudLayoutPeekDoesNotInsert() {
        // 读取路径用 peek：不得把探查的键写进配置（此前 get() 会把 health.text 插进 map）
        HudLayoutConfig cfg = new HudLayoutConfig();
        int before = cfg.components.size();
        assertNull(cfg.peek("health.text"));
        assertNull(cfg.peek("does_not_exist"));
        assertEquals(before, cfg.components.size());
    }

    /** 自定义数据源:通道/模式钳制、重复 id 停用、非法 id 判定、参数完整性 */
    @Test
    void customSourcesAreValidated() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        var a = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource();
        a.id = "  Gun_Durability ";
        a.channel = 99;
        a.itemMode = 7;
        a.staticMax = -5f;
        a.color = "not-a-color";
        var b = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource();
        b.id = "gun_durability"; // 规范化后与 a 重复
        b.channel = 4;
        cfg.customSources.customSources.add(a);
        cfg.customSources.customSources.add(b);

        ConfigValidator.validate(cfg);

        assertEquals("gun_durability", a.id, "id 应去空白并转小写");
        assertEquals(4, a.channel, "channel 钳制 0..4");
        assertEquals(2, a.itemMode, "itemMode 钳制 0..2");
        assertEquals(0f, a.staticMax, "staticMax 负数归零");
        assertEquals("", a.color, "非法颜色清空");
        assertFalse(b.enabled, "重复 id 的后续源应停用");

        // paramsComplete:通道 4 原版耐久无需方法名;NBT 需要 key
        var item = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource();
        item.id = "dur"; item.channel = 4; item.itemMode = 0;
        assertTrue(item.paramsComplete(), "原版耐久通道无需额外参数");
        item.itemMode = 1;
        assertFalse(item.paramsComplete(), "NBT 模式缺 key 应判不完整");
        item.readMethod = "Damage";
        assertTrue(item.paramsComplete());

        var cap = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource();
        cap.id = "x"; cap.channel = 0; cap.holderClass = "a.B"; cap.readMethod = "get";
        assertFalse(cap.paramsComplete(), "Capability 缺 token/接口应判不完整");
        cap.holderField = "TOKEN"; cap.ifaceClass = "a.I";
        assertTrue(cap.paramsComplete());

        // 非法 id（大写/空格/超长）判不完整
        var bad = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource();
        bad.id = "Bad Id";
        assertFalse(bad.paramsComplete());
    }

    /** 审计 S1:应用预设不得清掉用户的自定义数据源组件(compat_custom_*) */
    @Test
    void applyPresetKeepsCustomDatasourceComponents() {
        HudLayoutConfig l = new HudLayoutConfig();
        String k = l.addInstance("compat_custom_gundur");
        assertNotNull(k);
        l.applyPreset("CLASSIC");
        assertTrue(l.components.containsKey(k), "compat_custom_* 必须被预设保留");
    }

    /** 审计 S2:数据源数量上限按索引修剪(旧实现 34+ 渐进丢失),且 _max 后缀被截断(S5) */
    @Test
    void customSourcesTrimAndMaxSuffix() {
        Z80ZHealthBarConfig cfg = new Z80ZHealthBarConfig();
        for (int i = 0; i < 34; i++) {
            var s = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource();
            s.id = "src" + i;
            s.channel = 4;
            cfg.customSources.customSources.add(s);
        }
        var m = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource();
        m.id = "foo_max"; m.channel = 4;
        cfg.customSources.customSources.add(m);

        ConfigValidator.validate(cfg);

        assertEquals(32, cfg.customSources.customSources.size(), "上限应精确为 32");
        // 34 个 src + foo_max 共 35 项 -> 保留前 32,foo_max(第 35 个)被修剪;
        // 单独验证 _max 截断逻辑
        Z80ZHealthBarConfig c2 = new Z80ZHealthBarConfig();
        var only = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource();
        only.id = "foo_max"; only.channel = 4;
        c2.customSources.customSources.add(only);
        ConfigValidator.validate(c2);
        assertEquals("foo", c2.customSources.customSources.get(0).id, "_max 后缀应被截断");
    }

    /** 通道 3 方法链:valueMethod 参与读取;paramsComplete 仍只要求 readMethod */
    @Test
    void channel3ChainParams() {
        var s = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource();
        s.id = "sat"; s.channel = 3; s.readMethod = "getFoodData";
        assertTrue(s.paramsComplete());
        s.valueMethod = "getSaturationLevel";
        assertTrue(s.paramsComplete());
    }
}
