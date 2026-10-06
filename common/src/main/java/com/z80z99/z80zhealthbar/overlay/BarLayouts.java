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
    /** 本帧渲染记录的状态图标矩形（编辑器框选/拖拽拆分用）: key → {x, y, w, h} */
    private static final Map<String, int[]> LAST_ICON_RECTS = new LinkedHashMap<>();
    /** 本帧渲染记录的数值文本矩形（编辑器框选/拖拽拆分用）: key → {x, y, w, h} */
    private static final Map<String, int[]> LAST_TEXT_RECTS = new LinkedHashMap<>();

    /** 取某条的组件配置（无则创建占位,默认可见/显示图标/无偏移） */
    public static OverlayConfig.BarFreePos get(String key) {
        return ConfigManager.getConfig().overlay.barFreePos
                .computeIfAbsent(key, k -> new OverlayConfig.BarFreePos());
    }

    /** 单条显示开关（关闭 = 整条不渲染,即"删除血条组件"） */
    public static boolean visible(String key) {
        OverlayConfig.BarFreePos p = ConfigManager.getConfig().overlay.barFreePos.get(key);
        return p == null || p.visible;
    }

    /** 状态图标开关（关闭 = 只渲染条本体） */
    public static boolean showIcon(String key) {
        OverlayConfig.BarFreePos p = ConfigManager.getConfig().overlay.barFreePos.get(key);
        return p == null || p.showIcon;
    }

    /** 数值文本开关（关闭 = 只渲染条与图标;全局开关与此为"与"关系） */
    public static boolean showText(String key) {
        OverlayConfig.BarFreePos p = ConfigManager.getConfig().overlay.barFreePos.get(key);
        return p == null || p.showText;
    }

    /** 数值文本相对默认位的独立偏移——文本与条拆开摆放 */
    public static int[] textOffset(String key) {
        OverlayConfig.BarFreePos p = ConfigManager.getConfig().overlay.barFreePos.get(key);
        return p == null ? new int[]{0, 0} : new int[]{p.textOffX, p.textOffY};
    }

    /** 状态图标相对条默认位（条左外 -11px）的独立偏移——图标与条拆开摆放 */
    public static int[] iconOffset(String key) {
        OverlayConfig.BarFreePos p = ConfigManager.getConfig().overlay.barFreePos.get(key);
        return p == null ? new int[]{0, 0} : new int[]{p.iconOffX, p.iconOffY};
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

    public static void recordIcon(String key, int x, int y, int w, int h) {
        LAST_ICON_RECTS.put(key, new int[]{x, y, w, h});
    }

    public static void recordText(String key, int x, int y, int w, int h) {
        LAST_TEXT_RECTS.put(key, new int[]{x, y, w, h});
    }

    public static int[] lastRect(String key) {
        return LAST_RECTS.get(key);
    }

    public static int[] lastIconRect(String key) {
        return LAST_ICON_RECTS.get(key);
    }

    public static int[] lastTextRect(String key) {
        return LAST_TEXT_RECTS.get(key);
    }

    /** 每帧预览渲染前清空（条消失时不残留旧框） */
    public static void clearRects() {
        LAST_RECTS.clear();
        LAST_ICON_RECTS.clear();
        LAST_TEXT_RECTS.clear();
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
