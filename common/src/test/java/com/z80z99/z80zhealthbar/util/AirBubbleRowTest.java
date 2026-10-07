package com.z80z99.z80zhealthbar.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 氧气气泡行计数测试（原版 ForgeGui.renderAir 语义;用户实测"泡沫爆裂后不消失/耗尽仍占行"回归） */
class AirBubbleRowTest {

    private static int drawn(int air, int max) {
        int[] c = AirBubbleRow.counts(air, max, 10);
        return c[0] + c[1];
    }

    @Test
    void emptyAtZeroAir() {
        // 氧气耗尽：不绘制任何气泡（整行消失）——此前恒画 10 个"将破"槽底
        assertArrayEquals(new int[]{0, 0}, AirBubbleRow.counts(0, 300, 10));
        assertEquals(0, drawn(0, 300));
        assertEquals(0, drawn(-5, 300));
    }

    @Test
    void fullRowAtFullAir() {
        assertArrayEquals(new int[]{10, 0}, AirBubbleRow.counts(300, 300, 10));
        // 299 仍在"预留 2 点"窗口内：10 个满泡,没有破裂泡（原版行为）
        assertArrayEquals(new int[]{10, 0}, AirBubbleRow.counts(299, 300, 10));
        assertArrayEquals(new int[]{10, 0}, AirBubbleRow.counts(500, 300, 10)); // 超出上限按满格
    }

    @Test
    void poppedBubblesDisappear() {
        // 气泡破掉即消失：绘制数随氧气单调不增,且不超过 ceil(air*10/max)
        int prev = 11;
        for (int air = 300; air >= 0; air--) {
            int d = drawn(air, 300);
            assertTrue(d <= prev, "air=" + air + " 绘制数反弹");
            assertTrue(d <= (int) Math.ceil(air * 10.0 / 300), "air=" + air + " 超画了未到量程的气泡");
            prev = d;
        }
        // 每 30 点一格：掉一格后正好少一个满泡
        assertArrayEquals(new int[]{9, 0}, AirBubbleRow.counts(270, 300, 10));
        assertArrayEquals(new int[]{5, 0}, AirBubbleRow.counts(150, 300, 10));
    }

    @Test
    void atMostOnePoppingBubble() {
        // 边界只有一个"正在破裂"的气泡;低氧气时它仍在（提示正在消耗）
        assertArrayEquals(new int[]{0, 1}, AirBubbleRow.counts(2, 300, 10));
        assertArrayEquals(new int[]{0, 1}, AirBubbleRow.counts(1, 300, 10));
        assertArrayEquals(new int[]{1, 0}, AirBubbleRow.counts(15, 300, 10));
        for (int air = 0; air <= 300; air++) {
            assertTrue(AirBubbleRow.counts(air, 300, 10)[1] <= 1, "air=" + air + " 出现多个破裂泡");
        }
    }

    @Test
    void scalesWithModdedMaxAir() {
        // 非 300 上限（模组/属性加成）同样按槽位归一：满 = 10 格,0 = 空行
        assertArrayEquals(new int[]{10, 0}, AirBubbleRow.counts(600, 600, 10));
        assertEquals(0, drawn(0, 600));
        assertTrue(drawn(300, 600) <= 5);
        // max ≤ 0 视为 1,不除零
        assertDoesNotThrow(() -> AirBubbleRow.counts(5, 0, 10));
        assertEquals(0, drawn(0, 0));
    }

    @Test
    void rowVisibleFollowsVanillaRule() {
        // 原版对玩家的规则：眼睛在水里 或 空气未满（且未耗尽）
        assertTrue(AirBubbleRow.rowVisible(true, 300, 300));   // 亡灵/水生:水下满氧也要显示（用户实测场景）
        assertTrue(AirBubbleRow.rowVisible(false, 100, 300));  // 出水回氧中
        assertFalse(AirBubbleRow.rowVisible(false, 300, 300)); // 岸上满氧
        assertFalse(AirBubbleRow.rowVisible(true, 0, 300));    // 耗尽（原版 0 氧气不画气泡）
        assertFalse(AirBubbleRow.rowVisible(false, 0, 300));
    }
}
