package com.z80z99.z80zhealthbar.overlay.parts;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.overlay.*;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

public class AirLevelOverlay extends SimpleBarOverlay {

    // icons.png 气泡坐标 (9x9)：16=满气泡, 25=部分气泡（原版仅用这两个）
    private static final int ICON_BUBBLE_U = 16, ICON_BUBBLE_V = 18;
    private static final int ICON_BUBBLE_SPLIT_U = 25, ICON_BUBBLE_SPLIT_V = 18;

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH) {
        var cfg = ConfigManager.getConfig().overlay;
        var colors = ConfigManager.getConfig().colors;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isUnderWater()) return;

        int air = player.getAirSupply();
        int maxAir = player.getMaxAirSupply();
        if (air >= maxAir) return;

        int fillColor = ColorHelper.parseColor(colors.air);
        int boundColor = ColorHelper.parseColor(colors.airBound);

        Parameters params = new Parameters();
        params.value = air;
        params.maxValue = maxAir;
        params.fillColor = fillColor;
        params.boundColor = boundColor;
        params.emptyColor = 0x40000000;

        int barWidth = OverlayManager.length > 0 ? OverlayManager.length : FILL_FULL_WIDTH_LONG;
        int barH = cfg.overlayBarInnerHeight;
        int margin = cfg.overlayBarVerticalMargin;

        OverlayPosition pos = getDefinedPosition();
        int left, top;

        if (pos == OverlayPosition.CENTER) {
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

        // Wave 11 美化：气泡图标（空气不足 1/4 时用破裂气泡）
        boolean lowAir = air <= maxAir / 4;
        drawIcon(graphics, left - 11, top, barH,
                lowAir ? ICON_BUBBLE_SPLIT_U : ICON_BUBBLE_U, ICON_BUBBLE_V);

        int fillW = (int)(air / (float)maxAir * barWidth);
        fillW = Math.max(0, Math.min(barWidth, fillW));

        drawBarCard(graphics, left, top, barWidth, barH);
        graphics.fill(left, top, left + fillW, top + barH, fillColor);
        // 顶部高光（渐变感）
        graphics.fill(left, top, left + fillW, top + 1, 0x55FFFFFF);
    }
}
