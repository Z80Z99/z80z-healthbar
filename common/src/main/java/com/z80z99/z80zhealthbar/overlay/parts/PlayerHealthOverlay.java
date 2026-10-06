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

        // 动态效果统一走 BarFx（与实体血条同源）：填充平滑（90ms 无延迟）、伤害残影（420ms 渐隐）。
        // 此前填充走 getFadeValue（500ms 起始延迟 + 最长 1s 余弦缓动）——残影 420ms 内已清空而填充
        // 还停在旧位置，残影被填充盖住永远不可见（实测"残影不见了/预览和实际不匹配"的共同根源）。
        double maxValue = cfg.fullHealthValue > 0 ? cfg.fullHealthValue : maxHealth;
        float rawRatio = (float) Math.max(0d, Math.min(1d, health / maxValue));
        var dxCfg = ConfigManager.getConfig().dynamicFx;
        var fxSt = com.z80z99.z80zhealthbar.mobdisplay.BarFx.tick(
                HudPreviewState.fxKey(player.getId()),
                rawRatio, player.hurtTime > 0, System.currentTimeMillis());
        float dispR = dxCfg.enabled && dxCfg.smooth ? fxSt.display() : rawRatio;
        dispR = Math.max(0f, Math.min(1f, dispR));
        double displayHealth = dispR * maxValue;

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
        params.maxValue = maxValue;
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
        int[] free = BarLayouts.resolve("health", screenW, screenH, barWidth, barH);
        if (free != null) {
            left = free[0];
            top = free[1];
        } else if (pos == OverlayPosition.CENTER) {
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
            // 容量扩展：显示吸收段时条容量 = max(max, hp+abs)——满血吃金苹果时吸收段
            // 仍然可见（此前终点被钳在内宽,满血时吸收段宽度归零完全不可见）
            double total = Math.max(params.maxValue, health + absorption);
            float compress = (float) (params.maxValue / total); // BarFx 比例(相对max)→条比例折算
            int innerW = HudBarPainter.innerWidth(barWidth);
            // 吸收段动态效果（与生命同款 BarFx,独立状态键）：吃金苹果平滑增长、被消耗
            // 平滑消退并在消耗区域留白色渐隐残影——此前直接用原始值,与生命的动态不一致
            float absRaw = (float) Math.max(0d, Math.min(1d, absorption / maxValue));
            var absFx = com.z80z99.z80zhealthbar.mobdisplay.BarFx.tick(
                    HudPreviewState.fxKeyAbs(player.getId()), absRaw, false, System.currentTimeMillis());
            float absDisp = dxCfg.enabled && dxCfg.smooth
                    ? Math.max(0f, Math.min(1f, absFx.display())) : absRaw;
            // 宽度一律 Math.round——(int) 截断叠 BarFx 渐近收敛（display 稳态 ≈0.99995）
            // 会把满值裁掉 1px,条右端永远留一条细缝（实测"血条填不满"）
            int healthW = (int) Math.round(Math.max(0, Math.min(innerW, params.value / total * innerW)));
            // 金段终点按 (平滑生命+平滑吸收)/total 一次取整——红/金两段各自取整会累计丢
            // 1~2px,满血+吸收（总量正好撑满条）时条尾出现细缝（实测"没有填满条"）
            int absEnd = (int) Math.max(healthW, Math.round(Math.min(innerW,
                    (params.value + absDisp * maxValue) / total * innerW)));
            drawBarCard(graphics, left, top, barWidth, barH);
            // 伤害残影（与样式3同源）：掉血后在 [当前填充, 掉血前血量] 区域画渐隐白
            drawGhostSegment(graphics, left, top, barWidth, barH, healthW, fxSt, 0, compress);
            // 生命值填充
            HudBarPainter.drawFillWidth(graphics, left, top, barWidth, barH, healthW, healthColor);
            // 吸收段附加在生命段之后
            HudBarPainter.drawSegment(graphics, left, top, barWidth, barH, healthW, absEnd, absorptionColor);
            // 吸收残影：[金段终点, 消耗前终点] 白色渐隐（锚定当前填充右侧,随填充一起收缩）
            if (dxCfg.enabled && dxCfg.ghost) {
                float gA = Math.max(0f, Math.min(1f, absFx.ghostAlpha()));
                float pre = Math.max(0f, Math.min(1f, absFx.preHit()));
                if (gA > 0f && pre > absDisp) {
                    int ghostW = Math.round((float) ((pre - absDisp) * maxValue / total * innerW));
                    int ghostEnd = Math.min(innerW, absEnd + ghostW);
                    if (ghostEnd > absEnd) {
                        HudBarPainter.drawSegment(graphics, left, top, barWidth, barH, absEnd, ghostEnd,
                                ColorHelper.modifyAlpha(ColorHelper.parseColor(dxCfg.ghostColor),
                                        (int) (gA * 255)));
                    }
                }
            }
            if (blinkBorder) {
                drawBound(graphics, left, top, left + barWidth, top + barH, boundColor);
            }
        } else {
            params.blink = blinkBorder;
            drawGhostSegment(graphics, left, top, barWidth, barH,
                    (int) Math.round(params.value / params.maxValue * HudBarPainter.innerWidth(barWidth)),
                    fxSt, params.verticalShift, 1f);
            renderBar(graphics, left, top, barWidth, barH, params);
        }

        boolean rightSide = pos == OverlayPosition.RIGHT;

        // 吸收状态由左侧状态心形图标（selectHeartIcon 吸收时变金心）+ 文本 "+N" 表达;
        // 此前条右外侧还画一个专门金心,与左侧金心重复（实测"重复的图标"）,已移除

        // 生命值文本（条右外侧；RIGHT 布局改画在条左外并右对齐，避免超出屏幕）
        if (cfg.displayHealthText) {
            // 数字滚动（与实体血条同语义,dynamicFx.numRoll）：数字跟随平滑填充一起滚,
            // 否则掉血瞬间数字即时跳变而条还在缓动——两者不一致（用户实测）
            // 整数不显示小数（20 而非 20.0）；非整保留一位
            String text = trimNum((float) (dxCfg.enabled && dxCfg.numRoll ? displayHealth : health));
            if (absorption > 0) text += " + " + trimNum(absorption);
            int textY = top + barH / 2 - 4;
            if (rightSide) {
                // 条左外右对齐,再往左避开条左侧状态心形图标（left-11）——此前 left-5 数字盖住图标
                OverlayManager.addStringRender(text, left - 15, textY, 0xFFFFFFFF, OverlayManager.ALIGN_RIGHT);
            } else {
                int textX = left + barWidth + 5;
                OverlayManager.addStringRender(text, textX, textY, 0xFFFFFFFF, OverlayManager.ALIGN_LEFT);
            }
        }
        BarLayouts.record("health", left, top, barWidth, barH);
    }

    /** 数值整数化：整数不显示小数（20 而非 20.0），非整保留一位 */
    private static String trimNum(float v) {
        float r = Math.round(v * 10) / 10f;
        if (r == (int) r) return String.valueOf((int) r);
        return String.valueOf(r);
    }

    /** 伤害残影段（与样式3同源 BarFx 动画）：[当前填充, 掉血前血量] 区域白色渐隐（dynamicFx.ghost 控制）；内宽几何。
     * ratioScale：BarFx 比例（相对 max）到条比例的折算系数——吸收段容量扩展时 &lt;1，保证残影边界与填充贴合 */
    private void drawGhostSegment(GuiGraphics graphics, int left, int top, int barWidth, int barH,
                                  int fillW, com.z80z99.z80zhealthbar.mobdisplay.BarFx.State st, int vShift,
                                  float ratioScale) {
        var dxCfg = ConfigManager.getConfig().dynamicFx;
        if (!dxCfg.enabled || !dxCfg.ghost) return;
        float ghostA = Math.max(0f, Math.min(1f, st.ghostAlpha()));
        if (ghostA <= 0f) return;
        float preHit = Math.max(0f, Math.min(1f, st.preHit()));
        int innerW = HudBarPainter.innerWidth(barWidth);
        int preHitW = Math.min(innerW, Math.round(preHit * ratioScale * innerW));
        if (preHitW <= fillW) return;
        int color = ColorHelper.modifyAlpha(ColorHelper.parseColor(dxCfg.ghostColor),
                (int) (ghostA * 255));
        int top2 = HudBarPainter.fillTop(top, barH) + vShift;
        graphics.fill(left + HudBarPainter.INSET + fillW, top2,
                left + HudBarPainter.INSET + preHitW, top2 + HudBarPainter.innerHeight(barH), color);
    }

    private void renderBar(GuiGraphics g, int x, int y, int w, int h, Parameters p) {
        drawBarCard(g, x, y, w, h);
        int innerW = HudBarPainter.innerWidth(w);
        int fillWidth = (int) Math.round(Math.max(0, Math.min(innerW, p.value / p.maxValue * innerW)));
        HudBarPainter.drawFillWidth(g, x, y, w, h, fillWidth, p.fillColor, p.verticalShift);

        if (p.blink) {
            // 低血量闪烁边框（覆盖卡片默认边框）
            drawBound(g, x, y, x + w, y + h, p.boundColor);
        }
    }
}
