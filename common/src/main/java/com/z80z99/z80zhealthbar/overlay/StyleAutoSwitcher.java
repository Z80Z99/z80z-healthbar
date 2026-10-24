package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig.ComponentLayout;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按窗口状态自动切换用户样式（1.0.147）。
 *
 * <p>三档窗口状态：全屏 / 最大化 / 窗口化（含任意窗口尺寸）。分辨率与 GUI 缩放变化
 * 由 1.0.144 的比例缩放承接，本类只管窗口形态。
 *
 * <p><b>各状态独立记忆（live-slot 语义）</b>：切走时把当前活动布局写回原状态绑定的样式，
 * 切回时恢复——在窗口化下改布局不影响全屏布局。全程内存操作不落盘（绑定关系落盘；
 * 下次启动按当前状态自动重放，即"游戏启动时默认窗口大小"自动应用窗口化样式）。
 *
 * <p>判定逻辑抽成纯函数（{@link #stateOf}/{@link #boundStyleName}）以便单测；
 * 界面打开期间（含编辑器）不切换，回到游戏后按状态差补切，防止编辑器会话被中途换底。
 */
public final class StyleAutoSwitcher {

    public enum WindowState { WINDOWED, MAXIMIZED, FULLSCREEN }

    private static WindowState last;

    static {
        // 文件热重载后重放当前状态样式（cur==last 不触发的问题,审计 #5）;
        // restoreJson（编辑器放弃/保存）不触发——不应覆盖刚还原/保存的布局
        com.z80z99.z80zhealthbar.config.ConfigManager
                .setOnConfigReloaded(StyleAutoSwitcher::invalidate);
    }

    /** 丢弃已见状态：下一 tick 按当前状态重放（prev=null 只载入不写回,语义安全） */
    public static void invalidate() {
        last = null;
    }

    private StyleAutoSwitcher() {}

    /** 窗口状态归约（纯函数）：全屏优先于最大化 */
    public static WindowState stateOf(boolean fullscreen, boolean maximized) {
        if (fullscreen) return WindowState.FULLSCREEN;
        return maximized ? WindowState.MAXIMIZED : WindowState.WINDOWED;
    }

    /** 该状态绑定的样式名（纯函数;空串 = 未绑定） */
    public static String boundStyleName(HudLayoutConfig layout, WindowState state) {
        if (layout == null || state == null) return "";
        return switch (state) {
            case FULLSCREEN -> nz(layout.styleFullscreen);
            case MAXIMIZED -> nz(layout.styleMaximized);
            case WINDOWED -> nz(layout.styleWindowed);
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 运行时检测当前窗口状态（common 直接用 GLFW——ColorWheelScreen 等已有先例） */
    public static WindowState detect(Minecraft mc) {
        try {
            if (mc.getWindow().isFullscreen()) return WindowState.FULLSCREEN;
            if (org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED) != 0) return WindowState.MAXIMIZED;
        } catch (Throwable ignored) {
            // 无窗口句柄的极端环境:按窗口化处理
        }
        return WindowState.WINDOWED;
    }

    /** 每 tick 调用（Forge ClientTick END / Fabric END_CLIENT_TICK） */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            last = null; // 退出世界复位:下次进世界按"首帧"语义处理
            return;
        }
        // 任何界面打开时不切换（含 HUD 编辑器）——回到游戏后 cur != last 自动补切
        if (mc.screen != null) return;
        WindowState cur = detect(mc);
        WindowState prev = last;
        last = cur;
        var layout = ConfigManager.getConfig().hudLayout;
        if (!layout.autoStyleSwitch) return;
        if (cur == prev) return;
        // live-slot:切走写回（首帧 prev=null 不写回任何样式）
        if (prev != null) {
            String prevName = boundStyleName(layout, prev);
            var prevStyle = findStyle(layout, prevName);
            if (prevStyle != null) saveLiveToStyle(layout, prevStyle);
        }
        String curName = boundStyleName(layout, cur);
        var curStyle = findStyle(layout, curName);
        if (curStyle != null) loadStyleToLive(layout, curStyle);
    }

    /** 按名查样式（找不到返回 null;名空白视为未绑定） */
    public static HudLayoutConfig.UserStyle findStyle(HudLayoutConfig layout, String name) {
        if (layout == null || name == null || name.isBlank()
                || layout.userStyles == null) return null;
        for (var st : layout.userStyles) {
            if (st != null && name.equals(st.name)) return st;
        }
        return null;
    }

    /** 当前活动布局 → 样式快照（深拷;内存操作） */
    public static void saveLiveToStyle(HudLayoutConfig layout, HudLayoutConfig.UserStyle style) {
        style.components = new LinkedHashMap<>();
        for (Map.Entry<String, ComponentLayout> e : layout.components.entrySet()) {
            style.components.put(e.getKey(),
                    e.getValue() == null ? new ComponentLayout() : e.getValue().copy());
        }
        style.scaleMode = layout.scaleMode;
        style.referenceWidth = layout.referenceWidth;
        style.referenceHeight = layout.referenceHeight;
    }

    /** 样式快照 → 当前活动布局（深拷;内存操作,不落盘） */
    public static void loadStyleToLive(HudLayoutConfig layout, HudLayoutConfig.UserStyle style) {
        Map<String, ComponentLayout> fresh = new LinkedHashMap<>();
        for (Map.Entry<String, ComponentLayout> e : style.components.entrySet()) {
            fresh.put(e.getKey(),
                    e.getValue() == null ? new ComponentLayout() : e.getValue().copy());
        }
        layout.components = fresh;
        layout.scaleMode = style.scaleMode;
        layout.referenceWidth = style.referenceWidth;
        layout.referenceHeight = style.referenceHeight;
    }

    /** 供编辑器"当前状态"提示行复用 */
    public static WindowState currentState() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? WindowState.WINDOWED : detect(mc);
    }
}
