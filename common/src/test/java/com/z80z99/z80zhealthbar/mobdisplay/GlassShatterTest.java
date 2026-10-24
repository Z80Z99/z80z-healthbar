package com.z80z99.z80zhealthbar.mobdisplay;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 玻璃切分纯函数测试（1.0.149 碎裂重做）：切分守面积、有界、可复现。
 * ShatterFx 的其余方法依赖 MC 渲染,不在纯 JVM 单测范围。
 */
class GlassShatterTest {

    private static final float EPS = 0.02f;

    @Test
    void splitCoversOriginalArea() {
        // 面积守恒：碎片面积和 = 原矩形面积（斜线切分不重叠不丢失）
        for (long seed = 1; seed <= 20; seed++) {
            var pieces = ShatterFx.glassSplit(60f, 8f, new Random(seed), 14);
            float sum = 0;
            for (float[] p : pieces) sum += Math.abs(ShatterFx.shoelace(p));
            assertEquals(60f * 8f, sum, 60f * 8f * EPS, "seed=" + seed);
        }
    }

    @Test
    void splitProducesIrregularQuadsInBounds() {
        var pieces = ShatterFx.glassSplit(60f, 8f, new Random(42), 14);
        assertEquals(14, pieces.size(), "target reached for a healthy bar size");
        float hw = 30f + EPS, hh = 4f + EPS;
        boolean anyIrregular = false;
        for (float[] p : pieces) {
            assertEquals(8, p.length);
            for (int i = 0; i < 4; i++) {
                assertTrue(p[i * 2] >= -hw && p[i * 2] <= hw, "x in bounds");
                assertTrue(p[i * 2 + 1] >= -hh && p[i * 2 + 1] <= hh, "y in bounds");
            }
            // 不规则性:至少有碎片的角点不是轴对齐矩形（切线斜率非 0）
            boolean axisAligned = (p[0] == p[6] || p[0] == p[2]) && (p[1] == p[3] || p[1] == p[7]);
            if (!axisAligned) anyIrregular = true;
        }
        assertTrue(anyIrregular, "not a plain uniform grid");
    }

    @Test
    void splitDeterministicBySeed() {
        var a = ShatterFx.glassSplit(60f, 8f, new Random(7), 12);
        var b = ShatterFx.glassSplit(60f, 8f, new Random(7), 12);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertArrayEquals(a.get(i), b.get(i), 1e-6f);
        }
    }

    @Test
    void splitStopsWhenTooSmall() {
        // 极小条（6x2）切不了几刀:碎片数 < target 但 >= 1,不抛异常,面积守恒
        var pieces = ShatterFx.glassSplit(6f, 2f, new Random(1), 14);
        assertFalse(pieces.isEmpty());
        assertTrue(pieces.size() < 14, "tiny bar yields fewer shards: " + pieces.size());
        float sum = 0;
        for (float[] p : pieces) sum += Math.abs(ShatterFx.shoelace(p));
        assertEquals(12f, sum, 12f * EPS);
    }

    @Test
    void shoelaceSignReflectsWinding() {
        float[] cw = {0, 0, 1, 0, 1, 1, 0, 1};
        float[] ccw = {0, 0, 0, 1, 1, 1, 1, 0};
        assertEquals(1f, ShatterFx.shoelace(cw), 1e-4);
        assertEquals(-1f, ShatterFx.shoelace(ccw), 1e-4);
    }
}
