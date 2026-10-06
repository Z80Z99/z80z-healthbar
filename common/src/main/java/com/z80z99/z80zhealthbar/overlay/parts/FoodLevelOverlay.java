package com.z80z99.z80zhealthbar.overlay.parts;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.overlay.*;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

public class FoodLevelOverlay extends SimpleBarOverlay {

    // icons.png 鸡腿坐标 (9x9)：16=容器, 52=满, 61=半；饥饿效果时原版整体偏移 +36 → 133=容器, 88=满, 97=半
    private static final int ICON_FOOD_U = 16, ICON_FOOD_V = 27;
    private static final int ICON_FOOD_HUNGRY_U = 88, ICON_FOOD_HUNGRY_V = 27;

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH) {
        var cfg = ConfigManager.getConfig().overlay;
        var colors = ConfigManager.getConfig().colors;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        int foodLevel = HudPreviewState.active ? HudPreviewState.food : player.getFoodData().getFoodLevel();
        float saturation = HudPreviewState.active ? 0f : player.getFoodData().getSaturationLevel();
        if (hideUnchanged(foodLevel)) return; // 数值长期不变时隐藏（hideUnchangingBarAfterSeconds）

        int fillColor = ColorHelper.parseColor(colors.foodNormal);
        int boundColor = ColorHelper.parseColor(colors.foodBound);

        boolean isHungry = foodLevel <= 6;
        boolean blinkBorder = false;
        if (isHungry && cfg.enableFoodBlink) {
            boundColor = ColorHelper.parseColor(
                    tick / 6 % 2 == 0 ? colors.foodBoundBlink : colors.foodBound);
            blinkBorder = true;
        }

        Parameters params = new Parameters();
        params.value = getFadeValue(foodLevel);
        params.maxValue = cfg.fullFoodLevelValue > 0 ? cfg.fullFoodLevelValue : 20;
        params.fillColor = fillColor;
        params.boundColor = boundColor;
        params.emptyColor = ColorHelper.parseColor(colors.foodEmpty);

        if (isHungry && cfg.shakeHealthAndFoodWhileLow) {
            applyShakeEffect(params, 3);
        }

        int barWidth = OverlayManager.length > 0 ? OverlayManager.length : FILL_FULL_WIDTH_LONG;
        int barH = cfg.overlayBarInnerHeight;
        int margin = cfg.overlayBarVerticalMargin;

        OverlayPosition pos = getDefinedPosition();
        int left, top;
        int[] free = BarLayouts.resolve("food", screenW, screenH, barWidth, barH);
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

        // Wave 11 美化：鸡腿图标（饥饿时红色饥饿鸡腿）
        drawIcon(graphics, left - 11, top, barH,
                isHungry ? ICON_FOOD_HUNGRY_U : ICON_FOOD_U, ICON_FOOD_V);

        int innerW = HudBarPainter.innerWidth(barWidth);
        // Math.round 而非 (int) 截断——BarFx 类平滑值稳态略小于 1.0 时截断会裁掉 1px 填充
        int fillW = (int) Math.round(Math.max(0, Math.min(innerW, params.value / params.maxValue * innerW)));

        drawBarCard(graphics, left, top, barWidth, barH);

        // 饱食度填充（统一内宽填充 + 高光/压暗；低饥饿抖动经 yOffset）
        HudBarPainter.drawFillWidth(graphics, left, top, barWidth, barH, fillW,
                params.fillColor, params.verticalShift);

        // 饱和度覆盖（金色叠加在食物条之上，原版逻辑）
        if (cfg.displaySaturation && saturation > 0) {
            float satRatio = saturation / (float)(cfg.fullSaturationValue > 0 ? cfg.fullSaturationValue : 20);
            int satW = (int) Math.round(Math.max(0f, Math.min(1f, satRatio)) * innerW);
            HudBarPainter.drawSegment(graphics, left, top, barWidth, barH, 0, satW,
                    ColorHelper.parseColor(colors.saturation));
        }

        if (blinkBorder) {
            // 饥饿闪烁边框（覆盖卡片默认边框）
            drawBound(graphics, left, top, left + barWidth, top + barH, boundColor);
        }

        // 文本（条右外侧；RIGHT 布局改画在条左外并右对齐,再往左避开条左侧图标——
        // 此前 x=left-5 与 left-11 处的图标重叠,数字盖住图标）
        if (cfg.displayFoodText) {
            int textY = top + barH / 2 - 4 + cfg.overlayBarTextOffsetY;
            String text = foodLevel + "/" + (int) params.maxValue;
            if (pos == OverlayPosition.RIGHT) {
                OverlayManager.addStringRender(text, left - 15, textY, 0xFFFFFFFF,
                        OverlayManager.ALIGN_RIGHT);
            } else {
                OverlayManager.addStringRender(text, left + barWidth + 5, textY, 0xFFFFFFFF,
                        OverlayManager.ALIGN_LEFT);
            }
        }
        BarLayouts.record("food", left, top, barWidth, barH);
    }
}
