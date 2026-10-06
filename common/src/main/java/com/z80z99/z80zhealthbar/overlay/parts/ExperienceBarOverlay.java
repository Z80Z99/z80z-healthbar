package com.z80z99.z80zhealthbar.overlay.parts;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.overlay.*;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

public class ExperienceBarOverlay extends SimpleBarOverlay {

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH) {
        var cfg = ConfigManager.getConfig().overlay;
        var colors = ConfigManager.getConfig().colors;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        int expLevel = HudPreviewState.active ? HudPreviewState.xpLevel : player.experienceLevel;
        float expProgress = HudPreviewState.active ? HudPreviewState.xpProgress : player.experienceProgress;

        int fillColor = ColorHelper.parseColor(colors.experience);
        int boundColor = ColorHelper.parseColor(colors.experienceBound);
        int emptyColor = ColorHelper.parseColor(colors.experienceEmpty);

        int barWidth = OverlayManager.length > 0 ? OverlayManager.length : FILL_FULL_WIDTH_LONG;
        int barH = cfg.overlayBarInnerHeight;
        int margin = cfg.overlayBarVerticalMargin;

        int left, top;
        OverlayPosition pos = getDefinedPosition();
        int[] free = BarLayouts.resolve("experience", screenW, screenH, barWidth, barH);
        if (free != null) {
            left = free[0];
            top = free[1];
        } else if (pos == OverlayPosition.CENTER) {
            // Wave 11 修复：物品栏上方布局真正水平居中
            left = centerBarLeft(screenW, barWidth);
            top = renderGui.getLeftHeight(margin);
            renderGui.setLeftHeight(top + barH + margin);
        } else if (pos == OverlayPosition.LEFT) {
            left = OverlayManager.horizontalOffset;
            top = renderGui.getLeftHeight(margin);
            renderGui.setLeftHeight(top + barH + margin);
        } else {
            left = screenW - OverlayManager.horizontalOffset - barWidth;
            top = renderGui.getRightHeight(margin);
            renderGui.setRightHeight(top + barH + margin);
        }

        // Wave 11 美化：卡片 + 统一填充（高光/压暗由共享原语提供）
        drawBarCard(graphics, left, top, barWidth, barH);
        HudBarPainter.drawRatioFill(graphics, left, top, barWidth, barH,
                Math.max(0f, Math.min(1f, expProgress)), fillColor);

        // 显示等级/进度文本（Wave 11：移至条内居中，原版经验条风格）
        // Wave 12 修复：用户不需要百分比，默认关闭 displayExperienceProgress，
        // 保留配置开关供需要时开启（配置驱动，避免死配置）
        if (cfg.displayExperienceLevel || cfg.displayExperienceProgress) {
            StringBuilder sb = new StringBuilder();
            if (cfg.displayExperienceLevel) sb.append(expLevel);
            if (cfg.displayExperienceProgress) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(String.format("%.0f%%", expProgress * 100));
            }
            int textX = left + barWidth / 2;
            int textY = top + barH / 2 - 4;
            OverlayManager.addStringRender(sb.toString(), textX, textY, 0xFFFFFFFF);
        }
        BarLayouts.record("experience", left, top, barWidth, barH);
    }
}
