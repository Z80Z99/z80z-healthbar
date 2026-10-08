package com.z80z99.z80zhealthbar.config;

import com.z80z99.z80zhealthbar.config.configs.*;
import com.z80z99.z80zhealthbar.mobdisplay.EntityHealthStyle;
import com.z80z99.z80zhealthbar.util.ColorHelper;

/**
 * 配置校验（任务书 10：数值范围校验，非法值自动回退）。
 * 纯 JVM 逻辑，不依赖 MC 注册表 —— 可被单元测试直接覆盖。
 */
public final class ConfigValidator {

    private ConfigValidator() {}

    public static void validate(Z80ZHealthBarConfig cfg) {
        if (cfg.overlay == null) cfg.overlay = new OverlayConfig();
        if (cfg.barStyle == null) cfg.barStyle = new BarStyleConfig();
        if (cfg.plaqueStyle == null) cfg.plaqueStyle = new PlaqueStyleConfig();
        if (cfg.styleA == null) cfg.styleA = new StyleAConfig();
        if (cfg.entityAddons == null) cfg.entityAddons = new EntityAddonsConfig();
        if (cfg.hudLayout == null) cfg.hudLayout = new HudLayoutConfig();
        if (cfg.visibility == null) cfg.visibility = new VisibilityConfig();
        if (cfg.colors == null) cfg.colors = new ColorConfig();
        if (cfg.compat == null) cfg.compat = new CompatConfig();
        if (cfg.damagePopup == null) cfg.damagePopup = new DamagePopupConfig();
        if (cfg.dynamicFx == null) cfg.dynamicFx = new DynamicFxConfig();

        validateOverlay(cfg.overlay);
        validateHudLayout(cfg.hudLayout);
        validateBarStyle(cfg.barStyle);
        validatePlaqueStyle(cfg.plaqueStyle);
        validateStyleA(cfg.styleA);
        validateEntityAddons(cfg.entityAddons);
        validateVisibility(cfg.visibility);
        validateColors(cfg.colors);
        validateDamagePopup(cfg.damagePopup);
        validateDynamicFx(cfg.dynamicFx);
        normalizeEntityStyle(cfg);
    }

    private static void validateDynamicFx(DynamicFxConfig c) {
        if (ColorHelper.parseColorSafe(c.ghostColor) == null) c.ghostColor = "#80FFFFFF";
    }

    private static void validateDamagePopup(DamagePopupConfig c) {
        c.theme = c.themeParsed();
        c.motion = c.motionParsed();
        c.spawnOrigin = c.spawnOriginParsed();
        c.animRisePx = clamp(c.animRisePx, 0, 80);
        c.animPunchPercent = clamp(c.animPunchPercent, 0, 150);
        c.animFadeStartPercent = clamp(c.animFadeStartPercent, 30, 95);
        c.animTiltDegrees = clamp(c.animTiltDegrees, -45, 45);
        c.animDriftPx = clamp(c.animDriftPx, 0, 24);
        c.scale = clamp(c.scale, 0.5, 2.0);
        c.offsetY = clamp(c.offsetY, -2, 4);
        c.lifetimeTicks = clamp(c.lifetimeTicks, 10, 60);
        c.maxPerEntity = clamp(c.maxPerEntity, 1, 8);
        c.mergeWindowTicks = clamp(c.mergeWindowTicks, 0, 20);
        c.launchAngleDegrees = clamp(c.launchAngleDegrees, 0, 180);
        c.colorHealth = fixColor(c.colorHealth, "#FFFFFFFF");
        c.colorBigHit = fixColor(c.colorBigHit, "#FFFFD24A");
        c.colorAbsorbed = fixColor(c.colorAbsorbed, "#FF4FC8FF");
        c.colorKill = fixColor(c.colorKill, "#FFFF1744");
        c.colorFire = fixColor(c.colorFire, "#FFFFAB40");
        c.colorMagic = fixColor(c.colorMagic, "#FFB388FF");
        c.colorExplosion = fixColor(c.colorExplosion, "#FFFF5722");
        c.colorProjectile = fixColor(c.colorProjectile, "#FF7FB8E8");
        c.colorFall = fixColor(c.colorFall, "#FFC8A678");
    }

    /** 非法颜色字符串回退默认值 */
    private static String fixColor(String value, String fallback) {
        return ColorHelper.parseColorSafe(value) == null ? fallback : value;
    }

    private static void validateOverlay(OverlayConfig c) {
        try {
            com.z80z99.z80zhealthbar.overlay.HudStyle.valueOf(c.hudStyle);
        } catch (IllegalArgumentException | NullPointerException e) {
            c.hudStyle = com.z80z99.z80zhealthbar.overlay.HudStyle.ASTEORBAR.name();
        }
        c.overlayLayoutStyle = clamp(c.overlayLayoutStyle, 0, 8);
        c.overlayTextScale = clamp(c.overlayTextScale, 0.25, 4.0);
        c.overlayBarInnerHeight = clamp(c.overlayBarInnerHeight, 1, 12);
        c.overlayBarVerticalMargin = clamp(c.overlayBarVerticalMargin, 0, 16);
        c.overlayBarTextOffsetY = clamp(c.overlayBarTextOffsetY, -50, 50);
        if (c.barFreePos != null) { // 长条自由摆放坐标（编辑器拖拽写回）
            c.barFreePos.values().removeIf(java.util.Objects::isNull);
            for (var p : c.barFreePos.values()) {
                p.x = clamp(p.x, 0, 10000);
                p.y = clamp(p.y, 0, 10000);
                p.saturationMode = clamp(p.saturationMode, 0, 4); // 同组件级:防循环行从越界值起跳
            }
        }
        c.fullFoodLevelValue = clamp(c.fullFoodLevelValue, 0, 40);
        c.fullSaturationValue = clamp(c.fullSaturationValue, 0, 40);
        c.fullArmorValue = clamp(c.fullArmorValue, 0, 100);
        c.fullHealthValue = clamp(c.fullHealthValue, 0, 100000);
        c.hideUnchangingBarAfterSeconds = clamp(c.hideUnchangingBarAfterSeconds, 0, 600);
        c.lowHealthRate = clamp(c.lowHealthRate, 0.05, 0.95);
        c.absorptionMode = clamp(c.absorptionMode, 0, 1); // 旧值 2(BOUND 未实现)合并为 1(仅图标)
        c.cornerBarLength = clamp(c.cornerBarLength, 20, 400);
        c.cornerHorizontalPadding = clamp(c.cornerHorizontalPadding, 0, 50);
        c.cornerVerticalPadding = clamp(c.cornerVerticalPadding, 0, 50);
    }

    private static void validateBarStyle(BarStyleConfig c) {
        c.barScale = clamp(c.barScale, 0.1, 4.0);
        c.barOffsetY = clamp(c.barOffsetY, -4, 8);
        c.barHalfWidth = clamp(c.barHalfWidth, 0, 80);
        c.barHalfHeight = clamp(c.barHalfHeight, 1, 12);
        c.barBoundWidth = clamp(c.barBoundWidth, 0, 4);
        c.barTextScale = clamp(c.barTextScale, 0.1, 2.0);
        c.barTextOffsetX = clamp(c.barTextOffsetX, -50, 50);
        c.barTextOffsetY = clamp(c.barTextOffsetY, -50, 50);
        c.barAlpha = clamp(c.barAlpha, 0, 255);
        c.barVariant = clamp(c.barVariant, 0, 3);
        c.segmentCount = (int) clamp(c.segmentCount, 1, 64);
        c.segmentHp = (int) clamp(c.segmentHp, 0, 10000);
        c.barPixelOffsetX = clamp(c.barPixelOffsetX, -200, 200);
        c.barPixelOffsetY = clamp(c.barPixelOffsetY, -200, 200);
    }

    private static void validatePlaqueStyle(PlaqueStyleConfig c) {
        c.plaqueScale = clamp(c.plaqueScale, 0.05, 2.0);
        c.maxPlaqueRowWidth = clamp(c.maxPlaqueRowWidth, 0, 500);
        c.maxRenderDistance = clamp(c.maxRenderDistance, 4, 256);
        // 旧垂直偏移（heightOffset，正值向上）一次性迁移到 yOffset（正值向下）
        if (c.heightOffset != 0) {
            c.yOffset -= c.heightOffset;
            c.heightOffset = 0;
        }
        c.xOffset = clamp(c.xOffset, -300, 300);
        c.yOffset = clamp(c.yOffset, -120, 120);
        c.opacity = clamp(c.opacity, 0, 255);
        c.textScale = clamp(c.textScale, 0.25, 3.0);
        c.textOffsetX = clamp(c.textOffsetX, -60, 60);
        c.textOffsetY = clamp(c.textOffsetY, -60, 60);
    }

    private static void validateStyleA(StyleAConfig c) {
        // 旧配置一次性迁移到"形状 + 配色"两轴（textureMode=V2 哨兵保证幂等）：
        //  a) ORIGINAL 0-3（经典宽条/双半心行/迷你小条/心形条带）→ 形状（经典→2，心形族→1，迷你→2）
        //  b) 合并序列 0-5（四色外框条×4 / 心形行 / 经典宽条）→ 颜色拆到 colorVariant
        if (!"V2".equals(c.textureMode)) {
            if (c.isOriginalTexture()) {
                int old = Math.max(0, Math.min(3, c.barType));
                c.barType = (old == 1 || old == 3) ? 1 : 2;
            } else if (c.barType <= 3) {
                c.colorVariant = c.barType;
                c.barType = 0;
            } else if (c.barType == 4) {
                c.barType = 1;
            } else if (c.barType == 5) {
                c.barType = 2;
            }
            c.textureMode = "V2";
        }
        c.barType = clamp(c.barType, 0, c.shapeCount() - 1);
        c.colorVariant = clamp(c.colorVariant, 0, 3);
        c.offsetX = clamp(c.offsetX, -256, 256);
        c.offsetY = clamp(c.offsetY, -256, 256);
        c.opacity = clamp(c.opacity, 0, 255);
        c.numOffsetX = clamp(c.numOffsetX, -100, 100);
        c.numOffsetY = clamp(c.numOffsetY, -100, 100);
        c.heightOffset = clamp(c.heightOffset, -4, 8);
        c.scaleName = clamp(c.scaleName, 0.25, 4.0);
        c.scaleBar = clamp(c.scaleBar, 0.25, 4.0);
        c.scaleNums = clamp(c.scaleNums, 0.25, 4.0);
        // 新字段在旧配置中缺失（Gson 不跑字段初始化器）→ 0 视为 1.0（不拉伸）
        c.scaleBarWidth = c.scaleBarWidth <= 0 ? 1.0 : clamp(c.scaleBarWidth, 0.25, 4.0);
        c.scaleBarHeight = c.scaleBarHeight <= 0 ? 1.0 : clamp(c.scaleBarHeight, 0.25, 4.0);
    }

    private static void validateEntityAddons(EntityAddonsConfig c) {
        c.rowGap = clamp(c.rowGap, 0, 16);
        // 旧配置缺失（≤0）→ 1.0（基准大小,同 scaleBar 的 Gson 补默认约定）
        c.addonScale = c.addonScale <= 0 ? 1.0 : clamp(c.addonScale, 0.25, 4.0);
    }

    private static void validateHudLayout(HudLayoutConfig c) {
        c.panelX = clamp(c.panelX, -1, 10000);
        c.panelY = clamp(c.panelY, -1, 10000);
        if (c.components == null || c.components.isEmpty()) {
            c.components = HudLayoutConfig.defaultComponents();
            return;
        }
        // 子元素键（*.text / *.icon）——拆分数据存在主组件字段,map 中不存在合法的独立键。
        // 历史版本编辑器的读取路径经 get() 误插入过这类键 → 渲染成重复文本/图标,加载时清除
        c.components.keySet().removeIf(k -> k != null && (k.endsWith(".text") || k.endsWith(".icon")));
        for (HudLayoutConfig.ComponentLayout cl : c.components.values()) {
            if (cl == null) continue;
            cl.scale = clamp(cl.scale, 0.5, 2.0);
            cl.spacing = clamp(cl.spacing, 0, 40);
            cl.barWidth = clamp(cl.barWidth, 40, 400);
            cl.offsetX = clamp(cl.offsetX, -1000, 1000);
            cl.offsetY = clamp(cl.offsetY, -1000, 1000);
            cl.barHeight = clamp(cl.barHeight, 5, 16);
            // 与编辑器可达范围一致（拖拽 ±1000 / 步进 ±999 / 图标 ±500）——此前收紧到 ±50,
            // 拆分子件放到 50px 外保存后,下次加载被静默拉回
            cl.textOffsetX = clamp(cl.textOffsetX, -1000, 1000);
            cl.textOffsetY = clamp(cl.textOffsetY, -1000, 1000);
            // 旧配置缺少新字段时 Gson 留下 0（不跑字段初始化器）→ 归位默认 1.0
            cl.textScale = cl.textScale <= 0 ? 1.0 : clamp(cl.textScale, 0.25, 3.0);
            cl.iconScale = cl.iconScale <= 0 ? 1.0 : clamp(cl.iconScale, 0.25, 3.0);
            cl.iconOffsetX = clamp(cl.iconOffsetX, -500, 500);
            cl.iconOffsetY = clamp(cl.iconOffsetY, -500, 500);
            cl.opacity = clamp(cl.opacity, 0, 100);
            cl.idleFadeSecs = clamp(cl.idleFadeSecs, 0, 600);
            cl.rotation = clamp(cl.rotation, -180, 180);
            // 饱和度显示方式（0=覆盖 1=右追加 2=顶部细条 3=底部细条 4=关闭）：不钳制时
            // 编辑器的循环行从越界值起跳（存 7 显示"关闭",点一下跳到"底部细条"）
            cl.saturationMode = clamp(cl.saturationMode, 0, 4);
        }
    }

    private static void validateVisibility(VisibilityConfig c) {
        c.maxDistance = clamp(c.maxDistance, 4, 256);
        c.maxConcurrentDisplays = clamp(c.maxConcurrentDisplays, 0, 200);
        c.fadeInTicks = clamp(c.fadeInTicks, 0, 40);
    }

    private static void validateColors(ColorConfig c) {
        ColorConfig def = new ColorConfig();
        java.lang.reflect.Field[] fields = ColorConfig.class.getFields();
        for (java.lang.reflect.Field f : fields) {
            if (f.getType() != String.class) continue;
            try {
                String value = (String) f.get(c);
                if (value == null || ColorHelper.parseColorSafe(value) == null) {
                    f.set(c, f.get(def));
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static void normalizeEntityStyle(Z80ZHealthBarConfig cfg) {
        try {
            EntityHealthStyle.valueOf(cfg.entityStyle);
        } catch (IllegalArgumentException | NullPointerException e) {
            cfg.entityStyle = EntityHealthStyle.ASTEORBAR.name();
        }
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
