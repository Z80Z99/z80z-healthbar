package com.z80z99.z80zhealthbar.overlay;

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
     * 扫光带在填充内的区间。
     *
     * @param innerW 条内宽（带宽 = max(6, innerW/6)）
     * @param fillW 当前填充宽（扫光只在有血区域流动）
     * @param now   时间戳
     * @param phase 每条相位偏移（如 entityId*400,防多根条同步扫）
     * @return {start, end} 相对填充左缘的像素区间;不在填充内（如 fillW=0）返回 null
     */
    public static int[] sheenBand(int innerW, int fillW, long now, int phase) {
        if (fillW <= 0) return null;
        int bandW = Math.max(6, innerW / 6);
        long t = Math.floorMod(now + phase, SHEEN_PERIOD_MS);
        double frac = t / (double) SHEEN_PERIOD_MS;
        // 从填充左侧外进入、右侧外离开:pos ∈ [-bandW, fillW]
        int pos = (int) Math.round(-bandW + frac * (fillW + bandW));
        int start = Math.max(0, pos);
        int end = Math.min(fillW, pos + bandW);
        if (end - start <= 0) return null;
        return new int[]{start, end};
    }

    /** 扫光带前缘宽（亮边,占带宽 1/4,至少 1px） */
    public static int sheenEdgeW(int innerW) {
        return Math.max(1, Math.max(6, innerW / 6) / 4);
    }

    /** 受击抖动 y 偏移：flash 衰减期按 50ms 步进取 0/1 序列,幅度随 flash 收敛 */
    public static int shakeOffset(long now, float flash) {
        if (flash <= 0f) return 0;
        int idx = (int) ((now / 50) % SHAKE.length);
        return Math.round(SHAKE[idx] * Math.min(1f, flash) * 2);
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
