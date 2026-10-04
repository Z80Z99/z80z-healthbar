package com.z80z99.z80zhealthbar.overlay.parts;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.overlay.*;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public class ArmorLevelOverlay extends SimpleBarOverlay {

    // icons.png 盾牌坐标 (9x9)
    private static final int ICON_SHIELD_U = 34, ICON_SHIELD_V = 9;

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH) {
        var cfg = ConfigManager.getConfig().overlay;
        var colors = ConfigManager.getConfig().colors;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        int armor = player.getArmorValue();
        if (armor <= 0) return;

        int toughness = (int) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

        Parameters params = new Parameters();
        params.value = armor;
        params.maxValue = cfg.fullArmorValue > 0 ? cfg.fullArmorValue : 20;
        params.fillColor = ColorHelper.parseColor(colors.armor);
        params.boundColor = ColorHelper.parseColor(colors.armorBound);
        params.emptyColor = ColorHelper.parseColor(colors.armorEmpty);

        int barWidth = OverlayManager.length > 0 ? OverlayManager.length : FILL_FULL_WIDTH_LONG;
        int barH = cfg.overlayBarInnerHeight;
        int margin = cfg.overlayBarVerticalMargin;

        int left, top;
        OverlayPosition pos = getDefinedPosition();

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

        // Wave 11 美化：盾牌图标
        drawIcon(graphics, left - 11, top, barH, ICON_SHIELD_U, ICON_SHIELD_V);

        int fillW = (int)(params.value / params.maxValue * barWidth);
        fillW = Math.max(0, Math.min(barWidth, fillW));

        drawBarCard(graphics, left, top, barWidth, barH);
        graphics.fill(left, top, left + fillW, top + barH, params.fillColor);
        // 顶部高光（渐变感）
        graphics.fill(left, top, left + fillW, top + 1, 0x55FFFFFF);

        // 文本：护甲值，开启韧性显示且韧性>0 时追加 (韧性)；RIGHT 布局改条左外右对齐
        StringBuilder text = new StringBuilder(String.valueOf(armor));
        if (cfg.displayArmorToughness && toughness > 0) {
            text.append(" (").append(toughness).append(")");
        }
        int textY = top + barH / 2 - 4;
        if (pos == OverlayPosition.RIGHT) {
            OverlayManager.addStringRender(text.toString(), left - 5, textY, 0xFFFFFFFF,
                    OverlayManager.ALIGN_RIGHT);
        } else {
            OverlayManager.addStringRender(text.toString(), left + barWidth + 5, textY, 0xFFFFFFFF,
                    OverlayManager.ALIGN_LEFT);
        }
    }
}
