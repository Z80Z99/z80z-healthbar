package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.OverlayConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 长条（ASTEORBAR）样式的逐条自由摆放（HUD 布局编辑器拖拽写回）。
 *
 * <p>free=true 的条脱离预设布局（overlayLayoutStyle 0-8 + 边距堆叠）按绝对坐标渲染;
 * 所有条每帧渲染时记录矩形,编辑器据此画框选与拖拽（与自定义样式同款体验）。
 */
public final class BarLayouts {

    /** 可自由摆放的长条键（与 z80zhealthbar.hud.component.* 文案键一致） */
    public static final List<String> KEYS = List.of("health", "food", "air", "armor", "mount", "experience");

    /** 本帧渲染记录的条矩形（编辑器框选用）: key → {x, y, w, h} */
    private static final Map<String, int[]> LAST_RECTS = new LinkedHashMap<>();

    /** 取某条的自由摆放数据（无则创建占位,free=false） */
    public static OverlayConfig.BarFreePos get(String key) {
        return ConfigManager.getConfig().overlay.barFreePos
                .computeIfAbsent(key, k -> new OverlayConfig.BarFreePos());
    }

    /** free 条的绝对位置（钳制在屏幕内）;非 free 返回 null → 调用方走预设布局 */
    public static int[] resolve(String key, int screenW, int screenH, int barW, int barH) {
        OverlayConfig.BarFreePos p = ConfigManager.getConfig().overlay.barFreePos.get(key);
        if (p == null || !p.free) return null;
        return new int[]{
                Math.max(0, Math.min(p.x, Math.max(0, screenW - barW))),
                Math.max(0, Math.min(p.y, Math.max(0, screenH - barH)))
        };
    }

    public static void record(String key, int x, int y, int w, int h) {
        LAST_RECTS.put(key, new int[]{x, y, w, h});
    }

    public static int[] lastRect(String key) {
        return LAST_RECTS.get(key);
    }

    /** 每帧预览渲染前清空（条消失时不残留旧框） */
    public static void clearRects() {
        LAST_RECTS.clear();
    }

    /** 单条释放回预设布局 */
    public static void reset(String key) {
        ConfigManager.getConfig().overlay.barFreePos.remove(key);
    }

    /** 全部释放回预设布局 */
    public static void resetAll() {
        ConfigManager.getConfig().overlay.barFreePos.clear();
    }

    private BarLayouts() {}
}
