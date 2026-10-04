package com.z80z99.z80zhealthbar.util;

public final class ColorHelper {
    private ColorHelper() {}

    /** 解析 ARGB 十六进制字符串 (#AARRGGBB 或 #RRGGBB) */
    public static int parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return 0xFF000000;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) h = "FF" + h;
        try {
            return (int) Long.parseLong(h, 16);
        } catch (NumberFormatException e) {
            return 0xFF000000;
        }
    }

    /** 从 ARGB int 提取 alpha */
    public static int getAlpha(int argb) {
        return (argb >> 24) & 0xFF;
    }

    /** 从 ARGB int 提取红色 */
    public static int getRed(int argb) {
        return (argb >> 16) & 0xFF;
    }

    /** 从 ARGB int 提取绿色 */
    public static int getGreen(int argb) {
        return (argb >> 8) & 0xFF;
    }

    /** 从 ARGB int 提取蓝色 */
    public static int getBlue(int argb) {
        return argb & 0xFF;
    }

    /** 修改 alpha 值(alpha<=0 = 完全透明;原值 0..255 与目标 alpha 相乘缩放)。
     *  注意:早先版本 alpha<=0 时返回原色(全不透明),导致淡出末帧跳回不透明——已修正 */
    public static int modifyAlpha(int argb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        int newAlpha = (argb >> 24 & 0xFF) * a / 255;
        return (argb & 0x00FFFFFF) | (newAlpha << 24);
    }

    /** 在两个颜色之间插值 */
    public static int lerp(int colorA, int colorB, float t) {
        int a = (int) (getAlpha(colorA) + (getAlpha(colorB) - getAlpha(colorA)) * t);
        int r = (int) (getRed(colorA) + (getRed(colorB) - getRed(colorA)) * t);
        int g = (int) (getGreen(colorA) + (getGreen(colorB) - getGreen(colorA)) * t);
        int b = (int) (getBlue(colorA) + (getBlue(colorB) - getBlue(colorA)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 解析颜色；非法返回 null（供配置校验用，不与 parseColor 的回退语义冲突） */
    public static Integer parseColorSafe(String hex) {
        if (hex == null) return null;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) h = "FF" + h;
        if (h.length() != 8) return null;
        try {
            long v = Long.parseLong(h, 16);
            if (v < 0 || v > 0xFFFFFFFFL) return null;
            return (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
