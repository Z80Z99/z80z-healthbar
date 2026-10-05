package com.z80z99.z80zhealthbar.overlay.parts.compat;

import com.z80z99.z80zhealthbar.compat.CompatAdapters;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.overlay.OverlayManager;
import com.z80z99.z80zhealthbar.overlay.OverlayPosition;
import com.z80z99.z80zhealthbar.overlay.RenderGui;
import com.z80z99.z80zhealthbar.overlay.SimpleBarOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * ASTEORBAR 布局下的兼容状态条组（数据源统一为 CompatAdapters）。
 * 任一适配器可用即渲染；各行 = 卡片条 + lang 文本（max 未知时仅文本）。
 * 继承 SimpleBarOverlay 以获得布局位置（CENTER/LEFT/RIGHT）能力。
 */
public class CompatStatsOverlay extends SimpleBarOverlay {

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        List<CompatAdapters.Stat> stats = CompatAdapters.collect(player);
        if (stats.isEmpty()) return;

        var cfg = ConfigManager.getConfig().overlay;
        int barWidth = OverlayManager.length > 0 ? OverlayManager.length : FILL_FULL_WIDTH_LONG;
        int barH = cfg.overlayBarInnerHeight;
        int margin = cfg.overlayBarVerticalMargin;
        int blockH = stats.size() * (barH + margin) - margin;

        int left, top;
        OverlayPosition pos = getDefinedPosition();
        if (pos == OverlayPosition.RIGHT) {
            left = screenW - OverlayManager.horizontalOffset - barWidth;
            top = renderGui.getRightHeight(margin);
            renderGui.setRightHeight(top + blockH + margin);
        } else if (pos == OverlayPosition.CENTER) {
            left = centerBarLeft(screenW, barWidth);
            top = renderGui.getLeftHeight(margin);
            renderGui.setLeftHeight(top + blockH + margin);
        } else {
            left = OverlayManager.horizontalOffset;
            top = renderGui.getLeftHeight(margin);
            renderGui.setLeftHeight(top + blockH + margin);
        }

        for (CompatAdapters.Stat stat : stats) {
            drawBarCard(graphics, left, top, barWidth, barH);
            if (stat.max() != null && stat.max() > 0) {
                com.z80z99.z80zhealthbar.overlay.HudBarPainter.drawRatioFill(graphics,
                        left, top, barWidth, barH,
                        (float) Math.max(0d, Math.min(1d, stat.value() / stat.max())), stat.color());
            }
            String text = net.minecraft.network.chat.Component.translatable(
                    stat.langKey()).getString() + ": " + format(stat.value(), stat.max());
            OverlayManager.addStringRender(text,
                    left + barWidth + 5, top + barH / 2 - 4, 0xFFFFFFFF, OverlayManager.ALIGN_LEFT);
            top += barH + margin;
        }

        // AppleSkin 手持食物预览（手持食物时一行文本）
        float[] food = com.z80z99.z80zhealthbar.compat.CompatAdapters.AppleSkinAdapter.heldFoodValues(player);
        if (food != null) {
            OverlayManager.addStringRender(
                    net.minecraft.network.chat.Component.translatable(
                            "z80zhealthbar.stat.appleskin.preview",
                            (int) food[0], String.format(java.util.Locale.ROOT, "%.1f", food[1])).getString(),
                    left, top, 0xFFE8D0A0, OverlayManager.ALIGN_LEFT);
        }
    }

    private static String format(float v, Float max) {
        String vs = v >= 100 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
        if (max == null) return vs;
        return vs + "/" + (max >= 100 ? String.valueOf(Math.round(max)) : String.format(java.util.Locale.ROOT, "%.0f", max));
    }
}
