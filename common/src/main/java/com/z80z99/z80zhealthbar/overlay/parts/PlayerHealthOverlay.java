package com.z80z99.z80zhealthbar.overlay.parts;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.overlay.*;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public class PlayerHealthOverlay extends SimpleBarOverlay {

    public static final int ABSORPTION_TOGETHER = 0;
    public static final int ABSORPTION_STACK = 1;
    public static final int ABSORPTION_BOUND = 2;

    // vanilla icons.png 心形坐标 (9x9)
    private static final int ICON_NORMAL_U = 52, ICON_NORMAL_V = 0;
    private static final int ICON_POISON_U = 88, ICON_POISON_V = 0;
    private static final int ICON_WITHER_U = 124, ICON_WITHER_V = 0;
    private static final int ICON_ABSORB_U = 160, ICON_ABSORB_V = 0;
    private static final int ICON_FROZEN_U = 178, ICON_FROZEN_V = 0;

    /** 根据状态选择心形图标 UV（Wave 11 美化：图标自带状态色） */
    private int[] selectHeartIcon(Player player) {
        if (player.getAbsorptionAmount() > 0) return new int[]{ICON_ABSORB_U, ICON_ABSORB_V};
        if (player.hasEffect(MobEffects.POISON)) return new int[]{ICON_POISON_U, ICON_POISON_V};
        if (player.hasEffect(MobEffects.WITHER)) return new int[]{ICON_WITHER_U, ICON_WITHER_V};
        if (player.getTicksFrozen() > 0) return new int[]{ICON_FROZEN_U, ICON_FROZEN_V};
        return new int[]{ICON_NORMAL_U, ICON_NORMAL_V};
    }

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH) {
        var cfg = ConfigManager.getConfig().overlay;
        var colors = ConfigManager.getConfig().colors;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        float health = HudPreviewState.active ? HudPreviewState.health : player.getHealth();
        float maxHealth = HudPreviewState.active ? HudPreviewState.maxHealth : player.getMaxHealth();
        float absorption = HudPreviewState.active ? HudPreviewState.absorption : player.getAbsorptionAmount();

        // 确定生命值颜色
        int healthColor = ColorHelper.parseColor(colors.healthNormal);
        if (player.hasEffect(MobEffects.POISON)) {
            healthColor = ColorHelper.parseColor(colors.healthPoison);
        } else if (player.hasEffect(MobEffects.WITHER)) {
            healthColor = ColorHelper.parseColor(colors.healthWither);
        } else if (player.getTicksFrozen() > 0) {
            healthColor = ColorHelper.parseColor(colors.healthFrozen);
        }
        int boundColor = ColorHelper.parseColor(colors.healthBound);
        int emptyColor = ColorHelper.parseColor(colors.healthEmpty);
        int absorptionColor = ColorHelper.parseColor(colors.absorption);

        double displayHealth = getFadeValue(health);

        // 低血量闪烁
        boolean lowHealth = health / maxHealth <= cfg.lowHealthRate;
        boolean blinkBorder = false;
        if (lowHealth && cfg.enableHealthBlink) {
            boundColor = ColorHelper.parseColor(
                    tick / 6 % 2 == 0 ? colors.healthBoundBlink : colors.healthBoundLow);
            blinkBorder = true;
        }

        Parameters params = new Parameters();
        params.value = displayHealth;
        params.maxValue = cfg.fullHealthValue > 0 ? cfg.fullHealthValue : maxHealth;
        params.fillColor = healthColor;
        params.boundColor = boundColor;
        params.emptyColor = emptyColor;

        if (lowHealth && cfg.shakeHealthAndFoodWhileLow) {
            applyShakeEffect(params, 2);
        }

        // 计算条形位置
        int barWidth = OverlayManager.length > 0 ? OverlayManager.length : FILL_FULL_WIDTH_LONG;
        int barH = cfg.overlayBarInnerHeight;
        int margin = cfg.overlayBarVerticalMargin;
        OverlayPosition pos = getDefinedPosition();
        int left, top;

        if (pos == OverlayPosition.CENTER) {
            // Wave 11 修复：物品栏上方布局真正水平居中（原版 HUD 逻辑）
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

        // Wave 11 美化：状态心形图标（条左侧 2px 间距）
        int[] icon = selectHeartIcon(player);
        drawIcon(graphics, left - 11, top, barH, icon[0], icon[1]);

        // 与吸收值一起显示
        if (cfg.absorptionMode == ABSORPTION_TOGETHER && absorption > 0) {
            // Wave 12 修复：原先只画吸收段导致金苹果后血条被"清空并覆盖"。
            // 正确行为：生命值填充 + 吸收段附加在生命段之后（类似原版金心附加在血心后）
            int healthW = (int)(params.value / params.maxValue * barWidth);
            int absWidth = (int)(absorption / params.maxValue * barWidth);
            drawBarCard(graphics, left, top, barWidth, barH);
            // 伤害残影（与样式3同源）：掉血后原血量位置留下渐隐白段
            drawGhostSegment(graphics, left, top, barWidth, barH,
                    (float) (health / params.maxValue), player, 0);
            // 生命值填充
            drawEmptyFill(graphics, left, top, left + healthW, top + barH, healthColor);
            // 顶部高光（渐变感）
            drawEmptyFill(graphics, left, top, left + healthW, top + 1, 0x55FFFFFF);
            // 吸收段附加在生命段之后（clamp 到条宽内）
            int absEnd = Math.min(left + barWidth, left + healthW + absWidth);
            if (absEnd > left + healthW) {
                drawEmptyFill(graphics, left + healthW, top, absEnd, top + barH, absorptionColor);
            }
            if (blinkBorder) {
                drawBound(graphics, left, top, left + barWidth, top + barH, boundColor);
            }
        } else {
            params.blink = blinkBorder;
            drawGhostSegment(graphics, left, top, barWidth, barH,
                    (float) (health / params.maxValue), player, params.verticalShift);
            renderBar(graphics, left, top, barWidth, barH, params);
        }

        boolean rightSide = pos == OverlayPosition.RIGHT;

        // 吸收金心：条右外侧（原版逻辑：金心跟在血条后）；RIGHT 布局该处越出屏幕，省略
        if (absorption > 0 && !rightSide) {
            drawIcon(graphics, left + barWidth + 2, top, barH, ICON_ABSORB_U, ICON_ABSORB_V);
        }

        // 生命值文本（条右外侧；RIGHT 布局改画在条左外并右对齐，避免超出屏幕）
        if (cfg.displayHealthText) {
            // 整数不显示小数（20 而非 20.0）；非整保留一位
            String text = trimNum(health);
            if (absorption > 0) text += " + " + trimNum(absorption);
            int textY = top + barH / 2 - 4;
            if (rightSide) {
                OverlayManager.addStringRender(text, left - 5, textY, 0xFFFFFFFF, OverlayManager.ALIGN_RIGHT);
            } else {
                int textX = left + barWidth + (absorption > 0 ? 15 : 5);
                OverlayManager.addStringRender(text, textX, textY, 0xFFFFFFFF, OverlayManager.ALIGN_LEFT);
            }
        }
    }

    /** 数值整数化：整数不显示小数（20 而非 20.0），非整保留一位 */
    private static String trimNum(float v) {
        float r = Math.round(v * 10) / 10f;
        if (r == (int) r) return String.valueOf((int) r);
        return String.valueOf(r);
    }

    /** 伤害残影段（与样式3同源 BarFx 动画）：掉血后在 [当前填充, 掉血前血量] 区间画渐隐白（dynamicFx.ghost 控制） */
    private void drawGhostSegment(GuiGraphics graphics, int left, int top, int barWidth, int barH,
                                  float rawRatio, Player player, int vShift) {
        var dxCfg = ConfigManager.getConfig().dynamicFx;
        if (!dxCfg.enabled || !dxCfg.ghost) return;
        float ratio = Math.max(0f, Math.min(1f, rawRatio));
        var st = com.z80z99.z80zhealthbar.mobdisplay.BarFx.tick(player.getId(),
                ratio, player.hurtTime > 0, System.currentTimeMillis());
        float ghostR = Math.max(st.ghost(), ratio);
        int fillW = (int) (ratio * barWidth);
        int ghostW = (int) (Math.max(0f, Math.min(1f, ghostR)) * barWidth);
        if (ghostW <= fillW) return;
        int color = ColorHelper.modifyAlpha(ColorHelper.parseColor(dxCfg.ghostColor), 255);
        graphics.fill(left + fillW, top + vShift, left + ghostW, top + barH + vShift, color);
    }

    private void renderBar(GuiGraphics g, int x, int y, int w, int h, Parameters p) {
        int fillWidth = (int)(p.value / p.maxValue * w);
        fillWidth = Math.max(0, Math.min(w, fillWidth));

        drawBarCard(g, x, y, w, h);
        drawEmptyFill(g, x, y + p.verticalShift, x + fillWidth, y + h + p.verticalShift, p.fillColor);
        // 顶部高光（渐变感）
        drawEmptyFill(g, x, y + p.verticalShift, x + fillWidth, y + p.verticalShift + 1, 0x55FFFFFF);

        if (p.blink) {
            // 低血量闪烁边框（覆盖卡片默认边框）
            drawBound(g, x, y, x + w, y + h, p.boundColor);
        }
    }
}
