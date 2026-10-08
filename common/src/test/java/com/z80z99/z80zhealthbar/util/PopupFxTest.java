package com.z80z99.z80zhealthbar.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PopupFxTest {

    @Test
    void originYCoversAllAnchors() {
        double head = 10.0, feet = 8.0;
        assertEquals(head, PopupFx.originY("HEAD", head, feet), 1e-9);
        assertEquals(head + 0.75, PopupFx.originY("BAR", head, feet), 1e-9);
        assertEquals(9.0, PopupFx.originY("CENTER", head, feet), 1e-9);
        assertEquals(feet + 0.25, PopupFx.originY("FEET", head, feet), 1e-9);
        assertEquals(head, PopupFx.originY(null, head, feet), 1e-9);
        assertEquals(head, PopupFx.originY("BOGUS", head, feet), 1e-9); // 非法回退 HEAD
    }

    @Test
    void risePxFollowsOverrideOrTheme() {
        assertEquals(16f, PopupFx.risePx("APEX", false, 0), 1e-6);
        assertEquals(34f, PopupFx.risePx("WARFRAME", false, 0), 1e-6);
        assertEquals(20f, PopupFx.risePx("CLASSIC", false, 0), 1e-6);
        assertEquals(40f, PopupFx.risePx("APEX", true, 40), 1e-6);
        assertEquals(0f, PopupFx.risePx("WARFRAME", true, 0), 1e-6);   // 覆盖 0=不上升
    }

    @Test
    void punchScaleBoundsAndZeroStrength() {
        assertEquals(1f, PopupFx.punchScale(0f, 0.5f), 1e-6);           // 无冲击恒 1
        assertEquals(1f, PopupFx.punchScale(0.45f, 0f), 1e-6);          // 相位 0
        assertEquals(1f, PopupFx.punchScale(0.45f, 1f), 1e-6);          // 相位 1
        assertEquals(1.45f, PopupFx.punchScale(0.45f, 0.5f), 1e-4);     // 峰值 = 1+强度
        for (float ph = 0f; ph <= 1f; ph += 0.05f) {
            float v = PopupFx.punchScale(0.85f, ph);
            assertTrue(v >= 1f && v <= 1.85f, "punch out of range: " + v);
        }
    }

    @Test
    void fadeAlphaStartsAtFadeStartAndReachesZero() {
        assertEquals(1f, PopupFx.fadeAlpha(0f, 0.7f), 1e-6);
        assertEquals(1f, PopupFx.fadeAlpha(0.69f, 0.7f), 1e-6);
        assertEquals(0.5f, PopupFx.fadeAlpha(0.85f, 0.7f), 1e-4);
        assertEquals(0f, PopupFx.fadeAlpha(1f, 0.7f), 1e-6);
        assertEquals(1f, PopupFx.fadeAlpha(0.5f, 0.95f), 1e-6);         // 起点更晚 → 更晚淡出
    }

    @Test
    void swayAndDriftBoundedAndDeterministic() {
        for (int seed = 0; seed < 64; seed++) {
            for (float t = 0f; t <= 1f; t += 0.1f) {
                float sx = PopupFx.swayX(t, seed);
                assertTrue(Math.abs(sx) <= 10f + 1e-4, "sway out of range");
                assertEquals(sx, PopupFx.swayX(t, seed), 1e-6);          // 确定性
            }
            float dx = PopupFx.driftX(seed, 6f);
            assertTrue(Math.abs(dx) <= 6f + 1e-4, "drift out of range");
        }
    }

    @Test
    void tiltDegFollowsOverrideOrTheme() {
        assertEquals(-6f, PopupFx.tiltDeg("WARFRAME", false, 0), 1e-6);
        assertEquals(0f, PopupFx.tiltDeg("APEX", false, 0), 1e-6);
        assertEquals(15f, PopupFx.tiltDeg("APEX", true, 15), 1e-6);
        assertEquals(-30f, PopupFx.tiltDeg("WARFRAME", true, -30), 1e-6);
        assertEquals(0f, PopupFx.tiltDeg("APEX", true, 0), 1e-6);        // 覆盖 0=无倾斜
    }
}
