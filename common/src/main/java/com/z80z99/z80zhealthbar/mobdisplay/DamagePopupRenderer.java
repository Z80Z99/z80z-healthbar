package com.z80z99.z80zhealthbar.mobdisplay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.DamagePopupConfig;
import com.z80z99.z80zhealthbar.network.packets.DamagePopupPacket;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 伤害跳字渲染（全局通道：GameRenderer.renderLevel TAIL 每帧调用一次）。
 *
 * <p>与实体渲染解耦：跳字锚点在推入时冻结于命中位置，生物死亡/尸体消失后
 * 仍按完整存活时长显示——FPS 语义（Apex/Warframe 的数字不随尸体消失）。
 *
 * <p>主题（参考主流 FPS 命中反馈）：
 * <ul>
 *   <li>{@code APEX} —— Apex Legends：生命伤害白字 + 吸收伤害金色分色小字，击杀红字放大，深底板；</li>
 *   <li>{@code TACTICAL} —— COD/守望：单色白，≥20% 最大生命的大额伤害金色放大，随机水平散布，深描边；</li>
 *   <li>{@code WARFRAME} —— Warframe：按伤害类型配色的大字，-6° 倾斜，深底板，快速上漂；</li>
 *   <li>{@code CLASSIC} —— 经典类型配色 + 匀速上漂 + 细描边；</li>
 *   <li>{@code MINIMAL} —— CS 竞技极简：小号白字纯阴影快消，反馈主体交给准星命中标记。</li>
 * </ul>
 * 动画：从头顶以随机偏角跳出（锥角 = launchAngleDegrees 配置，仅 RISE/ARC）→ 出场过冲弹跳
 * （180ms 正弦过冲）→ easeOutQuad 上漂 → 末段 30% 生命线性淡出。
 */
public final class DamagePopupRenderer {

    private DamagePopupRenderer() {}

    /** 出生过冲时长（ms） */
    private static final long PUNCH_MS = 180;
    /** 跳字全亮度（世界光照不作用于命中反馈） */
    private static final int FULLBRIGHT = 0xF000F0;

    // ================= 全局渲染通道 =================

    /** viewMatrix = GameRendererMixin 在 LevelRenderer.renderLevel 调用瞬间捕获的原版视图矩阵（含摆动等） */
    /** 是否有可绘制跳字(窗口门控) */
    public static boolean popupsPending() {
        return ConfigManager.getConfig().damagePopup.enabled && DamagePopupManager.hasLive();
    }

    public static void renderGlobal(Matrix4f viewMatrix) {
        var cfg = ConfigManager.getConfig().damagePopup;
        if (!cfg.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) return;
        // 透视文字着色器是唯一乘 ColorModulator 的文字变体——世界渲染末尾残留的
        // 着色器颜色会把数字染黑(实测根因),绘制前强制复位为白色
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        long lifetimeMs = cfg.lifetimeTicks * 50L;
        String motion = cfg.motionParsed();
        List<DamagePopupManager.Popup> popups = motion.equals("CUMULATIVE")
                ? DamagePopupManager.cumulativeLive(lifetimeMs)
                : DamagePopupManager.allLive(lifetimeMs);
        if (popups.isEmpty()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        // 远→近:覆盖层内后画者覆盖先画者,距离排序使重叠跳字的层叠确定
        popups.sort((a, b) -> Double.compare(
                (b.x() - camPos.x) * (b.x() - camPos.x) + (b.headY() - camPos.y) * (b.headY() - camPos.y) + (b.z() - camPos.z) * (b.z() - camPos.z),
                (a.x() - camPos.x) * (a.x() - camPos.x) + (a.headY() - camPos.y) * (a.headY() - camPos.y) + (a.z() - camPos.z) * (a.z() - camPos.z)));
        Font font = mc.font;
        long now = System.currentTimeMillis();
        String theme = cfg.themeParsed();
        float launchConeDeg = (float) cfg.launchAngleDegrees;
        double maxDistSqr = (double) ConfigManager.getConfig().visibility.maxDistance
                * ConfigManager.getConfig().visibility.maxDistance;

        // 与实体渲染完全一致的视图变换：原版捕获矩阵 · T(实体坐标 - 相机坐标)
        PoseStack pose = new PoseStack();
        pose.mulPoseMatrix(viewMatrix);

        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        // 堆叠序号必须按各自实体计算:全局列表混着多个实体的跳字,用全局下标会把
        // 新出生的跳字错抬十几层(实测"数字一多就半空出现"),每实体内最老最高、最新贴头
        java.util.Map<Integer, java.util.List<DamagePopupManager.Popup>> byEntity = new java.util.LinkedHashMap<>();
        for (DamagePopupManager.Popup p : popups) {
            byEntity.computeIfAbsent(p.entityId(), k -> new java.util.ArrayList<>()).add(p);
        }

        for (java.util.List<DamagePopupManager.Popup> entityPopups : byEntity.values()) {
            int stack = entityPopups.size();
            for (DamagePopupManager.Popup p : entityPopups) {
                stack--;
                if (!categoryEnabled(cfg, p.category())) continue;
                double dx = p.x() - camPos.x, dy = p.headY() - camPos.y, dz = p.z() - camPos.z;
                double distSqr = dx * dx + dy * dy + dz * dz;
                if (distSqr > maxDistSqr) continue;
                float dist = Mth.sqrt((float) distSqr);

                float t = Mth.clamp((now - p.birthMillis()) / (float) lifetimeMs, 0f, 1f);
                if (t >= 1f) continue; // 存活期满即刻跳过:淡出完成帧与移除帧原子一致,无尾帧窗口
                boolean killed = (p.flags() & DamagePopupPacket.FLAG_KILLED) != 0;
                float rise = 1f - (1f - t) * (1f - t); // easeOutQuad
                float riseAmt = com.z80z99.z80zhealthbar.util.PopupFx.risePx(theme, cfg.animOverride, cfg.animRisePx);
                float punchPhase = Mth.clamp((now - p.birthMillis()) / (float) PUNCH_MS, 0f, 1f);
                float punchStrength = cfg.animOverride ? cfg.animPunchPercent / 100f
                        : (motion.equals("BURST") ? 0.85f : 0.45f);
                float punch = com.z80z99.z80zhealthbar.util.PopupFx.punchScale(punchStrength, punchPhase);
                float s = 0.025f * (float) cfg.scale * baseScale(theme, killed, p) * punch;

                // 随机角度跳出:种子确定性推导相对正上方的偏角(0°=垂直向上=旧行为),仅 RISE/ARC 生效
                float launchSin = 0f, launchCos = 1f;
                if (launchConeDeg > 0f && (motion.equals("RISE") || motion.equals("ARC") || motion.equals("SWAY"))) {
                    float launchRad = (float) Math.toRadians(
                            (seedToUnit(p.seed()) * 2f - 1f) * launchConeDeg);
                    launchSin = Mth.sin(launchRad);
                    launchCos = Mth.cos(launchRad);
                }

                // 运动方式 → 垂直轨迹(像素)与世界下坠(方块)
                float yPx;
                float xPx = 0f;
                float worldDy = 0f;
                switch (motion) {
                    case "ARC" -> {
                        // 弹出到顶点后加速坠落至地面(锚点 headY,地面 feetY);
                        // 发射向量绕锚点旋转:垂直分量随偏角收缩,水平分量随全程 easeOut 展开
                        float riseUp = Mth.sin(Math.min(1f, t * 1.8f) * (float) (Math.PI / 2));
                        float fall = Mth.clamp((t - 0.45f) / 0.55f, 0f, 1f);
                        yPx = -riseAmt * riseUp * launchCos;
                        xPx = launchSin * riseAmt * rise;
                        worldDy = -(float) ((p.headY() - p.feetY() + 0.1) * fall * fall);
                    }
                    case "STACK" -> yPx = -stack * 12; // 塔式堆叠:静止分层,顶旧底新
                    case "CUMULATIVE", "BURST" -> yPx = 0; // BURST:冲击放大后原地淡出(不上升)
                    case "SWAY" -> { // 上升 + 水平正弦摆动(全程 1.5 周期,种子错相)
                        yPx = -riseAmt * rise * launchCos;
                        xPx = launchSin * riseAmt * rise + com.z80z99.z80zhealthbar.util.PopupFx.swayX(t, p.seed());
                    }
                    default -> { // RISE:沿发射方向的直线漂浮
                        yPx = -riseAmt * rise * launchCos;
                        xPx = launchSin * riseAmt * rise;
                    }
                }

                pose.pushPose();
                // 世界空间锚点（方块单位）
                double anchorY = com.z80z99.z80zhealthbar.util.PopupFx.originY(cfg.spawnOriginParsed(), p.headY(), p.feetY());
                pose.translate((float) (p.x() - camPos.x), (float) (anchorY - camPos.y + cfg.offsetY + worldDy),
                        (float) (p.z() - camPos.z));
                pose.mulPose(camera.rotation()); // billboard
                pose.scale(-s, -s, s); // 名牌空间约定：此后为像素单位（+y 向下、+x 屏幕右）
                // 上漂/散布/堆叠必须在缩放之后（像素单位）——放在缩放前会变成 16~34 方块的位移
                float driftAmp = cfg.animOverride ? cfg.animDriftPx : (theme.equals("TACTICAL") ? 6f : 0f);
                float drift = (motion.equals("RISE") || motion.equals("ARC") || motion.equals("SWAY"))
                        ? com.z80z99.z80zhealthbar.util.PopupFx.driftX(p.seed(), driftAmp) * rise : 0f;
                pose.translate(drift + xPx, yPx, 0);
                float tilt = com.z80z99.z80zhealthbar.util.PopupFx.tiltDeg(theme, cfg.animOverride, cfg.animTiltDegrees);
                if (tilt != 0f) pose.mulPose(Axis.ZP.rotationDegrees(tilt));
                drawPopup(font, pose, buffer, cfg, theme, p, killed, t, s);
                pose.popPose();
            }
        }
    }

    private static void drawPopup(Font font, PoseStack poseStack, MultiBufferSource buffer,
                                  DamagePopupConfig cfg, String theme, DamagePopupManager.Popup p,
                                  boolean killed, float t, float s) {
        float fadeStart = cfg.animOverride ? cfg.animFadeStartPercent / 100f : 0.7f;
        float alpha = Mth.clamp(com.z80z99.z80zhealthbar.util.PopupFx.fadeAlpha(t, fadeStart), 0f, 1f);
        int a255 = (int) (255 * alpha);
        if (a255 <= 0) return; // 淡尽即停:存活期后 200ms 保留窗口内不再绘制任何顶点
        boolean outline = theme.equals("TACTICAL") || theme.equals("CLASSIC");
        boolean plate = theme.equals("APEX") || theme.equals("WARFRAME");
        // 伤害总和模式只显示累计数值,不显示命中次数 ×N(其余模式的连击合并保留 ×N 语义)
        String suffix = p.count() > 1 && !cfg.motionParsed().equals("CUMULATIVE")
                ? " ×" + p.count() : "";

        String main;
        String sub = null;
        int mainColor;
        if (theme.equals("APEX")) {
            // 生命伤害主数字(普通白/击杀绯红),吸收部分护盾蓝小字第二行 —— Apex 护盾/生命分色语义
            mainColor = killed ? ColorHelper.parseColor(cfg.colorKill)
                    : ColorHelper.parseColor(cfg.colorHealth);
            main = MobHealthBarStyle.formatNumber(p.healthDamage()) + suffix;
            if (p.absorbed() > 0.01f) {
                sub = "+" + MobHealthBarStyle.formatNumber(p.absorbed());
            }
        } else {
            float total = p.healthDamage() + p.absorbed();
            mainColor = switch (theme) {
                case "TACTICAL" -> isBigHit(p) ? ColorHelper.parseColor(cfg.colorBigHit)
                        : ColorHelper.parseColor(cfg.colorHealth);
                case "MINIMAL" -> ColorHelper.parseColor(cfg.colorHealth);
                default -> categoryColor(cfg, p.category());
            };
            if (killed) mainColor = ColorHelper.parseColor(cfg.colorKill);
            main = MobHealthBarStyle.formatNumber(total) + suffix;
        }

        // 自绘底板（APEX/WARFRAME）：文字四周 3px 内边距 + 1px 顶部高光，先于文字绘制
        if (plate) {
            int w = Math.max(font.width(main), sub == null ? 0 : (int) (font.width(sub) * 0.8f));
            int x0 = -w / 2 - 3, y0 = -2;
            int x1 = w / 2 + 3, y1 = sub != null ? 21 : 11;
            VertexConsumer vc = buffer.getBuffer(ModRenderType.barRect());
            fillQuad(vc, matrix(poseStack), x0, y0, x1, y1,
                    ColorHelper.modifyAlpha(0x8C10141C, a255));
            fillQuad(vc, matrix(poseStack), x0, y0, x1, y0 + 1,
                    ColorHelper.modifyAlpha(0x30FFFFFF, a255));
        }

        Matrix4f matrix = matrix(poseStack);
        if (outline) {
            // ±1 整数像素 8 向描边,画完立即提交:
            // 透视文字类型自带批次内距离排序,同中心的 9 份副本排序键相同而排序不稳定,
            // 同批绘制时描边副本可能被排到主字之后(实测"白边黑芯"反转)——分层提交不参与排序
            int dark = ColorHelper.modifyAlpha(0xFF101014, a255);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                font.drawInBatch(main, -font.width(main) / 2f + d[0], d[1], dark, false,
                        matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, FULLBRIGHT);
            }
            if (buffer instanceof MultiBufferSource.BufferSource bsource) {
                bsource.endBatch();
            }
        }
        font.drawInBatch(main, -font.width(main) / 2f, 0, ColorHelper.modifyAlpha(mainColor, a255),
                false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, FULLBRIGHT);
        if (sub != null) {
            font.drawInBatch(sub, -font.width(sub) / 2f, 11,
                    ColorHelper.modifyAlpha(ColorHelper.parseColor(cfg.colorAbsorbed), a255),
                    false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, FULLBRIGHT);
        }
    }

    private static Matrix4f matrix(PoseStack poseStack) {
        return poseStack.last().pose();
    }

    private static void fillQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int color) {
        vc.vertex(m, x0, y0, 0).color(color).endVertex();
        vc.vertex(m, x0, y1, 0).color(color).endVertex();
        vc.vertex(m, x1, y1, 0).color(color).endVertex();
        vc.vertex(m, x1, y0, 0).color(color).endVertex();
    }

    // ================= 主题参数 =================

    private static float baseScale(String theme, boolean killed, DamagePopupManager.Popup p) {
        float base = switch (theme) {
            case "WARFRAME" -> 1.15f;
            case "MINIMAL" -> 0.85f;
            case "TACTICAL" -> isBigHit(p) ? 1.25f : 1.0f;
            default -> 1.0f;
        };
        return killed ? base * 1.35f : base;
    }

    /** 种子 → [0,1) 确定性均匀散列（splitmix 风格）;公开供设置页预览与真实渲染保持同角度公式 */
    public static float seedToUnit(int seed) {
        int h = seed * 0x9E3779B9;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0x00FFFFFF) / (float) 0x01000000;
    }

    /** 大额伤害判定：≥ 20% 目标最大生命（COD/守望的"重击"反馈语义） */
    private static boolean isBigHit(DamagePopupManager.Popup p) {
        return p.maxHealth() > 0 && (p.healthDamage() + p.absorbed()) / p.maxHealth() >= 0.2f;
    }

    /** 类别开关（设置页实时预览共用,保持单一事实来源） */
    public static boolean categoryEnabled(DamagePopupConfig cfg, byte category) {
        return switch (category) {
            case DamagePopupPacket.CAT_PROJECTILE -> cfg.showProjectile;
            case DamagePopupPacket.CAT_FIRE -> cfg.showFire;
            case DamagePopupPacket.CAT_EXPLOSION -> cfg.showExplosion;
            case DamagePopupPacket.CAT_MAGIC -> cfg.showMagic;
            case DamagePopupPacket.CAT_FALL -> cfg.showFall;
            default -> cfg.showPhysical;
        };
    }

    /** 类别配色（设置页实时预览共用,保持单一事实来源） */
    public static int categoryColor(DamagePopupConfig cfg, byte category) {
        return switch (category) {
            case DamagePopupPacket.CAT_FIRE -> ColorHelper.parseColor(cfg.colorFire);
            case DamagePopupPacket.CAT_EXPLOSION -> ColorHelper.parseColor(cfg.colorExplosion);
            case DamagePopupPacket.CAT_MAGIC -> ColorHelper.parseColor(cfg.colorMagic);
            case DamagePopupPacket.CAT_PROJECTILE -> ColorHelper.parseColor(cfg.colorProjectile);
            case DamagePopupPacket.CAT_FALL -> ColorHelper.parseColor(cfg.colorFall);
            default -> ColorHelper.parseColor(cfg.colorHealth);
        };
    }

    // ================= 准星命中标记（屏幕空间，FPS hitmarker） =================

    /** 在准星周围绘制 X 命中标记；击杀 320ms 内变红加粗。由 HUD 渲染入口每帧调用。 */
    public static void renderHitMarker(GuiGraphics graphics) {
        var cfg = ConfigManager.getConfig().damagePopup;
        if (!cfg.enabled || !cfg.hitMarker) return;
        long now = System.currentTimeMillis();
        long dt = now - DamagePopupManager.lastHitMillis();
        if (dt < 0 || dt > 320) return;
        float progress = 1f - dt / 320f;
        long killAge = now - DamagePopupManager.lastKillMillis();
        boolean kill = killAge >= 0 && killAge <= 320;
        int argb = ColorHelper.modifyAlpha(kill ? ColorHelper.parseColor(cfg.colorKill)
                : ColorHelper.parseColor(cfg.colorHealth), (int) (255 * progress));

        Minecraft mc = Minecraft.getInstance();
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        int gap = kill ? 5 : 4;
        int len = kill ? 6 : 5;
        int t = kill ? 2 : 1; // 击杀标记加粗

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(45f));
        graphics.fill(gap, -t, gap + len, t, argb);          // 右
        graphics.fill(-gap - len, -t, -gap, t, argb);        // 左
        graphics.fill(-t, -gap - len, t, -gap, argb);        // 上
        graphics.fill(-t, gap, t, gap + len, argb);          // 下
        pose.popPose();
    }
}
