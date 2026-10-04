package com.z80z99.z80zhealthbar.overlay;

public enum OverlayStyle {
    NONE(0),
    ABOVE_HOT_BAR_LONG(1),
    ABOVE_HOT_BAR_SHORT(2),
    TOP_BOTH_SIDES(3),
    BOTTOM_BOTH_SIDES(4),
    TOP_LEFT(5),
    TOP_RIGHT(6),
    BOTTOM_LEFT(7),
    BOTTOM_RIGHT(8);

    public static final int NUM_STYLES = 9;

    public final int id;

    OverlayStyle(int id) {
        this.id = id;
    }

    public static OverlayStyle fromId(int id) {
        return switch (id) {
            case 1 -> ABOVE_HOT_BAR_LONG;
            case 2 -> ABOVE_HOT_BAR_SHORT;
            case 3 -> TOP_BOTH_SIDES;
            case 4 -> BOTTOM_BOTH_SIDES;
            case 5 -> TOP_LEFT;
            case 6 -> TOP_RIGHT;
            case 7 -> BOTTOM_LEFT;
            case 8 -> BOTTOM_RIGHT;
            default -> NONE;
        };
    }
}
