package com.z80z99.z80zhealthbar.util;

import net.minecraft.util.Mth;

/**
 * 伤害飘字运动/淡出/锚点纯函数（2026-10-08 抽取——渲染器内联公式全部集中到此,可单测）。
 * 主题默认参数（rise/tilt/drift）在此与渲染器共享同一事实来源。
 */
public final class PopupFx {

    private PopupFx() {}

    /** 生成锚点世界 Y:HEAD=头顶 / BAR=血条上方(头顶+0.75,与三样式条底基线≈头顶+0.5 再留条高) / CENTER=实体中心 / FEET=脚上 */
    public static double originY(String origin, double headY, double feetY) {
        return switch (origin == null ? "HEAD" : origin) {
            case "BAR" -> headY + 0.75;
            case "CENTER" -> (headY + feetY) / 2.0;
            case "FEET" -> feetY + 0.25;
            default -> headY;
        };
    }

    /** 上升距离(px):覆盖开启用配置值,否则主题默认 */
    public static float risePx(String theme, boolean override, int overridePx) {
        if (override) return Mth.clamp(overridePx, 0, 80);
        return switch (theme) {
            case "APEX" -> 16f;
            case "TACTICAL" -> 24f;
            case "WARFRAME" -> 34f;
            case "MINIMAL" -> 12f;
            default -> 20f;
        };
    }

    /** 出生冲击缩放:1 + 强度×sin(相位·π)（相位 0..1,180ms 内的正弦过冲）;强度≤0 恒 1 */
    public static float punchScale(float strength, float phase) {
        if (strength <= 0f) return 1f;
        return 1f + strength * Mth.sin(Mth.clamp(phase, 0f, 1f) * (float) Math.PI);
    }

    /** 淡出透明度:淡出起点前恒 1,之后线性到 0（fadeStart ∈ [0.3,0.95]） */
    public static float fadeAlpha(float t, float fadeStart) {
        if (t < fadeStart) return 1f;
        return Math.max(0f, 1f - (t - fadeStart) / (1f - fadeStart));
    }

    /** SWAY 水平摆动:全程 1.5 个正弦周期,相位按种子错开,幅度 10px */
    public static float swayX(float t, int seed) {
        return Mth.sin(t * (float) (Math.PI * 3f) + seedAngle(seed)) * 10f;
    }

    /** 水平随机散布:种子确定性 ±amp */
    public static float driftX(int seed, float amp) {
        return (seedToUnit(seed) * 2f - 1f) * amp;
    }

    /** 倾斜角:覆盖开启用配置值,否则主题默认（WARFRAME -6°,其余 0） */
    public static float tiltDeg(String theme, boolean override, int overrideDeg) {
        if (override) return Mth.clamp(overrideDeg, -45, 45);
        return theme.equals("WARFRAME") ? -6f : 0f;
    }

    /** 种子 → [0,1) 确定性均匀散列（splitmix 风格）;渲染器/设置页预览共用 */
    public static float seedToUnit(int seed) {
        int h = seed * 0x9E3779B9;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0x00FFFFFF) / (float) 0x01000000;
    }

    private static float seedAngle(int seed) {
        return seedToUnit(seed) * (float) (Math.PI * 2);
    }
}
