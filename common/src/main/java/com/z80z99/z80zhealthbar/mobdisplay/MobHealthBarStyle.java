package com.z80z99.z80zhealthbar.mobdisplay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.StyleAConfig;
import com.z80z99.z80zhealthbar.mobdisplay.plaques.HeartType;
import com.z80z99.z80zhealthbar.status.EntityStatusSnapshot;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/**
 * 样式 A：YDM's Mob Health Bar 的净室重实现（行为见 docs/source-mod-audit.md §1）。
 *
 * <ul>
 *   <li>变体 0-3：自制贴图外框条（style_a_bars.png，steel/blood/gold/arcane 配色）+ 按血量
 *       比例染色的白色填充条；对应原 MOD 14 种贴图类型中的外框条一族。</li>
 *   <li>变体 4：心形行式（每行 10 颗、多行上堆、半心），对应原 MOD type 4/13。</li>
 *   <li>变体 5：经典宽条——原版 type1 形态的程序化复刻（零贴图采样）。</li>
 *   <li>名称（showName/scaleName）+ "当前/最大" 数值（showHp/scaleNums）。</li>
 * </ul>
 * 旧 textureMode（自制/原版贴图两套变体序列）已并入上述统一变体号，不分家；
 * 心形族（原 type4/13 与自制心形行本就同一渲染路径）收敛为变体 4。
 * 贴图为程序化生成（scripts/GenTextures.java），不复用原 MOD 任何素材（ARR 许可）。
 */
public final class MobHealthBarStyle {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Z80ZHealthBar.MOD_ID, "textures/gui/style_a_bars.png");
    private static final ResourceLocation VANILLA_ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");

    private static final int FRAME_W = 128, FRAME_H = 12;
    private static final int FILL_INSET_X = 3, FILL_INSET_Y = 3;
    private static final int INNER_W = 121, FILL_H = 5;
    private static final int HEART_ROW_SIZE = 10, HEART_SIZE = 9, HEART_ROW_GAP = 1;

    private MobHealthBarStyle() {}

    public static void render(EntityStatusSnapshot snap, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        StyleAConfig cfg = ConfigManager.getConfig().styleA;
        // 不透明度：整体淡出（框架/填充/心形/名称/数值一同生效）
        alpha *= Math.max(0f, Math.min(1f, cfg.opacity / 255f));

        poseStack.pushPose();
        poseStack.translate(0, snap.entityHeight + cfg.heightOffset, 0);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = 0.025f * (float) cfg.scaleBar;
        // 原版名牌矩阵约定：(-s, -s, s)；实测该空间等效 +x 屏幕向右、+y 屏幕向下
        // （与 style 3 文字偏移的实测方向一致）。用正缩放会让 Font 文本整体旋转 180°。
        poseStack.scale(-s, -s, s);
        MobDisplayRenderer.DisplayAnimation.applyScreenFx(poseStack, snap); // 整条动画（像素空间）
        // 原版名牌约定下 scale<1 时条会下沉入模型，做与原 MOD 相同的高度补偿
        if (cfg.scaleBar < 1.0) {
            poseStack.translate(0, 1.5 * (1.0 - cfg.scaleBar) / 0.025, 0);
        } else if (cfg.scaleBar > 1.0) {
            poseStack.translate(0, -(cfg.scaleBar - 1.0) * 1.5 / 0.025, 0);
        }
        // 血条像素偏移仅作用于条本身（不带动文字）；正值=屏幕向右/向下
        poseStack.pushPose();
        poseStack.translate(cfg.offsetX, cfg.offsetY, 0);
        // 独立宽/高拉伸（仅作用于条与心排，不影响文字；≤0 视为 1.0 不拉伸）
        float ws = cfg.scaleBarWidth > 0 ? (float) cfg.scaleBarWidth : 1f;
        float hs = cfg.scaleBarHeight > 0 ? (float) cfg.scaleBarHeight : 1f;
        if (ws != 1f || hs != 1f) poseStack.scale(ws, hs, 1f);
        if (cfg.barType == 1) {
            // 心形排从锚点上堆，会压住名称行（-12）：整体上移一行给名称让位；无名称时保持紧凑
            boolean nameBand = cfg.showName && !snap.displayName.isBlank();
            if (nameBand) poseStack.translate(0, -12, 0);
            renderHeartRows(snap, poseStack, buffer, alpha);
            if (nameBand) poseStack.translate(0, 12, 0);
        } else if (cfg.barType == 2) {
            renderClassicBar(snap, poseStack, buffer, cfg, alpha);
        } else {
            // 越界形状兜底为外框条，避免渲染分支落空
            renderFrameBar(snap, poseStack, buffer, cfg, alpha);
        }
        poseStack.popPose(); // 结束条偏移作用域

        renderTexts(snap, poseStack, buffer, font, cfg, packedLight, alpha);

        poseStack.popPose();
    }

    /** 变体 2：经典宽条——原版 type1 形态的程序化复刻：全程序化绘制(槽底/纯色填充/吸收段/边框)，零贴图采样。
     *  原版贴图为整面斜纹半透明条,缩小采样产生高频黑线闪烁(移动时走样),故此路径彻底不采样贴图。 */
    private static void renderClassicBar(EntityStatusSnapshot snap, PoseStack poseStack,
                                         MultiBufferSource buffer, StyleAConfig cfg, float alpha) {
        float ratio = snap.plainHealthRatio();
        // 横向条几何（原 type1：126x11）
        int w = 126;
        int h = 11;
        int insetX = 3;
        int insetY = 2;
        int fillH = 7;
        int innerW = w - 2 * insetX;

        int x = -w / 2, y = 0;
        VertexConsumer vc = buffer.getBuffer(ModRenderType.barRect());
        Matrix4f m = poseStack.last().pose();
        int a = (int) (alpha * 255);
        int slot = ColorHelper.modifyAlpha(0xFF1D1D22, a);
        // 死亡碎裂记录（样式1像素空间;心排/牌匾变体不记录 → 死亡自动回退渐隐路径）
        ShatterFx.record(snap.entityId, x + w / 2f, y + h / 2f, w, h,
                fillColor(snap, Math.max(0, Math.min(3, cfg.colorVariant))),
                (float) cfg.scaleBar * 0.025f,
                (float) (snap.entityHeight + cfg.heightOffset) - (y + h / 2f) * (float) cfg.scaleBar * 0.025f);
        int border = ColorHelper.modifyAlpha(0xFF6E6E78, a);
        // 动态效果（平滑/残影/闪白）
        var f = barFx(snap);
        var fxCfg = ConfigManager.getConfig().dynamicFx;
        int baseFill = fillColor(snap, Math.max(0, Math.min(3, cfg.colorVariant)));
        if (f.flash > 0.01f) {
            baseFill = ColorHelper.lerp(baseFill, 0xFFFFFFFF, f.flash * 0.6f);
        }
        // 低血脉冲 / 治疗泛光（实体条阈值固定 0.3）
        if (fxCfg.enabled) {
            if (fxCfg.lowHpPulse && snap.plainHealthRatio() <= 0.3f) {
                baseFill = ColorHelper.lerp(baseFill, 0xFFFFFFFF,
                        com.z80z99.z80zhealthbar.overlay.HudFx.pulse(System.currentTimeMillis()) * 0.45f);
            }
            if (fxCfg.healGlow) {
                baseFill = ColorHelper.lerp(baseFill, 0xFF50E080, f.heal * 0.45f);
            }
        }
        int fill = ColorHelper.modifyAlpha(baseFill, a);

        fillQuad(vc, m, x, y, w, h, slot);
        int fillW = Mth.floor(f.disp * innerW);
        int preHitW = Mth.floor(f.preHit * innerW);
        int ghostA = (int) (f.ghostAlpha * a);
        if (preHitW > fillW && ghostA > 0) { // 伤害残影：[当前填充, 掉血前血量] 区域白色渐隐
            fillQuad(vc, m, x + insetX + fillW, y + insetY, preHitW - fillW, fillH,
                    ColorHelper.modifyAlpha(
                            ColorHelper.parseColor(ConfigManager.getConfig().dynamicFx.ghostColor), ghostA));
        }
        if (fillW > 0) fillQuad(vc, m, x + insetX, y + insetY, fillW, fillH, fill);
        if (snap.absorption > 0) { // 吸收段:金色,追加在生命填充之后
            int absW = Mth.floor(Math.min(1f, snap.absorption / snap.maxHealth) * (innerW - fillW));
            if (absW > 0) fillQuad(vc, m, x + insetX + fillW, y + insetY, absW, fillH,
                    ColorHelper.modifyAlpha(0xFFFFE173, a));
        }
        fillQuad(vc, m, x, y, w, 1, border);
        fillQuad(vc, m, x, y + h - 1, w, 1, border);
        fillQuad(vc, m, x, y, 1, h, border);
        fillQuad(vc, m, x + w - 1, y, 1, h, border);
    }

    /** POSITION_COLOR 纯色矩形(barRect 批次) */
    private static void fillQuad(VertexConsumer vc, Matrix4f m, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        vc.vertex(m, x, y, 0).color(color).endVertex();
        vc.vertex(m, x, y + h, 0).color(color).endVertex();
        vc.vertex(m, x + w, y + h, 0).color(color).endVertex();
        vc.vertex(m, x + w, y, 0).color(color).endVertex();
    }

    /** 变体 0：外框条——贴图外框（配色槽位）+ 按血量状态染色的填充 */
    private static void renderFrameBar(EntityStatusSnapshot snap, PoseStack poseStack,
                                       MultiBufferSource buffer, StyleAConfig cfg, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        int variant = Math.max(0, Math.min(3, cfg.colorVariant));
        int x = -FRAME_W / 2, y = 0;
        // 死亡碎裂记录（帧条变体）
        ShatterFx.record(snap.entityId, x + FRAME_W / 2f, y + FRAME_H / 2f, FRAME_W, FRAME_H,
                fillColor(snap, variant), (float) cfg.scaleBar * 0.025f,
                (float) (snap.entityHeight + cfg.heightOffset)
                        - (y + FRAME_H / 2f) * (float) cfg.scaleBar * 0.025f);

        // 外框：白色染色（内容不变），使不透明度可整体淡出
        VertexConsumer frame = buffer.getBuffer(ModRenderType.tintedIcon(TEXTURE));
        float fu1 = (variant * FRAME_W) / 512f, fv1 = 0f;
        float fu2 = fu1 + FRAME_W / 512f, fv2 = FRAME_H / 40f;
        quadColor(frame, matrix, x, y, x + FRAME_W, y + FRAME_H, fu1, fv1, fu2, fv2,
                ColorHelper.modifyAlpha(0xFFFFFFFF, (int) (alpha * 255)));

        // 空槽底色已烘焙进外框贴图（变体槽底色不同，无需再画）
        VertexConsumer empty = buffer.getBuffer(ModRenderType.tintedIcon(TEXTURE));

        // 动态效果（平滑/残影/闪白）
        var f = barFx(snap);
        int fillW = Mth.floor(f.disp * INNER_W);

        // 伤害残影：[当前填充, 掉血前血量] 区域白色渐隐（透明度随时间衰减）
        int preHitW = Mth.floor(f.preHit * INNER_W);
        int ghostA = (int) (f.ghostAlpha * alpha * 255);
        if (preHitW > fillW && ghostA > 0) {
            int ghostColor = ColorHelper.modifyAlpha(
                    ColorHelper.parseColor(ConfigManager.getConfig().dynamicFx.ghostColor),
                    ghostA);
            float gu1 = (variant * FRAME_W + FILL_INSET_X) / 512f, gv1 = 16f / 40f;
            quadColor(empty, matrix, x + FILL_INSET_X + fillW, y + FILL_INSET_Y,
                    x + FILL_INSET_X + preHitW, y + FILL_INSET_Y + FILL_H,
                    gu1 + fillW / 512f, gv1, gu1 + preHitW / 512f, gv1 + FILL_H / 40f, ghostColor);
        }

        // 填充（白色贴图 → 按血量状态染色；受伤时向白闪）
        if (fillW > 0) {
            int baseColor = fillColor(snap, variant);
            if (f.flash > 0.01f) {
                baseColor = ColorHelper.lerp(baseColor, 0xFFFFFFFF, f.flash * 0.6f);
            }
            // 低血脉冲 / 治疗泛光（帧条变体）
            var fxCfg2 = ConfigManager.getConfig().dynamicFx;
            if (fxCfg2.enabled) {
                if (fxCfg2.lowHpPulse && snap.plainHealthRatio() <= 0.3f) {
                    baseColor = ColorHelper.lerp(baseColor, 0xFFFFFFFF,
                            com.z80z99.z80zhealthbar.overlay.HudFx.pulse(System.currentTimeMillis()) * 0.45f);
                }
                if (fxCfg2.healGlow) {
                    baseColor = ColorHelper.lerp(baseColor, 0xFF50E080, f.heal * 0.45f);
                }
            }
            int fillColor = ColorHelper.modifyAlpha(baseColor, (int) (alpha * 255));
            float tu1 = (variant * FRAME_W + FILL_INSET_X) / 512f, tv1 = 16f / 40f;
            quadColor(empty, matrix, x + FILL_INSET_X, y + FILL_INSET_Y,
                    x + FILL_INSET_X + fillW, y + FILL_INSET_Y + FILL_H,
                    tu1, tv1, tu1 + fillW / 512f, tv1 + FILL_H / 40f, fillColor);
        }

        // 吸收：在填充右侧追加金色小段（AsteorBar 式语义，吸收>0 时）
        if (snap.absorption > 0) {
            int absW = Mth.floor(Math.min(1f, snap.absorption / snap.maxHealth) * (INNER_W - fillW));
            if (absW > 0) {
                int absColor = ColorHelper.modifyAlpha(0xFFFFE173, (int) (alpha * 255));
                float au1 = (variant * FRAME_W + FILL_INSET_X) / 512f, av1 = 16f / 40f;
                quadColor(empty, matrix, x + FILL_INSET_X + fillW, y + FILL_INSET_Y,
                        x + FILL_INSET_X + fillW + absW, y + FILL_INSET_Y + FILL_H,
                        au1, av1, au1 + absW / 512f, av1 + FILL_H / 40f, absColor);
            }
        }
    }

    /** 变体 4：心形行式（每行 10 颗、超行上堆；半心用 vanilla 半心图标） */
    private static void renderHeartRows(EntityStatusSnapshot snap, PoseStack poseStack,
                                        MultiBufferSource buffer, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        // 白色染色（内容不变），使不透明度可整体淡出
        VertexConsumer vc = buffer.getBuffer(ModRenderType.tintedIcon(VANILLA_ICONS));
        int white = ColorHelper.modifyAlpha(0xFFFFFFFF, (int) (alpha * 255));

        // 与 vanilla Gui 心形一致的状态选型（坐标见 plaques/HeartType）
        HeartType type = HeartType.NORMAL;
        if (snap.absorption > 0) type = HeartType.ABSORBING;
        else if (snap.poisoned) type = HeartType.POISON;
        else if (snap.withered) type = HeartType.WITHER;
        else if (snap.frozen) type = HeartType.FROZEN;

        int maxHearts = (int) Math.ceil(snap.maxHealth / 2.0f);
        int fullHearts = (int) Math.floor(snap.health / 2.0f);
        // 原版语义：任意余数 > 0 即显示半心
        boolean halfHeart = (snap.health - fullHearts * 2) > 0f;

        int rows = Mth.positiveCeilDiv(maxHearts, HEART_ROW_SIZE);
        for (int row = 0; row < rows; row++) {
            int heartsThisRow = Math.min(HEART_ROW_SIZE, maxHearts - row * HEART_ROW_SIZE);
            int rowY = -(row + 1) * (HEART_SIZE + HEART_ROW_GAP);
            int rowX0 = -(heartsThisRow * HEART_SIZE) / 2;
            for (int i = 0; i < heartsThisRow; i++) {
                int heartIndex = row * HEART_ROW_SIZE + i;
                int hx = rowX0 + i * HEART_SIZE;
                iconQuad(vc, matrix, hx, rowY, HeartType.CONTAINER, white);
                if (heartIndex < fullHearts) {
                    iconQuad(vc, matrix, hx, rowY, type, white);
                } else if (heartIndex == fullHearts && halfHeart) {
                    // 半心 = 满心左半：u..u+4.5 保守取整为 5px
                    float u1 = type.getTextureX() / 256f, v1 = type.getTextureY() / 256f;
                    float u2 = (type.getTextureX() + 5) / 256f, v2 = (type.getTextureY() + 9) / 256f;
                    quadColor(vc, matrix, hx, rowY, hx + 5, rowY + 9, u1, v1, u2, v2, white);
                }
            }
        }
    }

    private static void iconQuad(VertexConsumer vc, Matrix4f m, int x, int y,
                                 HeartType type, int color) {
        float u1 = type.getTextureX() / 256f, v1 = type.getTextureY() / 256f;
        float u2 = (type.getTextureX() + 9) / 256f, v2 = (type.getTextureY() + 9) / 256f;
        // 标准顶点顺序：与原版 Gui.blit/原 Mob Plaques innerBlit 一致（名牌空间无镜像，勿交换 u）
        // color 白色时内容不变；承载不透明度（TINTED 批次要求全顶点带色）
        quadColor(vc, m, x, y, x + 9, y + 9, u1, v1, u2, v2, color);
    }

    /** 配色方案填充色（0 钢红 / 1 血橙 / 2 金绿 / 3 奥术蓝），状态效果优先于配色 */
    private static int fillColor(EntityStatusSnapshot snap, int variant) {
        if (snap.poisoned) return 0xFF7FCC44;
        if (snap.withered) return 0xFF4F2727;
        if (snap.frozen) return 0xFF3798F4;
        return switch (variant) {
            case 1 -> 0xFFE07A20;
            case 2 -> 0xFF40C860;
            case 3 -> 0xFF40A0E0;
            default -> 0xFFE02020;
        };
    }

    /** 条形动态效果状态（平滑后填充比例 / 残影区域上缘 / 残影不透明度 / 受伤闪白强度）；关闭时全部退化为直读值 */
    private record FxState(float disp, float preHit, float ghostAlpha, float flash, float pulse, float heal) {}

    private static FxState barFx(EntityStatusSnapshot snap) {
        var fx = ConfigManager.getConfig().dynamicFx;
        float ratio = snap.plainHealthRatio();
        if (!fx.enabled) return new FxState(ratio, ratio, 0f, 0f, 0f, 0f);
        var st = BarFx.tick(snap.entityId, ratio, snap.hurtTime > 0, System.currentTimeMillis());
        float disp = fx.smooth ? st.display() : ratio;
        float preHit = fx.ghost ? Math.max(st.preHit(), disp) : disp;
        float ghostAlpha = fx.ghost ? st.ghostAlpha() : 0f;
        float flash = fx.hurtFlash ? st.flash() : 0f;
        return new FxState(disp, preHit, ghostAlpha, flash, st.flash(), st.heal()); // pulse 不受闪白开关影响
    }

    private static void renderTexts(EntityStatusSnapshot snap, PoseStack poseStack,
                                    MultiBufferSource buffer, Font font, StyleAConfig cfg,
                                    int packedLight, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        int textColor = ColorHelper.modifyAlpha(0xFFFFFFFF, (int) (alpha * 255));
        int absColor = ColorHelper.modifyAlpha(0xFFFFFF00, (int) (alpha * 255));

        // 名称：固定在锚点上方一行（-12），各形状一致；心形排由渲染侧上移让位
        if (cfg.showName && !snap.displayName.isBlank()) {
            float nameScale = (float) cfg.scaleName;
            // 心形排随高度拉伸时，名称跟随心排下缘移动（其余形状顶部锚定，不受影响）
            float hsN = cfg.scaleBarHeight > 0 ? (float) cfg.scaleBarHeight : 1f;
            float ny = cfg.barType == 1 ? -13f * hsN + 1f : -12;
            poseStack.pushPose();
            poseStack.scale(nameScale, nameScale, 1f);
            font.drawInBatch(snap.displayName,
                    -font.width(snap.displayName) / 2f, ny / nameScale,
                    textColor, false, poseStack.last().pose(), buffer,
                    Font.DisplayMode.SEE_THROUGH, 0, packedLight);
            poseStack.popPose();
        }

        // 数值 "当前/最大(+吸收)" + 数字动态效果（滚动/弹跳/变色）
        if (cfg.showHp) {
            var fxCfg = ConfigManager.getConfig().dynamicFx;
            var f = barFx(snap); // 同帧二次 tick（dt≈0），仅取状态
            float shownHp = fxCfg.enabled && fxCfg.numRoll
                    ? Math.min(snap.maxHealth, f.disp * snap.maxHealth) : snap.health;
            float punchS = fxCfg.enabled && fxCfg.numPunch ? 1f + f.pulse * 0.3f : 1f;
            int hpColor = textColor;
            if (fxCfg.enabled && fxCfg.numTint) {
                if (f.pulse > 0.01f) {
                    hpColor = ColorHelper.lerp(textColor, 0xFFFF5050, f.pulse * 0.8f);
                } else if (f.heal > 0.01f) {
                    hpColor = ColorHelper.lerp(textColor, 0xFF50E080, f.heal * 0.6f);
                }
            }
            float numScale = (float) (cfg.scaleNums * 0.7);
            String hp = formatValue(shownHp) + "/" + formatValue(snap.maxHealth);
            String abs = snap.absorption > 0 ? "+" + formatNumber(snap.absorption) : "";
            // 心形排上移后锚点即其下缘；条形则挂在条底（随高度拉伸下移）
            float hsT = cfg.scaleBarHeight > 0 ? (float) cfg.scaleBarHeight : 1f;
            float barBottom = cfg.barType == 2 ? 11f : FRAME_H;
            float ty = cfg.barType == 1 ? 2 : barBottom * hsT + 2;
            poseStack.pushPose();
            poseStack.scale(numScale, numScale, 1f);
            float tyS = ty / numScale;
            if (punchS > 1.001f) {
                // 弹跳以数值组中心为锚点向外扩散（原先锚在左上角只向下/右生长）
                float cY = tyS + 4f;
                poseStack.translate(0, cY, 0);
                poseStack.scale(punchS, punchS, 1f);
                poseStack.translate(0, -cY, 0);
            }
            Matrix4f m2 = poseStack.last().pose();
            float w = font.width(hp) + (abs.isEmpty() ? 0 : font.width(abs) + 2);
            // 数值文字偏移（像素；正值向右/向下）——与血条偏移独立，文字可单独摆位
            float tx = -w / 2f + (float) cfg.numOffsetX / numScale;
            float tyO = tyS + (float) cfg.numOffsetY / numScale;
            font.drawInBatch(hp, tx, tyO, hpColor, false, m2, buffer,
                    Font.DisplayMode.SEE_THROUGH, 0, packedLight);
            if (!abs.isEmpty()) {
                font.drawInBatch(abs, tx + font.width(hp) + 2, tyO, absColor, false, m2, buffer,
                        Font.DisplayMode.SEE_THROUGH, 0, packedLight);
            }
            poseStack.popPose();
        }
    }

    /** 数值文本格式化（受"数值取整"开关控制）：开=四舍五入（3.6→4），关=保留一位小数（3.6）。
     *  样式 1（本类）与样式 3（HealthDisplayRenderer）共用；样式 2 牌匾为整数设计不走此路径。 */
    public static String formatValue(double v) {
        if (ConfigManager.getConfig().barStyle.integerHealthText) {
            return String.valueOf(Math.round(v));
        }
        return formatNumber(v);
    }

    /** 大数值格式化：≥100 隐藏小数 */
    public static String formatNumber(double v) {
        if (v >= 100) return String.valueOf((long) Math.ceil(v));
        long rounded = Math.round(v * 10);
        if (rounded % 10 == 0) return String.valueOf(rounded / 10);
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /**
     * 样式 A 名牌接管判定（与原 MobHealthBar 行为一致）：本样式对通过可见性规则的实体
     * DENY 原版名牌后自绘名称，避免名字双重显示。样式 B/C 与名牌共存，不抑制。
     * 由 {@code LivingEntityRenderer.shouldShowName} 的 mixin 调用。
     */
    public static boolean suppressesNameTag(LivingEntity entity) {
        var cfg = ConfigManager.getConfig();
        if (cfg.entityStyleParsed() != EntityHealthStyle.MOBHEALTHBAR) return false;
        if (!cfg.barStyle.enableHealthBar) return false;
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        return MobVisibilityChecker.shouldRender(entity, entity.distanceToSqr(player));
    }

    private static void quad(VertexConsumer vc, Matrix4f m, int x1, int y1, int x2, int y2,
                             float u1, float v1, float u2, float v2) {
        vc.vertex(m, x1, y1, 0).uv(u1, v1).endVertex();
        vc.vertex(m, x1, y2, 0).uv(u1, v2).endVertex();
        vc.vertex(m, x2, y2, 0).uv(u2, v2).endVertex();
        vc.vertex(m, x2, y1, 0).uv(u2, v1).endVertex();
    }

    private static void quadColor(VertexConsumer vc, Matrix4f m, int x1, int y1, int x2, int y2,
                                  float u1, float v1, float u2, float v2, int color) {
        vc.vertex(m, x1, y1, 0).uv(u1, v1).color(color).endVertex();
        vc.vertex(m, x1, y2, 0).uv(u1, v2).color(color).endVertex();
        vc.vertex(m, x2, y2, 0).uv(u2, v2).color(color).endVertex();
        vc.vertex(m, x2, y1, 0).uv(u2, v1).color(color).endVertex();
    }
}
