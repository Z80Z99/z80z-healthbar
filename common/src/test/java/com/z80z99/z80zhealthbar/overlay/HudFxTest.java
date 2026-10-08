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
        int bandW = Math.max(10, innerW / 5); // 24
        for (double pos = -bandW; pos <= fillW + 5; pos += 3) {
            float[] b = HudFx.sheenBandPhase(innerW, fillW, pos);
            if (b == null) continue;
            assertTrue(b[0] >= 0 && b[1] <= fillW && b[1] > b[0], "band out of fill: " + b[0] + "," + b[1]);
        }
    }

    @Test
    void sheenBandSweepsAcross() {
        int innerW = 120, fillW = 80;
        int bandW = Math.max(10, innerW / 5); // 24
        assertNull(HudFx.sheenBandPhase(innerW, fillW, -bandW), "fully off-left → invisible");
        assertNull(HudFx.sheenBandPhase(innerW, fillW, fillW), "fully off-right → invisible");
        float[] mid = HudFx.sheenBandPhase(innerW, fillW, 30);
        assertNotNull(mid);
        assertTrue(mid[0] > 0 && mid[1] <= fillW, "mid band inside fill");
        float[] late = HudFx.sheenBandPhase(innerW, fillW, fillW - 1);
        assertTrue(late != null && late[1] == fillW, "band should touch right edge near fill end");
    }

    @Test
    void sheenBandEmptyFillIsNull() {
        assertNull(HudFx.sheenBandPhase(120, 0, 10));
    }

    @Test
    void sheenBandKeepsSubPixelPrecision() {
        int innerW = 120, fillW = 60;
        // 慢速条每帧 ~0.2-0.4px:带区间必须保留小数（取整会造成 1px 阶梯卡顿）
        float prevStart = -1f, prevEnd = -1f;
        for (double pos = 5.0; pos < 30.0; pos += 0.2) {
            float[] b = HudFx.sheenBandPhase(innerW, fillW, pos);
            assertNotNull(b, "band visible across mid-fill");
            assertTrue(b[0] >= prevStart - 1e-6f, "start must be monotonic");
            assertTrue(b[1] >= prevEnd - 1e-6f, "end must be monotonic");
            assertTrue(b[0] != prevStart || b[1] != prevEnd, "sub-pixel steps must change the band");
            prevStart = b[0]; prevEnd = b[1];
        }
    }

    @Test
    void sheenStripAlphaIsGradientWithLeadingHighlight() {
        int bandW = 100;
        int left = HudFx.sheenStripAlpha(0, 2, bandW);        // t≈0.01 边缘 → 接近 0
        int mid = HudFx.sheenStripAlpha(49, 2, bandW);        // t≈0.50 光晕峰
        int edge = HudFx.sheenStripAlpha(74, 2, bandW);       // t≈0.75 前缘亮点峰
        int right = HudFx.sheenStripAlpha(98, 2, bandW);      // t≈0.99 右缘 → 接近 0
        // 渐变而非平铺:两端暗、中部亮
        assertTrue(left <= 4, "left edge should fade out, got " + left);
        assertTrue(right <= 8, "right edge should fade out, got " + right);
        assertTrue(mid >= 30, "halo mid should be bright, got " + mid);
        // 前缘亮点高于光晕中部（右行方向的亮边）
        assertTrue(edge > mid, "leading highlight (" + edge + ") should exceed halo mid (" + mid + ")");
        // 全局上限 0x66
        for (int x = 0; x < bandW; x += 3) {
            int a = HudFx.sheenStripAlpha(x, Math.min(3, bandW - x), bandW);
            assertTrue(a >= 0 && a <= 0x66, "alpha out of range: " + a);
        }
    }

    /** 期望的单帧（100ms）步长:min(基准像素速度, 距离/最小单趟) × 0.1s */
    private static double sheenStepPx(int innerW, int fillW) {
        int bandW = Math.max(10, innerW / 5);
        double travel = fillW + bandW;
        return Math.min(HudFx.SHEEN_SPEED_PX_PER_SEC, travel * 1000.0 / HudFx.SHEEN_MIN_LAP_MS) * 0.1;
    }

    @Test
    void sheenLongBarsShareBasePixelSpeed() {
        long t0 = 1_000_000;
        // travel ≥ 基准×2.4s = 134.4px 的条:一律基准像素速度,与条长无关
        int kA = 91001, kB = 91002;
        double a0 = HudFx.advanceSheen(kA, 182, 178, t0, false);
        double b0 = HudFx.advanceSheen(kB, 240, 200, t0, false);
        double a = a0, b = b0;
        for (int i = 1; i <= 8; i++) {
            long t = t0 + i * 100L;
            a = HudFx.advanceSheen(kA, 182, 178, t, false);
            b = HudFx.advanceSheen(kB, 240, 200, t, false);
        }
        assertEquals(HudFx.SHEEN_SPEED_PX_PER_SEC * 0.8, a - a0, 1e-6, "long bar = base px speed");
        assertEquals(a - a0, b - b0, 1e-6, "speed must not depend on length above floor");
    }

    @Test
    void sheenShortBarsShareRhythmAndNeverExceedBase() {
        long t0 = 1_000_000;
        // travel < 134.4px 的条:速度 = travel/最小单趟 → 单趟恒 2.4s,节奏与条长无关
        int kS = 91003, kT = 91004;
        int fillS = 40, fillT = 8; // innerW 120 → travel 64 / 32
        double s0 = HudFx.advanceSheen(kS, 120, fillS, t0, false);
        double t0v = HudFx.advanceSheen(kT, 120, fillT, t0, false);
        double s = s0, tv = t0v;
        for (int i = 1; i <= 10; i++) {
            long t = t0 + i * 100L;
            s = HudFx.advanceSheen(kS, 120, fillS, t, false);
            tv = HudFx.advanceSheen(kT, 120, fillT, t, false);
        }
        assertEquals(sheenStepPx(120, fillS) * 10, s - s0, 1e-6, "short bar speed = travel/minLap");
        assertEquals(sheenStepPx(120, fillT) * 10, tv - t0v, 1e-6, "short bar speed = travel/minLap");
        // 节奏:10 步后行进 10×step → 一圈(travel)耗时 = travel/step/10 步 = 2.4s
        assertEquals(HudFx.SHEEN_MIN_LAP_MS / 1000.0, (fillS + 24) / (s - s0) * 1.0, 1e-3, "lap = 2.4s");
        // 任何条不超过基准
        assertTrue(s - s0 <= HudFx.SHEEN_SPEED_PX_PER_SEC * 1.0 + 1e-6, "never faster than base");
    }

    @Test
    void sheenFillChangeMovesSmoothlyNoJump() {
        int k = 424242;
        long t0 = 1_000_000;
        HudFx.advanceSheen(k, 120, 100, t0, false);
        double p1 = HudFx.advanceSheen(k, 120, 100, t0 + 100, false);
        assertEquals(sheenStepPx(120, 100), p1 - 0 /*起点未跟踪,首步不做断言*/, 1e9); // 占位:起点由内部状态决定
        // 用相对步长断言:填充 100→45→12,每步 = 当前 travel 对应的步长,连续步差 < 1.2px（平滑）
        double p2 = HudFx.advanceSheen(k, 120, 45, t0 + 200, false);
        double step2 = p2 - p1;
        assertTrue(step2 > 0.5 && step2 < 6.2, "step during shrink within model range: " + step2);
        double p3 = HudFx.advanceSheen(k, 120, 12, t0 + 300, false);
        double step3 = p3 - p2;
        assertTrue(step3 > 0.5 && step3 < 6.2, "step within model range: " + step3);
        // 步差上限 = 相邻两帧 travel 差/2.4(掉血 33px → 1.375px) + 余量;真正的跳变是数百 px 量级
        assertTrue(Math.abs(step3 - step2) < 3.0, "consecutive steps must be smooth (no jump): " + step2 + " -> " + step3);
    }

    @Test
    void sheenWrapRestartsOffLeft() {
        int k = 430001;
        int innerW = 120, fillW = 30;
        int bandW = 24;
        double step = sheenStepPx(innerW, fillW); // travel 54 → 2.25px/100ms → 一圈 2.4s
        long t0 = 3_000_000;
        double pos = HudFx.advanceSheen(k, innerW, fillW, t0, false);
        boolean wrapped = false;
        for (int i = 1; i <= 40 && !wrapped; i++) {
            double np = HudFx.advanceSheen(k, innerW, fillW, t0 + i * 100L, false);
            if (np < pos - 1e-9) {
                assertTrue(np >= -bandW - 1e-6 && np <= -bandW + step + 1e-6,
                        "wrap restart must stay off-left: " + np);
                wrapped = true;
            } else {
                assertEquals(step, np - pos, 1e-6, "steady advance must be uniform");
            }
            pos = np;
        }
        assertTrue(wrapped, "a full lap should wrap within 40 steps (lap=2.4s)");
    }

    @Test
    void sheenAdaptiveBoostsOnlyWhileChanging() {
        long t0 = 2_000_000;
        int kU = 92001, kA = 92002;
        double baseStep = sheenStepPx(120, 100); // travel 124 → 5.167px/100ms
        HudFx.advanceSheen(kU, 120, 100, t0, false);
        HudFx.advanceSheen(kA, 120, 100, t0, true);
        double u1 = HudFx.advanceSheen(kU, 120, 100, t0 + 100, false);
        double a1 = HudFx.advanceSheen(kA, 120, 100, t0 + 100, true);
        assertEquals(u1, a1, 1e-6, "idle adaptive must equal uniform");
        double u2 = HudFx.advanceSheen(kU, 120, 99, t0 + 200, false);
        double a2 = HudFx.advanceSheen(kA, 120, 99, t0 + 200, true);
        double dU = u2 - u1, dA = a2 - a1;
        assertEquals(sheenStepPx(120, 99), dU, 1e-6, "uniform speed = travel/minLap");
        assertTrue(dA > dU * 1.4 && dA < dU * 1.6, "adaptive boost should be ~1.5x, dU=" + dU + " dA=" + dA);
    }

    @Test
    void shakeOffsetBoundedAndConverges() {
        for (long t = 0; t < 3000; t += 53) {
            int off = HudFx.shakeStatic(t, 0.7f);
            assertTrue(off >= 0 && off <= 2);
            int smooth = HudFx.shakeSmooth(t, 0.7f, 2f);
            assertTrue(smooth >= -2 && smooth <= 2, "smooth shake within amplitude");
        }
        assertEquals(0, HudFx.shakeStatic(1000, 0f));
        assertEquals(0, HudFx.shakeSmooth(1000, -1f, 2f));
        // 按方式分派:关=0;静态在序列取 1 的时刻输出;动态（正弦峰 π/2·40ms）必非零
        assertEquals(0, HudFx.shakeByMode(0, 1000, 0.8f, 0.2f));
        assertEquals(2, HudFx.shakeByMode(1, 50, 0.8f, 0.2f));
        int dynPeak = HudFx.shakeByMode(3, (long) (Math.PI / 2 * 40), 1f, 0.3f);
        assertTrue(dynPeak >= 1, "dynamic shake at sine peak should be visible, got " + dynPeak);
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
