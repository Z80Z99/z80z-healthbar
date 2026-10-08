package com.z80z99.z80zhealthbar.overlay;

import java.util.Map;

/**
 * 血条动态效果的第二层纯函数（状态层仍是 BarFx：display/ghost/flash/heal）。
 * 本类只做"时间 → 视觉参数"计算，全部可纯 JVM 单测：
 * <ul>
 *   <li>{@link #pulse(long)} 低血呼吸明暗系数</li>
 *   <li>{@link #sheenBand(int, int, long, int)} 扫光带区间（裁剪在填充内）</li>
 *   <li>{@link #shakeOffset(long, float)} 受击抖动 y 偏移</li>
 *   <li>{@link #fadeAlpha(long, long, boolean)} 出现/消失淡入淡出透明度</li>
 *   <li>{@link #textTint(float, float)} 数字受伤红/治疗绿颜色混合系数</li>
 * </ul>
 */
public final class HudFx {

    /** 低血呼吸周期（ms/rad）：190 → 约 1.2s 一个完整呼吸（更快更醒目） */
    public static final double PULSE_PERIOD_MS = 190.0;
    /** 扫光完整周期（ms） */
    public static final long SHEEN_PERIOD_MS = 2600;
    /** 抖动序列（与 SimpleBarOverlay.SHIFT 同款 0/1 伪随机,独立副本避免跨类耦合） */
    private static final int[] SHAKE = {0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1};

    private HudFx() {}

    /** 低血脉冲系数 0..1（呼吸曲线,渲染端 fill = lerp(fill, 提亮, p·0.35)） */
    public static float pulse(long now) {
        return (float) (0.5 + 0.5 * Math.sin(now / PULSE_PERIOD_MS));
    }

    /**
     * 扫光带在填充内的区间（2026-10-08 重做,单调右行版）。
     *
     * <p>旧版"数据减少向左扫"的方向反转经 floorMod 回绕会在条的左右两端瞬移（视觉跳变）,
     * 且整带为纯色方块。重做:相位只增不减（右行循环）,条带由渲染端按亮度切片绘制成
     * 渐变（见 {@link #sheenStripAlpha}）;两端各有一小段完全出条区间,回绕不可见。
     *
     * @param innerW 条内宽（光晕带宽 = max(10, innerW/5)）
     * @param fillW 当前填充宽（扫光只在有血区域流动）
     * @param phaseMs 累计相位（ms;速度由 {@link #advanceSheen} 调制）
     * @return {start, end} 相对填充左缘的像素区间;不在填充内（如 fillW=0）返回 null
     */
    public static int[] sheenBandPhase(int innerW, int fillW, double phaseMs) {
        if (fillW <= 0) return null;
        int bandW = Math.max(10, innerW / 5);
        double t = phaseMs / (double) SHEEN_PERIOD_MS;
        double frac = t - Math.floor(t);
        // 从填充左侧外进入、右侧外离开:pos ∈ [-bandW, fillW]
        int pos = (int) Math.round(-bandW + frac * (fillW + bandW));
        int start = Math.max(0, pos);
        int end = Math.min(fillW, pos + bandW);
        if (end - start <= 0) return null;
        return new int[]{start, end};
    }

    /** 旧签名兼容（纯时间相位） */
    public static int[] sheenBand(int innerW, int fillW, long now, int phase) {
        return sheenBandPhase(innerW, fillW, now + phase);
    }

    // ---- 自适应扫光（数值变化 1.0× 速、静止 0.5× 速,方向随数据增减） ----
    // int key（实体 ID / 固定组件编号）——此前 String key 每帧拼接（"mob."+id）且表无界增长
    private static final Map<Integer, double[]> SHEEN = new java.util.HashMap<>(); // {phase, lastMs, lastFill}
    private static final int SHEEN_STALE_MS = 10_000;

    /**
     * 自适应扫光相位推进：静止 0.5× 速、数值变化 1.0× 速（单调右行——不再随增减反转方向）。
     *
     * @param key    相位跟踪键（实体 ID;玩家组件用负编号）
     * @param fillW  当前填充宽（变化检测;==0 视为静止）
     * @param now    时间戳
     * @param changing 数据是否正在变化（如 BarFx display 与 target 差 > ε）
     * @return 累计相位（ms）——直接喂 sheenBandPhase
     */
    public static double advanceSheen(int key, int fillW, long now, boolean changing) {
        double[] st = SHEEN.get(key);
        if (st == null) {
            if (SHEEN.size() > 256) { // 有界清理:整体重建（存活条目 << 256,代价可忽略）
                SHEEN.entrySet().removeIf(e -> now - e.getValue()[1] > SHEEN_STALE_MS);
            }
            st = new double[]{0.0, now, fillW};
            SHEEN.put(key, st);
            return 0.0;
        }
        long dt = Math.max(0, Math.min(100, now - (long) st[1]));
        double prevFill = st[2];
        boolean grew = fillW > prevFill + 0.5;
        boolean shrank = fillW < prevFill - 0.5;
        double speed = (changing || grew || shrank) ? 1.0 : 0.5;
        st[0] += dt * speed;
        st[1] = now;
        st[2] = fillW;
        return st[0];
    }

    /**
     * 扫光单条带的白色 alpha（0..0x66）——渐变分布,非平铺方块：
     * <ul>
     *   <li>主体光晕：对称余弦窗（中段最亮、两端渐隐至 0）;峰值 0x2A;</li>
     *   <li>前缘亮点：位于 t≈3/4（右行方向的前部）,窄而亮;峰值 0x55;</li>
     *   <li>两者叠加后整体钳制 0x66（≈40% 白,清晰但不刺眼）。</li>
     * </ul>
     *
     * @param relStart 该切片相对光带左缘的起点（px）
     * @param sliceW   切片宽（px）
     * @param bandW    光带总宽（px）
     */
    public static int sheenStripAlpha(int relStart, int sliceW, int bandW) {
        if (bandW <= 0 || sliceW <= 0) return 0;
        float t = (relStart + sliceW * 0.5f) / bandW;
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        float halo = (float) Math.sin(Math.PI * t);
        halo *= halo;
        float g = 1f - Math.abs(t - 0.75f) / 0.25f;
        if (g < 0f) g = 0f;
        g *= g;
        int a = Math.round(halo * 0x2A) + Math.round(g * 0x55);
        return Math.min(0x66, a);
    }

    /** 扫光切片绘制回调（各管线的 fill 原语签名不同——GuiGraphics.fill / VertexConsumer 矩形） */
    public interface SheenStrip {
        void fill(int x, int w, int alpha);
    }

    /**
     * 把扫光带按亮度切片交给渲染端绘制（x 相对填充左缘;alpha 为白色通道,未乘管线透明度）。
     * 切片步进 ≤ bandW/16,15-30px 的光带约 8-16 次 fill——渐变肉眼连续,开销可忽略。
     */
    public static void drawSheen(int[] band, SheenStrip sink) {
        if (band == null) return;
        int bandW = band[1] - band[0];
        int step = Math.max(1, bandW / 16);
        for (int x = band[0]; x < band[1]; x += step) {
            int w = Math.min(band[1], x + step) - x;
            int a = sheenStripAlpha(x - band[0], w, bandW);
            if (a > 0) sink.fill(x, w, a);
        }
    }

    /** 抖动方式：0=关 1=静态(0/1 序列,原版) 2=平滑比例(正弦,幅度∝强度) 3=动态(幅度∝伤害量) */
    public static final int SHAKE_OFF = 0, SHAKE_STATIC = 1, SHAKE_SMOOTH = 2, SHAKE_DYNAMIC = 3;

    /** 受击抖动 y 偏移（平滑正弦版,取代 0/1 跳变——实测反馈"抖动更平滑"） */
    public static int shakeSmooth(long now, float intensity, float ampPx) {
        if (intensity <= 0f) return 0;
        float osc = (float) Math.sin(now / 40.0); // 40ms 半周期,肉眼平滑
        return Math.round(osc * Math.min(1f, intensity) * ampPx);
    }

    /** 静态抖动（原版 0/1 序列,恒幅 2px） */
    public static int shakeStatic(long now, float intensity) {
        if (intensity <= 0f) return 0;
        int idx = (int) ((now / 50) % SHAKE.length);
        return Math.round(SHAKE[idx] * Math.min(1f, intensity) * 2);
    }

    /** 按抖动方式分派：1 静态（恒幅 0/1 序列）/ 2 平滑比例（正弦,幅度∝受击强度,默认）/
     *  3 动态（幅度∝本次伤害量:小伤害轻抖、大伤害猛抖）/ 其它=关 */
    public static int shakeByMode(int mode, long now, float flash, float lastDamage) {
        if (flash <= 0f) return 0;
        return switch (mode) {
            case SHAKE_STATIC -> shakeStatic(now, flash);
            case SHAKE_SMOOTH -> shakeSmooth(now, flash, 2f);
            case SHAKE_DYNAMIC -> shakeSmooth(now, flash, 1.5f + Math.min(1f, lastDamage * 4f) * 3.5f);
            default -> 0;
        };
    }

    /**
     * 淡入淡出透明度 0..1。
     *
     * @param now        当前时间
     * @param lastChange 可见性最近一次翻转的时间戳
     * @param visible    当前是否应有值
     * @return visible=true：入场 inMs 内 0→1;false：保持 holdMs 后 outMs 内 1→0
     */
    public static float fadeAlpha(long now, long lastChange, boolean visible, long inMs, long holdMs, long outMs) {
        long dt = now - lastChange;
        if (visible) {
            return clamp01(dt / (float) inMs);
        }
        if (dt < holdMs) return 1f;
        return clamp01(1f - (dt - holdMs) / (float) outMs);
    }

    /** 数字变色系数：{红, 绿}——受伤 flash 越大红越深,治疗 heal 越大绿越深 */
    public static float[] textTint(float flash, float heal) {
        return new float[]{clamp01(flash * 0.8f), clamp01(heal * 0.6f)};
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    /** easeOutBack（弹性过冲缓动）：t=0→0,t=1→1,中途超过 1（峰值约 1.10）后回落——弹入动画用 */
    public static float easeOutBack(float t) {
        float x = clamp01(t);
        final float c1 = 1.70158f;
        final float c3 = x - 1f;
        return 1f + (c1 + 1f) * c3 * c3 * c3 + c1 * c3 * c3;
    }

    /**
     * 弹入动画参数（实体血条首次出现）。
     *
     * @param elapsed 出现后经过时间
     * @return {scale, yOff}：scale 从 0.6 弹到 1（带过冲）,yOff 从 -6px 落到 0
     */
    public static float[] popIn(long elapsed) {
        float t = clamp01(elapsed / 220f);
        float scale = 0.6f + 0.4f * easeOutBack(t);
        float yOff = -6f * (1f - t);
        return new float[]{scale, yOff};
    }
}
