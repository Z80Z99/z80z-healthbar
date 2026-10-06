package com.z80z99.z80zhealthbar.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudFxTest {

    @Test
    void pulseIsBoundedAndOscillates() {
        for (long t = 0; t < 5000; t += 37) {
            float p = HudFx.pulse(t);
            assertTrue(p >= 0f && p <= 1f, "pulse out of range: " + p);
        }
        assertEquals(0.5f, HudFx.pulse(0), 1e-6);
        assertTrue(HudFx.pulse((long) (Math.PI / 2 * 190)) > 0.9f); // 四分之一周期后接近峰值
    }

    @Test
    void sheenBandClipsInsideFill() {
        int innerW = 120;
        int fillW = 80;
        for (long t = 0; t < HudFx.SHEEN_PERIOD_MS; t += 100) {
            int[] b = HudFx.sheenBand(innerW, fillW, t, 0);
            if (b == null) continue;
            assertTrue(b[0] >= 0 && b[1] <= fillW && b[1] > b[0], "band out of fill: " + b[0] + "," + b[1]);
        }
    }

    @Test
    void sheenBandSweepsAcross() {
        int[] early = HudFx.sheenBand(120, 80, 50, 0);          // 周期初:未入或贴左
        int[] late = HudFx.sheenBand(120, 80, HudFx.SHEEN_PERIOD_MS - 50, 0); // 周期末:贴右或已出
        assertTrue(early == null || early[0] == 0, "early band should touch left edge");
        assertTrue(late == null || late[1] == 80, "late band should touch right edge");
    }

    @Test
    void sheenBandEmptyFillIsNull() {
        assertNull(HudFx.sheenBand(120, 0, 1000, 0));
    }

    @Test
    void shakeOffsetBoundedAndConverges() {
        for (long t = 0; t < 3000; t += 53) {
            int off = HudFx.shakeOffset(t, 0.7f);
            assertTrue(off >= 0 && off <= 2);
        }
        assertEquals(0, HudFx.shakeOffset(1000, 0f));
        assertEquals(0, HudFx.shakeOffset(1000, -1f));
    }

    @Test
    void fadeAlphaFadesInHoldsAndOut() {
        long change = 10_000;
        assertEquals(0f, HudFx.fadeAlpha(change, change, true, 200, 400, 200), 1e-6);
        assertEquals(0.5f, HudFx.fadeAlpha(change + 100, change, true, 200, 400, 200), 1e-6);
        assertEquals(1f, HudFx.fadeAlpha(change + 500, change, true, 200, 400, 200), 1e-6);
        // 消失:保持 400ms 全显 → 200ms 淡出
        assertEquals(1f, HudFx.fadeAlpha(change + 300, change, false, 200, 400, 200), 1e-6);
        assertEquals(0.5f, HudFx.fadeAlpha(change + 500, change, false, 200, 400, 200), 1e-6);
        assertEquals(0f, HudFx.fadeAlpha(change + 700, change, false, 200, 400, 200), 1e-6);
    }

    @Test
    void textTintClamped() {
        float[] t = HudFx.textTint(2f, 3f);
        assertEquals(1f, t[0]);
        assertEquals(1f, t[1]);
        float[] z = HudFx.textTint(0f, 0f);
        assertEquals(0f, z[0]);
        assertEquals(0f, z[1]);
    }

    @Test
    void easeOutBackEndpointsAndOvershoot() {
        assertEquals(0f, HudFx.easeOutBack(0f), 1e-4);
        assertEquals(1f, HudFx.easeOutBack(1f), 1e-4);
        float peak = 0f;
        for (float t = 0f; t <= 1f; t += 0.01f) peak = Math.max(peak, HudFx.easeOutBack(t));
        assertTrue(peak > 1.02f && peak < 1.15f, "expected overshoot, peak=" + peak);
    }

    @Test
    void popInStartsSmallSettlesFull() {
        float[] s0 = HudFx.popIn(0);
        assertTrue(s0[0] < 0.65f);
        assertEquals(-6f, s0[1], 1e-4);
        float[] s1 = HudFx.popIn(500);
        assertEquals(1f, s1[0], 1e-4);
        assertEquals(0f, s1[1], 1e-4);
    }
}
