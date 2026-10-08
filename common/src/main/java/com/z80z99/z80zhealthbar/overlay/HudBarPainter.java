package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 玩家 HUD 条的统一视觉原语。
 *
 * <p>所有条都使用相同的 2px 内边距、深色卡片、顶部高光和底部压暗；
 * 旧 ASTEORBAR 与 CUSTOM 只负责布局/数据，不再各自复制卡片几何。
 */
public final class HudBarPainter {
    public static final int INSET = 2;

    private HudBarPainter() {}

    public static int innerWidth(int width) {
        return Math.max(0, width - INSET * 2);
    }

    /** 垂直内缩：**已归零**——实测反馈"条填充没填满卡片"。历史版本上下各缩最多 2px
     *  （9px 条填充仅 5px,顶部空带明显）;现填充画满卡片高,顶部高光/底部压暗仍作为
     *  叠加层保留（drawFillWidth 内 innerH>=2/>=4 分支）,卡片视觉语言不变。 */
    public static int insetY(int height) {
        return 0;
    }

    public static int innerHeight(int height) {
        return Math.max(0, height - insetY(height) * 2);
    }

    /** 填充区顶部 y（含垂直内缩）。 */
    public static int fillTop(int y, int height) {
        return y + insetY(height);
    }

    /** 现代紧凑卡片：深色底、冷色细描边、顶部高光、底部 1px 淡投影（投影曾为卡片下方 2px
     *  半透明黑且左右扩边,在游戏背景上呈明显黑块,实测"所有条下面都有黑色区域"）。 */
    public static void drawCard(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        g.fill(x + 1, y + h, x + w, y + h + 1, 0x30000000);
        g.fill(x, y, x + w, y + h, 0xD00B1018);
        g.fill(x, y, x + w, y + 1, 0x8092A8BB);
        g.fill(x, y + 1, x + 1, y + h - 1, 0x605E7588);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, 0x403B4D5D);
        if (h > 2) g.fill(x + 1, y + h - 1, x + w - 1, y + h, 0x6003070C);
    }

    /** 在卡片内绘制指定内宽的填充。 */
    public static void drawFillWidth(GuiGraphics g, int x, int y, int width, int height,
                                     int fillWidth, int color) {
        drawFillWidth(g, x, y, width, height, fillWidth, color, 0);
    }

    /** 在卡片内绘制指定内宽的填充，并支持低值抖动的垂直偏移。 */
    public static void drawFillWidth(GuiGraphics g, int x, int y, int width, int height,
                                     int fillWidth, int color, int yOffset) {
        int innerW = innerWidth(width);
        int innerH = innerHeight(height);
        fillWidth = Math.max(0, Math.min(innerW, fillWidth));
        if (innerW <= 0 || innerH <= 0 || fillWidth <= 0) return;
        int left = x + INSET;
        int top = fillTop(y, height) + yOffset;
        int bottom = top + innerH;
        g.fill(left, top, left + fillWidth, bottom, color);
        if (innerH >= 2) {
            g.fill(left, top, left + fillWidth, top + 1,
                    ColorHelper.lerp(color, 0xFFFFFFFF, 0.22f));
        }
        if (innerH >= 4) {
            g.fill(left, bottom - 1, left + fillWidth, bottom,
                    ColorHelper.lerp(color, 0xFF000000, 0.24f));
        }
    }

    /** 在卡片内绘制从 start 到 end 的叠加段（吸收/饱和度/兼容状态）。 */
    public static void drawSegment(GuiGraphics g, int x, int y, int width, int height,
                                   int start, int end, int color) {
        drawSegment(g, x, y, width, height, start, end, color, 0);
    }

    /** 叠加段（带 y 偏移——受击抖动等动态位移用,与填充的 yOffset 通道一致）。 */
    public static void drawSegment(GuiGraphics g, int x, int y, int width, int height,
                                   int start, int end, int color, int yOffset) {
        int innerW = innerWidth(width);
        int innerH = innerHeight(height);
        start = Math.max(0, Math.min(innerW, start));
        end = Math.max(start, Math.min(innerW, end));
        if (end <= start || innerH <= 0) return;
        g.fill(x + INSET + start, fillTop(y, height) + yOffset,
                x + INSET + end, fillTop(y, height) + yOffset + innerH, color);
    }

    /** 在卡片内按比例绘制填充，ratio 会被钳制到 0..1。 */
    public static void drawRatioFill(GuiGraphics g, int x, int y, int width, int height,
                                     float ratio, int color) {
        drawFillWidth(g, x, y, width, height,
                Math.round(Math.max(0f, Math.min(1f, ratio)) * innerWidth(width)), color);
    }

    /** 统一文本可用宽度，避免居中数字盖住卡片边框。 */
    public static int clampTextX(int x, int width, int textWidth, int requestedX) {
        int min = x + INSET;
        int max = x + width - INSET - textWidth;
        if (max < min) return min;
        return Math.max(min, Math.min(max, requestedX));
    }
}
