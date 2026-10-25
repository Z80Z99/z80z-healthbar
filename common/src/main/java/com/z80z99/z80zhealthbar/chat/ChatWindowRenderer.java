package com.z80z99.z80zhealthbar.chat;

import com.z80z99.z80zhealthbar.chat.ChatWindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 聊天窗口渲染（每帧由平台 HUD 回调调用;无 screen 与 ChatWindowScreen 打开时都绘制,
 * 打开时附加标题栏/缩放角/悬停高亮/行点击命中）。
 *
 * <p>换行缓存按消息实例（WeakHashMap）;wordWrap 关 = 只取首行硬截断。
 * 滚动状态为静态（行单位,底部对齐,新消息推入时上滚一格）。
 */
public final class ChatWindowRenderer {

    private ChatWindowRenderer() {}

    /** 换行缓存：GuiMessage → (宽度, 行列表)——消息实例入列表即换新引用,天然失效 */
    private static final Map<GuiMessage, Wrap> WRAP = new WeakHashMap<>();

    private record Wrap(int width, List<FormattedCharSequence> lines) {}

    /** 滚动（行单位;0 = 底部对齐显示最新） */
    private static float scroll, scrollTarget;
    /** 本帧行布局（命中测试用;仅 screen 打开时填充） */
    private static final List<HitLine> HIT = new ArrayList<>();
    private record HitLine(float x, float y, FormattedCharSequence fcs, int msgTick) {}
    /** 聊天界面打开时刻（Gui tick 基准;ChatWindowScreen.init 设置） */
    public static int openedAt;
    /** 标题栏文案缓存（translatable 每帧分配 → 静态化,语言切换后不刷新可接受） */
    private static String dragHintCache = "";
    /** 本帧内容宽（wrap 缓存键,render 开头更新一次——wrap 内不再各自 resolveRect） */
    private static int cachedInnerW = 274;

    /** 窗口矩形（含 scale 变换前的屏幕坐标） */
    public static int[] rect() {
        var mc = Minecraft.getInstance();
        return ChatWindowManager.resolveRect(ChatWindowManager.cfg(),
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    /** 屏幕 → 窗口局部坐标（除以 scale） */
    public static float[] toLocal(double mx, double my) {
        int[] r = rect();
        float s = (float) ChatWindowManager.cfg().scale;
        return new float[]{(float) ((mx - r[0]) / s), (float) ((my - r[1]) / s)};
    }

    /** 鼠标是否在窗口内（屏幕坐标,含 scale） */
    public static boolean inside(double mx, double my) {
        int[] r = rect();
        float s = (float) ChatWindowManager.cfg().scale;
        return mx >= r[0] && my >= r[1] && mx <= r[0] + r[2] * s && my <= r[1] + r[3] * s;
    }

    /** 滚动（行单位;0=底部最新）。滚轮向上 delta>0 → 向更早翻 → scroll 增大。
     *  上限 = 总行数-每页行数（超过后底部留白;旧实现 total-1 会多滚一屏空白）。 */
    public static void onScroll(double delta) {
        int max = maxScrollLines();
        scrollTarget = Math.max(0, Math.min(max, scrollTarget + (float) delta));
    }

    private static int maxScrollLines() {
        var all = ChatWindowManager.allMessages();
        int total = 0;
        for (var m : all) total += wrap(m).size();
        int page = ChatWindowManager.linesPerPage(ChatWindowManager.cfg(),
                rect()[3] - (net.minecraft.client.Minecraft.getInstance().screen instanceof com.z80z99.z80zhealthbar.gui.ChatWindowScreen ? 11 : 0));
        return Math.max(0, total - page);
    }

    /** 新消息到达时目标滚动保持底部(0) */
    public static void snapToBottom() {
        scrollTarget = 0;
        scroll = 0;
    }

    private static List<FormattedCharSequence> wrap(GuiMessage msg) {
        var mc = Minecraft.getInstance();
        Font font = mc.font;
        int innerW = cachedInnerW;
        Wrap w = WRAP.get(msg);
        if (w == null || w.width() != innerW) {
            w = new Wrap(innerW, font.split(msg.content(), innerW));
            WRAP.put(msg, w);
        }
        var c = ChatWindowManager.cfg();
        if (c.wordWrap) return w.lines();
        return w.lines().size() > 1 ? List.of(w.lines().get(0)) : w.lines();
    }

    /** 主渲染;chatOpen = 当前是 ChatWindowScreen（附加交互装饰） */
    public static void render(GuiGraphics g, int mouseX, int mouseY, boolean chatOpen) {
        var mc = Minecraft.getInstance();
        if (!ChatWindowManager.takeover()) return;
        var c = ChatWindowManager.cfg();
        var all = ChatWindowManager.allMessages(); // 新→旧;渲染按倒序索引直接自底向上堆,不再每帧拷贝反转
        if (all.isEmpty() && !chatOpen) return;
        int now = mc.gui.getGuiTicks();
        cachedInnerW = Math.max(20, rect()[2] - ChatWindowManager.PAD * 2);

        int[] r = rect();
        float s = (float) c.scale;

        // 整窗 alpha（静默淡出）× 弹出动画
        int newest = all.isEmpty() ? 0 : all.get(0).addedTime(); // allMessages[0] = 最新
        float winA = ChatWindowManager.windowAlpha(c, newest, now);
        float[] pop = ChatWindowManager.popAnim(c, openedAt, now);
        if (chatOpen) winA *= pop[1];

        var pose = g.pose();
        pose.pushPose();
        pose.translate(r[0], r[1], 0);
        pose.scale(s, s, 1f);
        if (chatOpen && pop[0] < 1f) {
            pose.translate(r[2] / 2f * (1f - pop[0]), r[3] / 2f * (1f - pop[0]), 0);
            pose.scale(pop[0], pop[0], 1f);
            pose.translate(-r[2] / 2f * (1f - pop[0]) / pop[0], -r[3] / 2f * (1f - pop[0]) / pop[0], 0);
        }

        // 底板（双层:投影 + 半透明主体）
        int bg = ((int) (c.bgOpacity * 2.55f * winA)) << 24;
        g.fill(2, 2, r[2] + 2, r[3] + 2, (int) (0x28 * winA) << 24);
        g.fill(0, 0, r[2], r[3], bg);
        g.renderOutline(0, 0, r[2], r[3], ((int) (0x50 * winA) << 24) | 0x7FD4FF);

        // 打开时:标题栏
        int headerH = 0;
        if (chatOpen) {
            headerH = 11;
            g.fill(0, 0, r[2], headerH, ((int) (0x90 * winA) << 24) | 0x1B2836);
            if (dragHintCache.isEmpty()) dragHintCache = "≡ " + net.minecraft.network.chat.Component
                    .translatable("z80zhealthbar.chat.drag_hint").getString();
            String drag = dragHintCache;
            mc.font.drawInBatch(drag, 4, 2, 0xCFE6FF, true,
                    pose.last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
            // 缩放角标（右下 7x7 三角示意）
            for (int i = 0; i < 4; i++) {
                g.fill(r[2] - 2 - (i + 1) * 3, r[3] - 2 - i * 3,
                        r[2] - 2 - i * 3, r[3] - 1 - i * 3, 0x907FD4FF);
            }
        }

        // 滚动平滑
        if (c.smoothScroll) {
            scroll += (scrollTarget - scroll) * 0.35f;
            if (Math.abs(scrollTarget - scroll) < 0.05f) scroll = scrollTarget;
        } else {
            scroll = scrollTarget;
        }

        // 行布局:自底向上堆
        int pageLines = ChatWindowManager.linesPerPage(c, r[3] - headerH);
        List<Object[]> layout = new ArrayList<>(); // {fcs, y(局部,不含header), added}
        int bottomY = r[3] - ChatWindowManager.PAD;
        int yCursor = bottomY;
        float skip = scroll; // 顶部跳过的行数(滚动向上=显示更早)
        // 从最新往旧堆;skip>0 表示顶部(最旧可见端)再跳过 skip 行 → 等效整体下移
        yCursor += (int) (skip * ChatWindowManager.LINE_H);
        int msgIdx = 0; // all 为新→旧:索引 0 = 最新,自底向上堆
        HIT.clear();
        var lm = chatOpen ? ChatWindowRenderer.toLocal(mouseX, mouseY) : null;
        while (msgIdx < all.size() && yCursor > headerH) {
            GuiMessage m = all.get(msgIdx++);
            var lines = wrap(m);
            for (int li = lines.size() - 1; li >= 0; li--) {
                if (yCursor - ChatWindowManager.LINE_H < headerH) break;
                var fcs = lines.get(li);
                float[] ins = ChatWindowManager.insertAnim(c, m.addedTime(), now);
                float rowA = winA * ins[1];
                int y = yCursor - ChatWindowManager.LINE_H + (int) ins[0];
                // 行背景
                g.fill(ChatWindowManager.PAD - 1, y,
                        r[2] - ChatWindowManager.PAD + 1, y + ChatWindowManager.LINE_H,
                        ((int) (0x28 * rowA)) << 24);
                // 悬停高亮（仅打开时）
                if (lm != null && lm[0] >= ChatWindowManager.PAD - 1 && lm[0] < r[2] - ChatWindowManager.PAD + 1
                        && lm[1] >= y && lm[1] < y + ChatWindowManager.LINE_H) {
                    g.fill(ChatWindowManager.PAD - 1, y, r[2] - ChatWindowManager.PAD + 1,
                            y + ChatWindowManager.LINE_H, ((int) (0x30 * rowA) << 24) | 0x7FD4FF);
                }
                mc.font.drawInBatch(fcs, ChatWindowManager.PAD, y,
                        applyAlpha(0xFFFFFF, (int) (rowA * 255)), true,
                        pose.last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
                HIT.add(new HitLine(ChatWindowManager.PAD, y, fcs, m.addedTime()));
                yCursor -= ChatWindowManager.LINE_H;
            }
            yCursor -= 1; // 消息间 1px 缝隙
        }

        // 滚动条
        int totalLines = 0;
        for (var m : all) totalLines += wrap(m).size();
        if (totalLines > pageLines) {
            float frac = pageLines / (float) totalLines;
            int trackTop = headerH + 1, trackBottom = r[3] - 2;
            int thumbH = Math.max(10, (int) ((trackBottom - trackTop) * frac));
            float maxScroll = Math.max(1f, totalLines - pageLines);
            int thumbY = trackTop + (int) ((trackBottom - trackTop - thumbH)
                    * Math.min(1f, scroll / maxScroll));
            g.fill(r[2] - 3, thumbY, r[2] - 1, thumbY + thumbH, ((int) (0xA0 * winA) << 24) | 0x7FD4FF);
        }

        pose.popPose();
    }

    private static int applyAlpha(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** 打开聊天时点击命中:窗口局部坐标 → Style（无则 null） */
    public static Style styleAt(double mx, double my) {
        var lm = toLocal(mx, my);
        var mc = Minecraft.getInstance();
        int w = rect()[2];
        for (HitLine hl : HIT) {
            if (lm[1] >= hl.y() && lm[1] < hl.y() + ChatWindowManager.LINE_H) {
                final float[] x = {hl.x()};
                final Style[] hit = {null};
                hl.fcs().accept((index, style, ch) -> {
                    if (hit[0] != null) return true;
                    int cw = mc.font.width(String.valueOf(ch));
                    if (lm[0] >= x[0] && lm[0] < x[0] + cw && style.getClickEvent() != null) {
                        hit[0] = style;
                        return false;
                    }
                    x[0] += cw;
                    return true;
                });
                return hit[0];
            }
        }
        return null;
    }

    /** 标题栏命中（局部）;仅 chatOpen */
    public static boolean headerHit(double mx, double my) {
        var lm = toLocal(mx, my);
        int[] r = rect();
        return lm[0] >= 0 && lm[0] <= r[2] && lm[1] >= 0 && lm[1] < 11;
    }

    /** 右下缩放角命中（局部,14×14）;仅 chatOpen */
    public static boolean cornerHit(double mx, double my) {
        var lm = toLocal(mx, my);
        int[] r = rect();
        return lm[0] >= r[2] - 14 && lm[1] >= r[3] - 14;
    }
}
