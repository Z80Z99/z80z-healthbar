package com.z80z99.z80zhealthbar.gui;

/**
 * 界面布局通用工具（响应式适配）。
 * <p>所有尺寸以 MC 的 GUI 缩放坐标为基准（Screen#width/height 即 GUI 坐标）；
 * 目标是在任意 GUI Scale / 窗口分辨率下界面都不溢出、不重叠。
 */
public final class GuiLayout {

    private GuiLayout() {}

    /** 宽度钳制到可用区域（两侧各留 pad） */
    public static int fitW(int desired, int screenW, int pad) {
        return Math.max(80, Math.min(desired, screenW - pad * 2));
    }

    /** 高度钳制到可用区域 */
    public static int fitH(int desired, int screenH, int pad) {
        return Math.max(40, Math.min(desired, screenH - pad * 2));
    }

    /** 水平居中并保证面板完全在屏内 */
    public static int centerX(int w, int screenW, int pad) {
        int x = (screenW - w) / 2;
        return Math.max(pad, Math.min(x, screenW - w - pad));
    }

    /** 垂直居中并保证面板完全在屏内 */
    public static int centerY(int h, int screenH, int pad) {
        int y = (screenH - h) / 2;
        return Math.max(pad, Math.min(y, screenH - h - pad));
    }

    /** 把任意矩形夹回屏内（宽高已定，只调 x/y） */
    public static int clampX(int x, int w, int screenW, int pad) {
        return Math.max(pad, Math.min(x, Math.max(pad, screenW - w - pad)));
    }

    public static int clampY(int y, int h, int screenH, int pad) {
        return Math.max(pad, Math.min(y, Math.max(pad, screenH - h - pad)));
    }

    /**
     * 小屏整体缩放系数（0.75..1.0）：用于固定尺寸界面（取色器）在小窗口下的等比缩退。
     * 以 1.0 为基准，屏幕小于参考尺寸时按比例缩小，但不低于 0.75（再小就不清晰）。
     */
    public static float uiScale(int screenW, int screenH, int refW, int refH, int pad) {
        float sx = (screenW - pad * 2) / (float) refW;
        float sy = (screenH - pad * 2) / (float) refH;
        return Math.max(0.75f, Math.min(1f, Math.min(sx, sy)));
    }

    /** 面板内容区最小可用高度（小于此值应折叠面板而不是挤成一条缝） */
    public static boolean tooTightForPanel(int screenH, int panelY, int minBody) {
        return screenH - panelY - 30 < minBody;
    }
}
