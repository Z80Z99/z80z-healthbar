package com.z80z99.z80zhealthbar.layout;

/** HUD 组件锚点（3×3 网格）。BOTTOM_CENTER 对应原版物品栏上方位置。 */
public enum HudAnchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    MIDDLE_LEFT,
    MIDDLE_CENTER,
    MIDDLE_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    public boolean isTop() { return this == TOP_LEFT || this == TOP_CENTER || this == TOP_RIGHT; }

    public boolean isMiddle() { return this == MIDDLE_LEFT || this == MIDDLE_CENTER || this == MIDDLE_RIGHT; }

    public boolean isBottom() { return this == BOTTOM_LEFT || this == BOTTOM_CENTER || this == BOTTOM_RIGHT; }

    public boolean isLeft() { return this == TOP_LEFT || this == MIDDLE_LEFT || this == BOTTOM_LEFT; }

    public boolean isCenter() { return this == TOP_CENTER || this == MIDDLE_CENTER || this == BOTTOM_CENTER; }

    public boolean isRight() { return this == TOP_RIGHT || this == MIDDLE_RIGHT || this == BOTTOM_RIGHT; }

    /** 锚点的屏幕 X（未叠加组件偏移；条宽由渲染期再扣除） */
    public int baseX(int screenW, int marginX) {
        if (isLeft()) return marginX;
        if (isRight()) return screenW - marginX;
        return screenW / 2;
    }

    /** 锚点的屏幕 Y 基线（向下为正） */
    public int baseY(int screenH, int marginY) {
        if (isTop()) return marginY;
        if (isMiddle()) return screenH / 2;
        // BOTTOM_CENTER = 原版血条行基线（物品栏上方 39px）
        if (this == BOTTOM_CENTER) return screenH - 39;
        return screenH - marginY;
    }

    /** 自定义模式下条的水平对齐方向：-1 左对齐 / 0 居中 / 1 右对齐 */
    public int horizontalAlign() {
        if (isLeft()) return -1;
        if (isRight()) return 1;
        return 0;
    }
}
