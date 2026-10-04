package com.z80z99.z80zhealthbar.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 颜色工具测试（渲染核心原语之一） */
class ColorHelperTest {

    @Test
    void parsesArgbAndRgb() {
        assertEquals(0xFFFF0000, ColorHelper.parseColor("#FFFF0000"));
        assertEquals(0xFFFF0000, ColorHelper.parseColor("#FF0000"));
        assertEquals(0xFF000000, ColorHelper.parseColor(""));
        assertEquals(0xFF000000, ColorHelper.parseColor(null));
    }

    @Test
    void safeParseRejectsGarbage() {
        assertNull(ColorHelper.parseColorSafe("xyz"));
        assertNull(ColorHelper.parseColorSafe("#12345"));
        assertNull(ColorHelper.parseColorSafe(null));
        assertEquals((Integer) 0xFF00FF00, ColorHelper.parseColorSafe("#00FF00"));
        assertEquals((Integer) 0x80FFFFFF, ColorHelper.parseColorSafe("#80FFFFFF"));
    }

    @Test
    void lerpInterpolatesMidpoint() {
        int a = 0xFF000000;
        int b = 0xFF808080;
        int mid = ColorHelper.lerp(a, b, 0.5f);
        assertEquals(0xFF404040, mid);
    }

    @Test
    void modifyAlphaScalesProportionally() {
        assertEquals(0x80FFFFFF, ColorHelper.modifyAlpha(0xFFFFFFFF, 128));
        // alpha=0 = 完全透明(旧语义"返回原色"导致淡出末帧跳回不透明,已修正)
        assertEquals(0x00FFFFFF, ColorHelper.modifyAlpha(0xFFFFFFFF, 0));
        assertEquals(0x00FFFFFF, ColorHelper.modifyAlpha(0xFFFFFFFF, -5));
    }
}
