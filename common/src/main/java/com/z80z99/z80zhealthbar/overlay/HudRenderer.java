package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import com.z80z99.z80zhealthbar.overlay.parts.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/** 玩家 HUD 渲染的入口点，由两个平台的事件处理器调用 */
public final class HudRenderer {
    // 覆盖层实例
    public static final MainOverlay MAIN = new MainOverlay();
    public static final PlayerHealthOverlay PLAYER_HEALTH = new PlayerHealthOverlay();
    public static final FoodLevelOverlay FOOD_LEVEL = new FoodLevelOverlay();
    public static final AirLevelOverlay AIR_LEVEL = new AirLevelOverlay();
    public static final ExperienceBarOverlay EXPERIENCE_BAR = new ExperienceBarOverlay();
    public static final ArmorLevelOverlay ARMOR_LEVEL = new ArmorLevelOverlay();
    public static final MountHealthOverlay MOUNT_HEALTH = new MountHealthOverlay();

    private static RenderGui renderGui = null;

    /** 确保布局表已初始化（OverlayManager.init 按 8 种布局各自填充，这里不得再逐个
     *  registerOverlay —— 否则"先删后头插"会把所有布局坍缩成同一个倒序列表） */
    public static void ensureOrderRegistered() {
        OverlayManager.init();
    }

    /** Forge 平台调用 - 禁用原版覆盖层 */
    public static void onPreRender(Gui gui) {
        ensureOrderRegistered();
        OverlayManager.reset();
        OverlayManager.clearStringRenders();
        renderGui = new RenderGui(gui);
    }

    /** 渲染玩家 HUD - 由平台事件处理器调用 */
    public static void render(GuiGraphics graphics, float partialTick) {
        EntityDebugOverlay.render(graphics);
        // 准星命中标记独立于 overlay.enableOverlay（伤害跳字自身的开关控制）
        com.z80z99.z80zhealthbar.mobdisplay.DamagePopupRenderer.renderHitMarker(graphics);
        var cfg = ConfigManager.getConfig().overlay;
        if (!cfg.enableOverlay) return;
        HudStyle style = hudStyleParsed();
        if (style == HudStyle.VANILLA) return;

        // CUSTOM：独立布局系统（与编辑器共用 HudLayoutSolver）
        if (style == HudStyle.CUSTOM) {
            CustomHudRenderer.render(graphics, partialTick);
            return;
        }

        if (renderGui == null) return;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        graphics.pose().pushPose();
        MAIN.render(renderGui, graphics, partialTick, screenW, screenH);
        graphics.pose().popPose();
    }

    /** 编辑器预览样式覆写（null = 使用配置样式；由 HUD 布局编辑器每帧设置/清除） */
    private static HudStyle styleOverride = null;

    public static void setStyleOverride(HudStyle style) {
        styleOverride = style;
    }

    public static HudStyle hudStyleParsed() {
        if (styleOverride != null) return styleOverride;
        try {
            return HudStyle.valueOf(ConfigManager.getConfig().overlay.hudStyle);
        } catch (IllegalArgumentException | NullPointerException e) {
            return HudStyle.ASTEORBAR;
        }
    }

    /**
     * 是否屏蔽指定原版 HUD 覆盖层（Forge VanillaGuiOverlay 名 / Fabric Gui 方法名统一用常量）。
     * VANILLA 模式一律不屏蔽；ASTEORBAR 按原行为（含 overwrite 旗标与布局 0=关闭）；
     * CUSTOM 按组件开关逐项屏蔽，避免原版与自定义重复绘制（任务书 4.2/11.1）。
     */
    public static boolean shouldCancelVanilla(VanillaOverlayId id) {
        var cfg = ConfigManager.getConfig().overlay;
        if (!cfg.enableOverlay) return false;
        HudStyle style = hudStyleParsed();
        if (style == HudStyle.VANILLA) return false;

        if (style == HudStyle.CUSTOM) {
            var layout = ConfigManager.getConfig().hudLayout;
            var mode = layout.get(id.layoutKey).modeParsed();
            return mode != HudLayoutConfig.ComponentMode.OFF;
        }

        // ASTEORBAR：布局 0 = 本模式绘制关闭 → 不屏蔽原版
        if (cfg.overlayLayoutStyle == 0) return false;
        return switch (id) {
            case PLAYER_HEALTH, FOOD_LEVEL, AIR_LEVEL, MOUNT_HEALTH -> true;
            case EXPERIENCE_BAR -> cfg.overwriteVanillaExperienceBar;
            case ARMOR_LEVEL -> cfg.overwriteVanillaArmorBar;
        };
    }

    /** 原版 HUD 覆盖层标识（Forge=VanillaGuiOverlay / Fabric=Gui.renderXxx 方法） */
    public enum VanillaOverlayId {
        PLAYER_HEALTH(HudLayoutConfig.HEALTH),
        FOOD_LEVEL(HudLayoutConfig.FOOD),
        AIR_LEVEL(HudLayoutConfig.AIR),
        EXPERIENCE_BAR(HudLayoutConfig.EXPERIENCE),
        ARMOR_LEVEL(HudLayoutConfig.ARMOR),
        MOUNT_HEALTH(HudLayoutConfig.MOUNT);

        public final String layoutKey;

        VanillaOverlayId(String layoutKey) {
            this.layoutKey = layoutKey;
        }
    }
}
