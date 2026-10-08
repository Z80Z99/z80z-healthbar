package com.z80z99.z80zhealthbar.mobdisplay;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.status.EntityStatusSnapshot;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 生命值渲染器：
 *  - renderBar = 样式 C 动态长条：动态色填充 + 空槽 + 分格刻度 + 吸收环 + "当前/最大" 文本。
 *    2026-10-05 V2 完全重写：放弃 AsteorBar 反编译移植的 z 层偏移 + 浮点边界方案，
 *    改为整数条像素几何 + 画家算法（BAR_RECT 无深度测试、批次内按插入序确定绘制），
 *    分格刻度为叠加式 1px 刻度线而非在填充里切缝——从实现层面根除尾部缝隙/白块错位一类缺陷。
 *  - renderPlaque = 样式 B 心形图标行（含状态选型与容器底图）
 */
public class HealthDisplayRenderer implements IMobDisplayRenderer {

    private static final ResourceLocation KEY = new ResourceLocation(Z80ZHealthBar.MOD_ID, "health");
    @Override
    public ResourceLocation getKey() { return KEY; }

    @Override
    public boolean wantsToRender(EntityStatusSnapshot snap) {
        return true; // 满血/吸收规则由 MobVisibilityChecker 统一裁决
    }

    // ================= 样式 C：AsteorBar 长条 =================

    @Override
    public void renderBar(PoseStack poseStack, MultiBufferSource buffer, Font font,
                          EntityStatusSnapshot snap, int x, int y, int barWidth, float alpha, float worldScale) {
        if (ConfigManager.getConfig().barStyle.originalRender) {
            renderBarOriginal(poseStack, buffer, font, snap, x, y, barWidth);
            return;
        }
        var cfg = ConfigManager.getConfig();
        var barCfg = cfg.barStyle;
        var colors = cfg.colors;

        // 血条像素偏移仅作用于条本身；文字保留默认锚点，由文字偏移独立控制
        int textAnchorX = x, textAnchorY = y;
        x += barCfg.barPixelOffsetX;
        y += barCfg.barPixelOffsetY;

        int barH = barCfg.barHalfHeight * 2;
        int boundW = barCfg.barBoundWidth;

        // barAlpha（0..255 血条不透明度配置）与动画渐隐 alpha 相乘生效
        float alphaMul = alpha * (barCfg.barAlpha / 255f);

        float ratio = snap.plainHealthRatio();
        // 死亡碎裂记录：条矩形 + 填充色（renderBarsGlobal 统一消费;lift = 条中心世界抬升）
        ShatterFx.record(snap.entityId, x + barWidth / 2f, y + barH / 2f,
                barWidth + boundW * 2f, barH, fillColor(snap, ratio), worldScale,
                (float) (snap.entityHeight + barCfg.barOffsetY) - barH * worldScale / 2f);
        // 动态效果：平滑填充 / 伤害残影 / 受伤闪白（dynamicFx 关闭时全部退化为直读比例）
        var fx = cfg.dynamicFx;
        var st = BarFx.tick(snap.entityId, ratio, snap.hurtTime > 0, System.currentTimeMillis());
        float dispR = fx.enabled && fx.smooth ? st.display() : ratio;
        // 平滑动画指数逼近浮点上永不精确到达(如停在 0.99987):距满血 0.1% 内一律视为满血,
        // 否则"只显示完整格"取整后满血永远差一格、普通填充差一像素(在渲染入口统一钳满,动画层不动)
        if (dispR >= 1f - 1e-3f) dispR = 1f;
        float preHitR = fx.enabled && fx.ghost ? Math.max(st.preHit(), dispR) : dispR;
        float ghostAlpha = fx.enabled && fx.ghost ? Math.max(0f, Math.min(1f, st.ghostAlpha())) : 0f;
        float flash = fx.enabled && fx.hurtFlash ? st.flash() : 0f;
        // 受伤脉冲：数字弹跳/变色用——独立于"血条闪白"开关（其关掉不影响数字效果）
        float pulse = fx.enabled ? st.flash() : 0f;
        int fillColor = fillColor(snap, ratio);
        if (flash > 0.01f) {
            fillColor = ColorHelper.lerp(fillColor, 0xFFFFFFFF, flash * 0.6f);
        }
        // 第二层动态效果：低血脉冲（实体条阈值固定 0.3）/ 治疗泛光（填充本体,与数字变绿互补）
        if (fx.enabled) {
            if (fx.lowHpPulse && ratio <= 0.3f) {
                fillColor = ColorHelper.lerp(fillColor, 0xFFFFFFFF,
                        com.z80z99.z80zhealthbar.overlay.HudFx.pulse(System.currentTimeMillis()) * 0.45f);
            }
            if (fx.healGlow) {
                fillColor = ColorHelper.lerp(fillColor, 0xFF50E080, st.heal() * 0.45f);
            }
        }
        int emptyColor = ColorHelper.modifyAlpha(ColorHelper.parseColor(colors.mobBarEmpty), (int) (alphaMul * 255));
        int boundColor = ColorHelper.modifyAlpha(ColorHelper.parseColor(colors.mobBarBound), (int) (alphaMul * 255));
        fillColor = ColorHelper.modifyAlpha(fillColor, (int) (alphaMul * 255));
        // 美术：最外 1px 深色描边 + 边框色环 + 刻度线取空槽色加深，形成"描边-框-槽-填充"四层观感
        int outlineColor = ColorHelper.modifyAlpha(
                ColorHelper.lerp(ColorHelper.parseColor(colors.mobBarBound), 0xFF000000, 0.6f), (int) (alphaMul * 255));
        int notchColor = ColorHelper.modifyAlpha(
                ColorHelper.lerp(ColorHelper.parseColor(colors.mobBarEmpty), 0xFF000000, 0.5f), (int) (alphaMul * 255));

        // ============ V2 实现：整数几何 + 画家算法 ============
        // BAR_RECT 无深度测试、批次内按插入序绘制——同批矩形谁后画谁在上，完全确定。
        // 旧实现（AsteorBar 反编译移植）用 z 层偏移做层叠、用浮点比例换算像素边界，
        // 在镜像缩放下产生顶点取整错位（尾部缝隙/白块错位一类缺陷的根源），全部废除：
        // 所有坐标都是整数条像素，格线改为叠加刻度而非在填充里切缝。
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(ModRenderType.barRect());

        // 填充/残影宽度（整数条像素）：整格模式按格边界取整,格边界 = i*条宽/格数(整数除法,满血必达条尾)
        boolean segmented = barCfg.barVariant == 2;
        int cells = segmented ? segmentCells(snap.maxHealth) : 1;
        int fillW;
        if (segmented && barCfg.segmentWholeOnly) {
            int fullCells = (int) (dispR * cells + 1e-2f);
            fillW = fullCells >= cells ? barWidth : (fullCells * barWidth) / cells;
        } else {
            fillW = (int) (dispR * barWidth);
        }
        // 残影区域上缘 = 掉血前血量（比例）;透明度随时间线性渐隐（固定 420ms 硬收敛,无渐近尾巴）
        int preHitW = (int) (Math.max(0f, Math.min(1f, preHitR)) * barWidth);
        int ghostA = (int) (ghostAlpha * alphaMul * 255);

        // 1) 最外 1px 深色描边（压住边框外缘,消除与天空色之间的半透明过渡）
        fillRect(vc, m, x - boundW - 1, y - boundW - 1,
                barWidth + 2 * boundW + 2, barH + 2 * boundW + 2, outlineColor);
        // 2) 吸收环（画在边框之下,与旧版层级一致）
        if (snap.absorption > 0 && boundW > 0) {
            int absColor = ColorHelper.modifyAlpha(
                    ColorHelper.parseColor(colors.mobBarAbsorption), (int) (alphaMul * 255));
            float absRate = snap.absorption / snap.maxHealth;
            int fullRings = Math.min(3, (int) Math.floor(absRate));
            float frac = absRate - (int) Math.floor(absRate);
            int fracW = Math.round((barWidth + boundW * 2) * frac);
            if (fracW == 0 && fullRings > 0) { fracW = barWidth + boundW * 2; fullRings--; }
            for (int i = 0; i < fullRings; i++) {
                ring(vc, m, x - boundW * (i + 1), y - boundW * (i + 1),
                        barWidth + boundW * (2 * i + 1), barH + boundW * (2 * i + 1), boundW, absColor);
            }
            if (fracW > 0) {
                fillRect(vc, m, x - boundW, y - boundW, fracW, boundW, absColor);
            }
        }
        // 3) 边框（环绕空槽+填充区：外沿 [x-t, x+宽+t],内孔恰好是填充区）
        if (boundW > 0) {
            ring(vc, m, x - boundW, y - boundW, barWidth + boundW, barH + boundW, boundW, boundColor);
        }
        // 4) 空槽
        fillRect(vc, m, x, y, barWidth, barH, emptyColor);
        // 5) 伤害残影（[当前填充, 掉血前血量] 区域,整体白色渐隐;整格模式下下缘 = 可见填充边界）
        if (preHitW > fillW && ghostA > 0) {
            fillRect(vc, m, x + fillW, y, preHitW - fillW, barH,
                    ColorHelper.modifyAlpha(ColorHelper.parseColor(fx.ghostColor), ghostA));
        }
        // 6) 填充（连续矩形）
        if (fillW > 0) {
            switch (Math.max(0, Math.min(3, barCfg.barVariant))) {
                case 1 -> gradientFill(vc, m, x, y, fillW, barH, fillColor);
                case 3 -> glossyFill(vc, m, x, y, fillW, barH, fillColor);
                default -> fillRect(vc, m, x, y, fillW, barH, fillColor);
            }
        }
        // 7) 扫光流动：填充区周期性扫过移动高光带（前缘亮边 + 主体淡带,裁剪在填充内;
        //    per-entity 相位偏移防多根条同步）。绘制于填充之上、刻度之下
        if (fx.enabled && fx.sheen && fillW > 0) {
            double sheenPhase = com.z80z99.z80zhealthbar.overlay.HudFx.advanceSheen(
                    snap.entityId, fillW, System.currentTimeMillis(), false);
            int[] band = com.z80z99.z80zhealthbar.overlay.HudFx.sheenBandPhase(barWidth, fillW, sheenPhase);
            if (band != null) {
                int edge = com.z80z99.z80zhealthbar.overlay.HudFx.sheenEdgeW(barWidth);
                int a1 = (int) (0x10 * alphaMul) << 24 | 0xFFFFFF;
                int a2 = (int) (0x20 * alphaMul) << 24 | 0xFFFFFF;
                fillRect(vc, m, x + band[0] + edge, y, band[1] - band[0] - edge, barH, a1);
                fillRect(vc, m, x + band[0], y, Math.min(edge, band[1] - band[0]), barH, a2);
            }
        }
        // 8) 分格刻度线（变体 2）：叠加式 1px 纵向刻度,画在填充/残影之上——
        //    不再在填充里切缝,格边界整数化后不存在尾部缝隙与对位问题;格宽不足 2px（连 1px 刻度
        //    + 1px 填充都放不下）才省略,此前阈值 4px 过于保守,19 格/72px 条会被整条省掉
        if (segmented && cells > 1) {
            int cellW = barWidth / cells;
            if (cellW >= 2) {
                for (int i = 1; i < cells; i++) {
                    int nx = x + (i * barWidth) / cells;
                    fillRect(vc, m, nx, y, 1, barH, notchColor);
                }
            }
        }

        // "当前/最大(+吸收)" 文本（AsteorBar 样式）+ 数字动态效果（滚动/弹跳/变色）
        if (barCfg.barTextScale > 0.05 && font != null) {
            float shownHp = fx.enabled && fx.numRoll
                    ? Math.min(snap.maxHealth, dispR * snap.maxHealth) : snap.health;
            float punchS = fx.enabled && fx.numPunch ? 1f + pulse * 0.35f : 1f;
            int textColor = ColorHelper.modifyAlpha(0xFFFFFFFF, (int) (alphaMul * 255));
            if (fx.enabled && fx.numTint) {
                if (pulse > 0.01f) {
                    textColor = ColorHelper.lerp(textColor, 0xFFFF5050, pulse * 0.8f);
                } else if (st.heal() > 0.01f) {
                    textColor = ColorHelper.lerp(textColor, 0xFF50E080, st.heal() * 0.6f);
                }
            }
            poseStack.pushPose();
            float ts = (float) barCfg.barTextScale;
            poseStack.scale(ts, ts, 1f);
            String text = MobHealthBarStyle.formatValue(shownHp) + "/"
                    + MobHealthBarStyle.formatValue(snap.maxHealth);
            // X 在字形单位（随 ts 缩放），offsetX/Y 以条像素计 → X 需除回 ts；
            // 文字锚点用未偏移的条位（血条偏移不带动文字）
            float drawX = -font.width(text) / 2f + (float) barCfg.barTextOffsetX / ts;
            float drawY = (textAnchorY + barH / 2f - 4) / ts + (float) barCfg.barTextOffsetY;
            poseStack.pushPose();
            if (punchS > 1.001f) {
                // 弹跳以文字中心为锚点向外扩散（原先锚在左上角只向下/右生长）
                float cX = (float) barCfg.barTextOffsetX / ts;
                float cY = drawY + 4f;
                poseStack.translate(cX, cY, 0);
                poseStack.scale(punchS, punchS, 1f);
                poseStack.translate(-cX, -cY, 0);
            }
            font.drawInBatch(text, drawX, drawY, textColor, false,
                    poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            poseStack.popPose();
            if (snap.absorption > 0) {
                String abs = "+" + MobHealthBarStyle.formatNumber(snap.absorption);
                int absColor = ColorHelper.modifyAlpha(0xFFFFFF00, (int) (alphaMul * 255));
                font.drawInBatch(abs, ((float) textAnchorX + 2 + (float) barCfg.barTextOffsetX) / ts,
                        drawY, absColor, false,
                        poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
            poseStack.popPose();
        }
    }

    private void ring(VertexConsumer vc, Matrix4f m, int x, int y, int w, int h, int t, int color) {
        fillRect(vc, m, x, y, w + t, t, color);
        fillRect(vc, m, x, y + h, w + t, t, color);
        fillRect(vc, m, x, y + t, t, h - t, color);
        fillRect(vc, m, x + w, y + t, t, h - t, color);
    }

    private void fillRect(VertexConsumer vc, Matrix4f m, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        vc.vertex(m, x, y, 0).color(color).endVertex();
        vc.vertex(m, x, y + h, 0).color(color).endVertex();
        vc.vertex(m, x + w, y + h, 0).color(color).endVertex();
        vc.vertex(m, x + w, y, 0).color(color).endVertex();
    }

    /** 长条变体 1：竖向渐变——顶部提亮 35%、底部压暗 30%（单 quad 顶点色插值） */
    private void gradientFill(VertexConsumer vc, Matrix4f m, int x, int y, int w, int h, int color) {
        int top = ColorHelper.lerp(color, 0xFFFFFFFF, 0.35f);
        int bottom = ColorHelper.lerp(color, 0xFF000000, 0.30f);
        vc.vertex(m, x, y, 0).color(top).endVertex();
        vc.vertex(m, x, y + h, 0).color(bottom).endVertex();
        vc.vertex(m, x + w, y + h, 0).color(bottom).endVertex();
        vc.vertex(m, x + w, y, 0).color(top).endVertex();
    }

    /** 分段刻度的格数：segmentHp > 0 按固定血量分格（超 64 格合并防亚像素），否则 segmentCount 固定格数 */
    private static int segmentCells(float maxHealth) {
        var barCfg = ConfigManager.getConfig().barStyle;
        int cells = barCfg.segmentCount;
        if (barCfg.segmentHp > 0) {
            cells = Math.max(1, (int) Math.ceil(maxHealth / barCfg.segmentHp));
            if (cells > 64) cells = 64;
        }
        return Math.max(1, cells);
    }

    /** 长条变体 3：金属高光——纯色填充 + 顶部 1px 提亮 + 底部 1px 压暗 */
    private void glossyFill(VertexConsumer vc, Matrix4f m, int x, int y, int w, int h, int color) {
        fillRect(vc, m, x, y, w, h, color);
        if (h >= 3) {
            fillRect(vc, m, x, y, w, 1, ColorHelper.lerp(color, 0xFFFFFFFF, 0.45f));
            fillRect(vc, m, x, y + h - 1, w, 1, ColorHelper.lerp(color, 0xFF000000, 0.35f));
        }
    }

    private int fillColor(EntityStatusSnapshot snap, float ratio) {
        var cfg = ConfigManager.getConfig();
        var colors = cfg.colors;
        var barCfg = cfg.barStyle;
        if (snap.poisoned) return ColorHelper.parseColor(colors.healthPoison);
        if (snap.withered) return ColorHelper.parseColor(colors.healthWither);
        if (snap.frozen) return ColorHelper.parseColor(colors.healthFrozen);
        if (barCfg.healthBarHealthColorDynamic) {
            return ColorHelper.lerp(
                    ColorHelper.parseColor(colors.mobBarHealthEmpty),
                    ColorHelper.parseColor(colors.mobBarHealthFull),
                    ratio);
        }
        return ColorHelper.parseColor(colors.mobBarHealth);
    }

    // ================= 调试：AsteorBar 原版复刻（逐行移植自反编译 EntityRenderer.render） =================

    private static final ResourceLocation LIGHTMAP_ORIGINAL =
            new ResourceLocation(Z80ZHealthBar.MOD_ID, "textures/ui/lightmap_original.png");

    /**
     * 原版复刻（仅调试，barStyle.originalRender 开启时替换本类 renderBar）：
     * 填充/空槽用 lightmap 贴图渐变采样（fill v0.625-1.0 / empty v0-0.375，uv2=0xFF00FF），
     * 吸收按边框环逐段填充（renderBound 算法）+ 条下小格；数值文本居中、吸收值黄色左置。
     * 原版实体条不做中毒/凋零/冰冻着色（那是玩家 HUD 逻辑），此处同样不做。
     */
    private void renderBarOriginal(PoseStack poseStack, MultiBufferSource buffer, Font font,
                                   EntityStatusSnapshot snap, int x, int y, int barWidth) {
        var cfg = ConfigManager.getConfig();
        var barCfg = cfg.barStyle;
        var colors = cfg.colors;

        float dist = Mth.sqrt((float) snap.distanceSqr);
        float layerDist = Math.max(0.002f, dist * 0.002f); // 原版用距离做深度分层，防止重叠条 z-fighting
        int alpha = barCfg.barAlpha;                        // 原版语义：0 = 不修改，非 0 = 按比例乘

        int left = x;
        int top = y;
        int right = x + barWidth;
        int bottom = y + barCfg.barHalfHeight * 2;
        int halfWidth = barWidth / 2;

        float healthRate = Math.min(snap.plainHealthRatio(), 1f);
        int healthWidth = (int) (halfWidth * 2 * healthRate);
        // 动态色：血量越高越接近"满血色"（lerp 第三参 = 1 时取第二参，故空色在前）
        int colorHealth = barCfg.healthBarHealthColorDynamic
                ? ColorHelper.lerp(ColorHelper.parseColor(colors.mobBarHealthEmpty),
                        ColorHelper.parseColor(colors.mobBarHealthFull), healthRate)
                : ColorHelper.parseColor(colors.mobBarHealth);
        colorHealth = modifyAlphaOriginal(colorHealth, alpha);
        int colorEmpty = modifyAlphaOriginal(ColorHelper.parseColor(colors.mobBarEmpty), alpha);

        VertexConsumer vc = buffer.getBuffer(ModRenderType.originalBar(LIGHTMAP_ORIGINAL));
        Matrix4f matrix = poseStack.last().pose();

        if (healthWidth > 0) {
            gradientQuad(vc, matrix, left, top, left + healthWidth, bottom, colorHealth, layerDist, 0.625f, 1.0f);
        }
        if (healthWidth < halfWidth * 2) {
            gradientQuad(vc, matrix, left + healthWidth, top, right, bottom, colorEmpty, layerDist, 0.0f, 0.375f);
        }

        int boundWidth = barCfg.barBoundWidth;
        int colorAbsorption = modifyAlphaOriginal(ColorHelper.parseColor(colors.mobBarAbsorption), alpha);
        int colorBound = modifyAlphaOriginal(ColorHelper.parseColor(colors.mobBarBound), alpha);
        boolean includeVertex = barCfg.barBoundVertex;

        // 吸收环（原版 renderBound 逐段算法）：absorptionRate 的小数部分决定环上"染色长度"
        int absorptionNum = 0;
        float absorptionWidth = 0f;
        if (snap.absorption > 0) {
            float absorptionRate = snap.absorption / snap.maxHealth;
            absorptionNum = (int) Math.floor(absorptionRate);
            absorptionRate -= absorptionNum;
            absorptionWidth = Math.round((halfWidth * 2 + boundWidth * 2) * absorptionRate);
            if (absorptionWidth == 0 && absorptionNum > 0) {
                absorptionWidth = halfWidth * 2 + boundWidth * 2;
                absorptionNum--;
            }
        }
        renderBoundOriginal(vc, poseStack, left, top, right, bottom,
                (int) absorptionWidth, boundWidth, colorAbsorption, colorBound, includeVertex, layerDist);

        // 条下小格：每个完整吸收环一格；环过多时原版改用乘数文本（扩展钩子），此处封顶 16 格
        if (absorptionNum > 0 && absorptionNum <= 16) {
            int expand = includeVertex ? boundWidth : 0;
            for (int i = 0; i < absorptionNum; i++) {
                solidQuad(vc, matrix, left - expand + i * boundWidth * 2, bottom + boundWidth * 2,
                        left - expand + i * boundWidth * 2 + boundWidth, bottom + boundWidth * 3,
                        colorAbsorption, layerDist);
            }
        }

        // 文本：健康值居中、吸收值黄色左置（原版 GuiHelper.renderCenteredString / renderString 语义）
        if (font != null) {
            poseStack.pushPose();
            float ts = (float) barCfg.barTextScale;
            float centerX = left + halfWidth;
            float centerY = top + barCfg.barHalfHeight + (float) barCfg.barTextOffsetY;
            poseStack.translate(centerX, centerY, 0);
            poseStack.scale(ts, ts, 1f);
            // 原版复刻：忠实显示小数（不受"数值取整"影响）
            String healthStr = MobHealthBarStyle.formatNumber(snap.health) + "/"
                    + MobHealthBarStyle.formatNumber(snap.maxHealth);
            font.drawInBatch(healthStr, -font.width(healthStr) / 2f, -4, 0xFFFFFF, false,
                    poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            if (snap.absorption > 0) {
                String absStr = MobHealthBarStyle.formatNumber(snap.absorption);
                font.drawInBatch(absStr, (-halfWidth + 1) / ts, 0, 0xFFFF00, false,
                        poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
            poseStack.popPose();
        }
    }

    /** 原版 modifyAlpha：alpha==0 表示"不修改"（非透明度 0） */
    private static int modifyAlphaOriginal(int color, int alpha) {
        if (alpha == 0) return color;
        int a = (color >> 24) * alpha / 255;
        return (color & 0xFFFFFF) | (a << 24);
    }

    /** lightmap 渐变矩形：v 范围决定采样区（fill=0.625-1.0 偏暗 / empty=0-0.375 偏亮），uv2 恒 0xFF00FF */
    private static void gradientQuad(VertexConsumer vc, Matrix4f m, int x1, int y1, int x2, int y2,
                                     int color, float z, float v1, float v2) {
        vc.vertex(m, x1, y1, z).color(color).uv(0f, v1).uv2(0xFF00FF).endVertex();
        vc.vertex(m, x1, y2, z).color(color).uv(0f, v2).uv2(0xFF00FF).endVertex();
        vc.vertex(m, x2, y2, z).color(color).uv(1f, v2).uv2(0xFF00FF).endVertex();
        vc.vertex(m, x2, y1, z).color(color).uv(1f, v1).uv2(0xFF00FF).endVertex();
    }

    private static void solidQuad(VertexConsumer vc, Matrix4f m, int x1, int y1, int x2, int y2,
                                  int color, float z) {
        gradientQuad(vc, m, x1, y1, x2, y2, color, z, 0.625f, 1.0f);
    }

    /** 原版 GuiHelper.renderBound 逐行移植：吸收从左下角开始沿环染色，未染色段用 colorEmpty */
    private static void renderBoundOriginal(VertexConsumer vc, PoseStack poseStack,
                                            int left, int top, int right, int bottom,
                                            int width, int boundWidth, int colorFill, int colorEmpty,
                                            boolean vertex, float z) {
        Matrix4f m = poseStack.last().pose();
        int cut = 0;
        int expand = vertex ? boundWidth : 0;
        if (width > 0) {
            int part = Math.min(width, boundWidth);
            cut += part;
            solidQuad(vc, m, left - boundWidth, top - expand, left - boundWidth + part, bottom + expand, colorFill, z);
            if (part < boundWidth) {
                solidQuad(vc, m, left - boundWidth + part, top - expand, left, bottom + expand, colorEmpty, z);
            }
        } else {
            solidQuad(vc, m, left - boundWidth, top - expand, left, bottom + expand, colorEmpty, z);
        }
        if (width > right - left + boundWidth) {
            int part = Math.min(width, boundWidth);
            cut += part;
            solidQuad(vc, m, right, top - expand, right + part, bottom + expand, colorFill, z);
            if (part < boundWidth) {
                solidQuad(vc, m, right + part, top - expand, right + boundWidth, bottom + expand, colorEmpty, z);
            }
        } else {
            solidQuad(vc, m, right, top - expand, right + boundWidth, bottom + expand, colorEmpty, z);
        }
        width -= cut;
        if (width > 0) {
            solidQuad(vc, m, left, top - boundWidth, left + width, top, colorFill, z);
            solidQuad(vc, m, left, bottom, left + width, bottom + boundWidth, colorFill, z);
        }
        if (width < right - left) {
            solidQuad(vc, m, left + width, top - boundWidth, right, top, colorEmpty, z);
            solidQuad(vc, m, left + width, bottom, right, bottom + boundWidth, colorEmpty, z);
        }
    }

    // ================= 样式 B：心形牌匾行 =================

    /** 每层心数上限（每层 10 心 = 20 HP；超出按 DNF 式叠加换色层） */
    private static final int HEART_ROW_CAP = 10;

    /** 分层配色（层索引循环，DNF 式多层血条：0 红 → 1 金 → 2 绿 → 3 青 → 4 紫 → 5 品红） */
    private static final int[] LAYER_COLORS = {
            0xFFE02020, 0xFFFFC028, 0xFF38C858, 0xFF30B8E0, 0xFFA860E8, 0xFFE850A8,
    };

    /**
     * 自绘白色心形贴图（18x9 双区；原版图标为彩色贴图无法分层换色）：
     * 左半区 = 容器心大轮廓（54 像素，比实心心大一圈——实心心叠上后露出的一圈即原版边框），
     * 右半区 = 实心心小轮廓（34 像素）；高光像素由渲染器单独补绘。
     */
    private static final ResourceLocation HEART_WHITE =
            new ResourceLocation(Z80ZHealthBar.MOD_ID, "textures/gui/heart_white.png");

    @Override
    public void renderPlaque(PoseStack poseStack, MultiBufferSource buffer,
                              EntityStatusSnapshot snap, int x, int y,
                              Font font, int packedLight, float alpha, float worldScale) {
        int maxHearts = (int) Math.ceil(snap.maxHealth / 2.0f);
        if (maxHearts <= 0) return;
        int slots = Math.min(maxHearts, HEART_ROW_CAP);
        int layers = (maxHearts + HEART_ROW_CAP - 1) / HEART_ROW_CAP;
        float cur = Math.max(0f, snap.health) / 2.0f; // 实际心数（可为 .5 半心）

        VertexConsumer vc = buffer.getBuffer(ModRenderType.tintedIcon(HEART_WHITE));
        Matrix4f matrix = poseStack.last().pose();
        int a = (int) (alpha * 255);

        // 空槽：容器心（贴图左半区，比实心心大一圈）——实心心叠上后露出的一圈即原版边框
        int containerColor = ColorHelper.modifyAlpha(0xFF34343C, a);
        for (int i = 0; i < slots; i++) {
            containerQuad(vc, matrix, x + i * 9, y, containerColor);
        }

        // 分层填充（DNF 式叠加）：每层 10 心，上层覆盖下层，下层颜色从上层空位透出。
        // 仅需绘制当前层与其下一层——更低层被下一层完整覆盖（10 槽满载），视觉等价
        int top = Math.min(layers - 1, Math.max(0, (int) Math.floor((cur - 0.001f) / HEART_ROW_CAP)));
        for (int layer = Math.max(0, top - 1); layer <= top; layer++) {
            float cap = Math.min(HEART_ROW_CAP, maxHearts - layer * HEART_ROW_CAP);
            float remain = Mth.clamp(cur - layer * (float) HEART_ROW_CAP, 0f, cap);
            if (remain <= 0f) continue;
            int color = layerColor(snap, layer, top, a);
            int hi = lighten(color, 0.55f); // 高光:提亮色补绘(乘性染色贴图无法自带高光)
            int full = (int) Math.floor(remain);
            for (int i = 0; i < full && i < slots; i++) {
                heartQuad(vc, matrix, x + i * 9, y, color, false);
                heartHighlight(vc, matrix, x + i * 9, y, hi);
            }
            if (full < slots && remain - full > 0.01f) {
                heartQuad(vc, matrix, x + full * 9, y, color, true); // 半心
                heartHighlight(vc, matrix, x + full * 9, y, hi);
            }
        }

        // 伤害残影：[当前心数, 掉血前心数] 区域内的槽位画白色渐隐心（BarFx 区域渐隐模型——
        // 区域透明度在 420ms 内线性降到 0 后区域清空,无渐近尾巴）。半心损失（满心→半心）
        // 画右半白心渐隐——消失的是右半,不能叠在左半红心上（红+白=粉色）。
        var dxCfg = ConfigManager.getConfig().dynamicFx;
        if (dxCfg.enabled && dxCfg.ghost) {
            float target = Mth.clamp(Math.max(0f, snap.health) / snap.maxHealth, 0f, 1f);
            var st = BarFx.tick(snap.entityId, target, snap.hurtTime > 0, System.currentTimeMillis());
            float ghostA = Math.max(0f, Math.min(1f, st.ghostAlpha()));
            float preHitHearts = Math.max(0f, Math.min(1f, st.preHit())) * slots;
            if (ghostA > 0f) {
                int ghostStart = (int) Math.ceil(cur - 0.01f);
                boolean halfCur = cur - Math.floor(cur) > 0.01f;
                // 半心损失（满心→半心）:该槽画右半白心渐隐
                if (halfCur && preHitHearts > Math.floor(cur)) {
                    float ga = Math.min(1f, preHitHearts - cur) * ghostA;
                    int wa = (int) (a * ga * 0.8f);
                    heartRightQuad(vc, matrix, x + (int) cur * 9, y, ColorHelper.modifyAlpha(0xFFFFFFFF, wa));
                }
                for (int i = ghostStart; i < slots; i++) {
                    float g = preHitHearts - i; // 该槽残影覆盖 (0..1]
                    if (g <= 0f) break;
                    int wa = (int) (a * Math.min(1f, g) * ghostA * 0.8f);
                    heartQuad(vc, matrix, x + i * 9, y, ColorHelper.modifyAlpha(0xFFFFFFFF, wa), false);
                }
            }
        }
    }

    /** 颜色向白提亮(0..1);用于心形高光 */
    private static int lighten(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 高光像素(实心区 (2,2) 单像素):1x1 四边形,uv 对齐右半区该像素 */
    private static void heartHighlight(VertexConsumer vc, Matrix4f m, int x, int y, int color) {
        float u1 = 11f / 18f, u2 = 12f / 18f;
        float v1 = 2f / 9f, v2 = 3f / 9f;
        vc.vertex(m, x + 2, y + 2, 0).uv(u1, v1).color(color).endVertex();
        vc.vertex(m, x + 2, y + 3, 0).uv(u1, v2).color(color).endVertex();
        vc.vertex(m, x + 3, y + 3, 0).uv(u2, v2).color(color).endVertex();
        vc.vertex(m, x + 3, y + 2, 0).uv(u2, v1).color(color).endVertex();
    }

    /** 层色：按层索引循环取色；当前顶层的颜色可被状态效果/吸收覆盖（保底红心） */
    private static int layerColor(EntityStatusSnapshot snap, int layer, int top, int alpha) {
        int base;
        if (layer == top) {
            if (snap.absorption > 0) base = 0xFFFFE173;      // 吸收：金心
            else if (snap.poisoned) base = 0xFF7FCC44;       // 中毒：绿心
            else if (snap.withered) base = 0xFF4F2727;       // 凋零：暗心
            else if (snap.frozen) base = 0xFF3798F4;         // 冰冻：蓝心
            else base = LAYER_COLORS[layer % LAYER_COLORS.length];
        } else {
            base = LAYER_COLORS[layer % LAYER_COLORS.length];
        }
        return ColorHelper.modifyAlpha(base, alpha);
    }

    /** 容器心四边形（贴图左半区 9x9：54 像素大轮廓）；空槽与边框的来源 */
    private static void containerQuad(VertexConsumer vc, Matrix4f m, int x, int y, int color) {
        vc.vertex(m, x, y, 0).uv(0f, 0f).color(color).endVertex();
        vc.vertex(m, x, y + 9, 0).uv(0f, 1f).color(color).endVertex();
        vc.vertex(m, x + 9, y + 9, 0).uv(0.5f, 1f).color(color).endVertex();
        vc.vertex(m, x + 9, y, 0).uv(0.5f, 0f).color(color).endVertex();
    }

    /** 实心心四边形（贴图右半区 9x9：34 像素小轮廓）；half = 左半 5px 半心 */
    private static void heartQuad(VertexConsumer vc, Matrix4f m, int x, int y,
                                  int color, boolean half) {
        float u1 = 0.5f, u2 = half ? 0.5f + 5f / 18f : 1f;
        int w = half ? 5 : 9;
        vc.vertex(m, x, y, 0).uv(u1, 0f).color(color).endVertex();
        vc.vertex(m, x, y + 9, 0).uv(u1, 1f).color(color).endVertex();
        vc.vertex(m, x + w, y + 9, 0).uv(u2, 1f).color(color).endVertex();
        vc.vertex(m, x + w, y, 0).uv(u2, 0f).color(color).endVertex();
    }

    /** 右半心四边形（实心心右侧 4px）：半心残影用——"满心→半心"损失的是右半,残影画右半而非叠在左半红心上 */
    private static void heartRightQuad(VertexConsumer vc, Matrix4f m, int x, int y, int color) {
        float u1 = 0.5f + 5f / 18f, u2 = 1f;
        vc.vertex(m, x + 5, y, 0).uv(u1, 0f).color(color).endVertex();
        vc.vertex(m, x + 5, y + 9, 0).uv(u1, 1f).color(color).endVertex();
        vc.vertex(m, x + 9, y + 9, 0).uv(u2, 1f).color(color).endVertex();
        vc.vertex(m, x + 9, y, 0).uv(u2, 0f).color(color).endVertex();
    }

    @Override
    public int getBarWidth(EntityStatusSnapshot snap) {
        // 固定半宽（barHalfWidth>0）优先；否则自动跟随最大血量（每点 2px，钳制 20-80px）
        int half = ConfigManager.getConfig().barStyle.barHalfWidth;
        if (half > 0) return Math.max(8, Math.min(160, half * 2));
        return Math.min(80, Math.max(20, (int) (snap.maxHealth * 2)));
    }

    @Override
    public int getPlaqueWidth(Font font, EntityStatusSnapshot snap) {
        int hearts = (int) Math.ceil(snap.maxHealth / 2.0f);
        return Math.min(hearts, HEART_ROW_CAP) * 9;
    }

    @Override
    public String getValueText(EntityStatusSnapshot snap) {
        int v = (int) Math.ceil(Math.min(snap.health, snap.maxHealth));
        int abs = Math.round(snap.absorption);
        return abs > 0 ? String.valueOf(v + abs) : String.valueOf(v);
    }

    @Override
    public int getValueColor(EntityStatusSnapshot snap) {
        // FPS 式血量渐变:HSV 色相插值(红 0° → 黄 60° → 绿 120°,S/V 恒满),
        // RGB 直接插值会在中段塌陷成暗橄榄色(用户实测反馈)
        float ratio = Mth.clamp(snap.plainHealthRatio(), 0f, 1f);
        float hue = 0f + (120f / 360f) * ratio;
        return 0xFF000000 | Mth.hsvToRgb(hue, 1.0f, 1.0f);
    }
}
