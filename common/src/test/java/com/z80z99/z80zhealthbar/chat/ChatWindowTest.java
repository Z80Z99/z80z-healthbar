package com.z80z99.z80zhealthbar.chat;

import com.z80z99.z80zhealthbar.config.Z80ZHealthBarConfig;
import com.z80z99.z80zhealthbar.config.configs.ChatWindowConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 聊天窗口纯函数测试（1.0.150） */
class ChatWindowTest {

    @Test
    void resolveRectDefaultsAndClamps() {
        var c = new ChatWindowConfig();
        int[] r = ChatWindowManager.resolveRect(c, 640, 360);
        assertEquals(4, r[0], "default x = 4");
        assertEquals(360 - r[3] - 4, r[1], "default y = bottom-anchored");
        assertEquals(280, r[2]);
        assertEquals(160, r[3]);
        // 越界钳制（负值是"默认"哨兵——validator 把 <-1 收敛为 -1;正越界在此钳制）
        c.x = 65000; c.y = 9999; c.width = 99999; c.height = 5;
        r = ChatWindowManager.resolveRect(c, 640, 360);
        assertEquals(Math.max(0, 640 - r[2]), r[0]);
        assertTrue(r[1] <= 360 - r[3]);
        assertTrue(r[2] >= ChatWindowManager.MIN_W);
        assertEquals(ChatWindowManager.MIN_H, r[3]);
    }

    @Test
    void windowAlphaHoldThenFade() {
        var c = new ChatWindowConfig();
        c.idleFadeSecs = 2;
        assertEquals(1f, ChatWindowManager.windowAlpha(c, 1000, 2500), "within hold");
        float mid = ChatWindowManager.windowAlpha(c, 1000, 3300); // 300ms into fade
        assertTrue(mid > 0.12f && mid < 1f);
        assertEquals(0.12f, ChatWindowManager.windowAlpha(c, 1000, 10000), 1e-4, "floor");
        c.idleFadeSecs = 0;
        assertEquals(1f, ChatWindowManager.windowAlpha(c, 0, 999999), "常驻");
    }

    @Test
    void insertAndPopAnimations() {
        var c = new ChatWindowConfig();
        float[] a = ChatWindowManager.insertAnim(c, 0, 0);
        assertEquals(-6f, a[0], 0.01f);
        assertEquals(0f, a[1], 0.01f);
        a = ChatWindowManager.insertAnim(c, 0, 220);
        assertEquals(0f, a[0], 0.01f);
        assertEquals(1f, a[1], 0.01f);
        float[] p = ChatWindowManager.popAnim(c, 0, 0);
        assertEquals(0.95f, p[0], 0.01f);
        p = ChatWindowManager.popAnim(c, 0, 150);
        assertEquals(1f, p[0], 0.001f);
        c.animEnabled = false;
        assertEquals(1f, ChatWindowManager.popAnim(c, 0, 0)[0], "anim off = instant");
    }

    @Test
    void validatorClampsChatWindow() {
        var cfg = new Z80ZHealthBarConfig();
        var c = new ChatWindowConfig();
        c.width = 99999; c.height = 1; c.scale = -1; c.bgOpacity = 500;
        c.idleFadeSecs = -3; c.x = -5; c.inputY = -9;
        cfg.chatWindow = c;
        com.z80z99.z80zhealthbar.config.ConfigValidator.validate(cfg);
        assertEquals(800, c.width);
        assertEquals(60, c.height);
        assertEquals(1.0, c.scale);
        assertEquals(100, c.bgOpacity);
        assertEquals(0, c.idleFadeSecs);
        assertEquals(-1, c.x);
        assertEquals(-1, c.inputY);
        cfg.chatWindow = null;
        com.z80z99.z80zhealthbar.config.ConfigValidator.validate(cfg);
        assertNotNull(cfg.chatWindow, "null section → new");
    }

    @Test
    void linesPerPageMath() {
        assertEquals((160 - 6) / 9, ChatWindowManager.linesPerPage(new ChatWindowConfig(), 160));
        assertTrue(ChatWindowManager.linesPerPage(new ChatWindowConfig(), 60) >= 1);
    }
}
