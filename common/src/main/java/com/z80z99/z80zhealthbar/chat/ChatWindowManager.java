package com.z80z99.z80zhealthbar.chat;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.ChatWindowConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天窗口核心（1.0.150）：接管裁决 + 原版私有字段的反射访问 + 窗口几何纯函数。
 *
 * <p>数据不动：消息仍由原版 ChatComponent 维护（ClientPacketListener→addMessage 管线不变）,
 * 这里只读 `allMessages`（反射缓存,读引用每帧开销可忽略）。
 * 关闭 enabled 即完全还原原版（渲染/输入/点击全部回退）。
 */
public final class ChatWindowManager {

    private ChatWindowManager() {}

    public static ChatWindowConfig cfg() {
        return ConfigManager.getConfig().chatWindow;
    }

    /** 是否接管原版聊天（渲染取消 + 聊天界面替换）。服务端聊天隐藏时仍渲染系统?——HIDDEN 全停 */
    public static boolean takeover() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        if (!cfg().enabled) return false;
        try {
            return mc.options.chatVisibility().get() != net.minecraft.world.entity.player.ChatVisiblity.HIDDEN;
        } catch (Throwable t) {
            return true;
        }
    }

    // ---- 反射访问原版私有成员（缓存;失败返回 null/空,绝不崩） ----

    private static Field guiChatField;
    private static Field allMessagesField;

    public static ChatComponent chat() {
        try {
            if (guiChatField == null) {
                guiChatField = net.minecraft.client.gui.Gui.class.getDeclaredField("chat");
                guiChatField.setAccessible(true);
            }
            return (ChatComponent) guiChatField.get(Minecraft.getInstance().gui);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 全量消息（含未裁剪原文;反射读 allMessages） */
    @SuppressWarnings("unchecked")
    public static List<GuiMessage> allMessages() {
        var chat = chat();
        if (chat == null) return List.of();
        try {
            if (allMessagesField == null) {
                allMessagesField = ChatComponent.class.getDeclaredField("allMessages");
                allMessagesField.setAccessible(true);
            }
            var list = (List<GuiMessage>) allMessagesField.get(chat);
            return list == null ? List.of() : list;
        } catch (Throwable t) {
            return List.of();
        }
    }

    // ---- 窗口几何（纯函数,可单测） ----

    /** 窗口最小尺寸 */
    public static final int MIN_W = 120, MIN_H = 60, MAX_W = 800, MAX_H = 600;
    /** 窗口内边距（内容宽 = width - PAD*2） */
    public static final int PAD = 3;
    /** 行高（原版聊天行距 9） */
    public static final int LINE_H = 9;

    /** 解析窗口矩形：-1 用默认（左下贴边）;钳制到屏幕与 min/max */
    public static int[] resolveRect(ChatWindowConfig c, int screenW, int screenH) {
        int w = clamp(c.width, MIN_W, Math.min(MAX_W, Math.max(MIN_W, screenW - 8)));
        int h = clamp(c.height, MIN_H, Math.min(MAX_H, Math.max(MIN_H, screenH - 8)));
        int x = c.x < 0 ? 4 : clamp(c.x, 0, Math.max(0, screenW - w));
        int y = c.y < 0 ? Math.max(0, screenH - h - 4) : clamp(c.y, 0, Math.max(0, screenH - h));
        return new int[]{x, y, w, h};
    }

    /** 窗口内容区可容纳的行数 */
    public static int linesPerPage(ChatWindowConfig c, int h) {
        return Math.max(1, (h - PAD * 2) / LINE_H);
    }

    /** 静默淡出系数：最新消息年龄 ≤ hold 内恒显,超过后 600ms 线性淡至 0.12（不完全消失便于找回） */
    public static float windowAlpha(ChatWindowConfig c, long newestAddedAt, long now) {
        if (c.idleFadeSecs <= 0) return 1f;
        long hold = c.idleFadeSecs * 1000L;
        long age = now - newestAddedAt;
        if (age <= hold) return 1f;
        float f = 1f - Math.min(1f, (age - hold) / 600f);
        return 0.12f + 0.88f * f;
    }

    /** 插入动画偏移：新消息行前 220ms 自 -6px 滑入（easeOutQuad）,返回 {yOff, alpha} */
    public static float[] insertAnim(ChatWindowConfig c, long addedAt, long now) {
        if (!c.animEnabled) return new float[]{0f, 1f};
        float t = Math.min(1f, (now - addedAt) / 220f);
        if (t >= 1f) return new float[]{0f, 1f};
        float e = 1f - (1f - t) * (1f - t);
        return new float[]{-6f * (1f - e), t};
    }

    /** 弹出动画（打开聊天窗口期）:150ms scale 0.95→1 + 淡入;返回 {scale, alpha} */
    public static float[] popAnim(ChatWindowConfig c, long openedAt, long now) {
        if (!c.animEnabled) return new float[]{1f, 1f};
        float t = Math.min(1f, (now - openedAt) / 150f);
        if (t >= 1f) return new float[]{1f, 1f};
        float e = 1f - (1f - t) * (1f - t);
        return new float[]{0.95f + 0.05f * e, t};
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 从 allMessages 视角把"新到旧"列表反转为渲染用"旧到新"（新在底部） */
    public static List<GuiMessage> oldestFirst(List<GuiMessage> all) {
        var out = new ArrayList<GuiMessage>(all.size());
        for (int i = all.size() - 1; i >= 0; i--) out.add(all.get(i));
        return out;
    }
}
