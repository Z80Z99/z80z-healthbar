package com.z80z99.z80zhealthbar.datasource;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 后台探测的驱动与游戏内面板。
 *
 * <p>tick：每 tick 调 {@link #tick()}（2 Hz 采样、界面打开时暂停、玩家离开世界 2 秒后清空）。
 * <p>面板：仅当无界面、未隐藏 HUD、会话活跃时绘制在左上角，最多 6 行；**渲染路径零反射**，
 * 只读 {@link ProbeSession} 已算好的快照。
 */
public final class ProbeOverlay {

    /** 面板最多显示候选行数 */
    private static final int MAX_ROWS = 6;
    private static final int PAD = 4;
    private static final int LINE_H = 10;

    private ProbeOverlay() {}

    /** 每 tick 调用（Forge/Fabric 事件） */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        long now = System.currentTimeMillis();
        ProbeSession.tickPresence(mc.player != null, now);
        if (!ProbeSession.isActive()) return;
        // 界面打开时世界暂停（单人）/玩家看不到面板,采样无意义 → 暂停
        if (mc.screen != null) return;
        ProbeSession.sample(now);
    }

    /** HUD 渲染调用（在 HUD 之后） */
    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.screen != null || mc.options.hideGui) return;
        if (!ProbeSession.isActive() && !ProbeSession.hasSession()) return;
        var sorted = ProbeSession.sortedForDisplay();
        if (sorted.isEmpty()) return;

        long now = System.currentTimeMillis();
        int shown = Math.min(MAX_ROWS, sorted.size());
        int width = 150;
        int height = PAD * 2 + LINE_H * (shown + 2);
        int x = PAD, y = PAD;

        g.fill(x + 1, y + 1, x + width + 1, y + height + 1, 0x50000000);
        g.fill(x, y, x + width, y + height, 0xB00E1218);
        g.renderOutline(x, y, width, height, 0x807FD4FF);

        String title = "数据源探测 · " + ProbeSession.elapsedSeconds(now) + "s · "
                + ProbeSession.candidates().size() + " 个候选";
        g.drawString(mc.font, title, x + PAD, y + PAD, 0xFF9FD8FF, true);

        for (int i = 0; i < shown; i++) {
            var c = sorted.get(i);
            int ly = y + PAD + LINE_H * (i + 1);
            String value = formatValue(c);
            int color = c.aborted ? 0xFF8A8A8A : c.changed() ? 0xFF7CFF9B : 0xFFB8C4D0;
            String line = trim(mc, c.label, width - PAD * 2 - 6);
            g.drawString(mc.font, line, x + PAD, ly, color, true);
            int vw = mc.font.width(value);
            g.drawString(mc.font, value, x + width - PAD - vw, ly,
                    c.changed() ? 0xFF9BFFB5 : 0xFF8FA8BF, true);
        }
        if (sorted.size() > shown) {
            g.drawString(mc.font, "…还有 " + (sorted.size() - shown) + " 个",
                    x + PAD, y + PAD + LINE_H * (shown + 1), 0xFF7F8C99, true);
        } else {
            g.drawString(mc.font, "喝水/进食后按 O 回来看结果",
                    x + PAD, y + PAD + LINE_H * (shown + 1), 0xFF7F8C99, true);
        }
    }

    private static String formatValue(ProbeSession.Candidate c) {
        if (c.aborted) return "过慢已停";
        if (c.current == null) return "读取失败";
        String cur = fmt(c.current);
        if (c.changes > 0 && c.baseline != null) {
            return cur + " ×" + c.changes;
        }
        return cur;
    }

    private static String fmt(Float v) {
        if (v == null) return "—";
        if (Math.abs(v - Math.round(v)) < 0.05f) return String.valueOf(Math.round(v));
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static String trim(Minecraft mc, String s, int maxW) {
        if (s == null) return "";
        if (mc.font.width(s) <= maxW) return s;
        int i = s.length();
        while (i > 0 && mc.font.width(s.substring(0, i) + "…") > maxW) i--;
        return s.substring(0, i) + "…";
    }
}
