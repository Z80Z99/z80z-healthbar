package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.overlay.parts.StringOverlay;
import com.z80z99.z80zhealthbar.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;

public final class OverlayManager {
    // 全局覆盖层实例
    public static final StringOverlay STRING = new StringOverlay();

    // 布局常量
    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    // 全局状态
    public static int cornerLeftHeight = 39;
    public static int cornerRightHeight = 39;
    public static int horizontalOffset;
    public static int length;
    public static int leftHeight = 39;
    public static int rightHeight = 39;

    // 所有注册的渲染器和文本渲染器
    private static final List<RenderEntry> entries = new ArrayList<>();
    private static final List<StringRenderEntry> stringEntries = new ArrayList<>();
    private static boolean initialized;

    // 预定义的布局顺序
    private static final List<Pair<BaseOverlay, OverlayPosition>> ORDER_ABOVE_HOT_BAR_LONG = new ArrayList<>();
    private static final List<Pair<BaseOverlay, OverlayPosition>> ORDER_ABOVE_HOT_BAR_SHORT = new ArrayList<>();
    private static final List<Pair<BaseOverlay, OverlayPosition>> ORDER_TOP_BOTH_SIDES = new ArrayList<>();
    private static final List<Pair<BaseOverlay, OverlayPosition>> ORDER_BOTTOM_BOTH_SIDES = new ArrayList<>();
    private static final List<Pair<BaseOverlay, OverlayPosition>> ORDER_TOP_LEFT = new ArrayList<>();
    private static final List<Pair<BaseOverlay, OverlayPosition>> ORDER_TOP_RIGHT = new ArrayList<>();
    private static final List<Pair<BaseOverlay, OverlayPosition>> ORDER_BOTTOM_LEFT = new ArrayList<>();
    private static final List<Pair<BaseOverlay, OverlayPosition>> ORDER_BOTTOM_RIGHT = new ArrayList<>();

    private static final List<List<Pair<BaseOverlay, OverlayPosition>>> ORDER = Arrays.asList(
            Collections.emptyList(),  // NONE
            ORDER_ABOVE_HOT_BAR_LONG,
            ORDER_ABOVE_HOT_BAR_SHORT,
            ORDER_TOP_BOTH_SIDES,
            ORDER_BOTTOM_BOTH_SIDES,
            ORDER_TOP_LEFT,
            ORDER_TOP_RIGHT,
            ORDER_BOTTOM_LEFT,
            ORDER_BOTTOM_RIGHT
    );

    private OverlayManager() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        // Wave 4 修复：原先 registerOverlay 因 "if (order.isEmpty()) continue" 导致首次注册时全部跳过，
        // 任何布局都拿不到 overlay，HUD 在游戏内空白。这里按 AsteorBar 设计为每种布局直接填入
        // 正确的 (overlay, position) 列表。后续 compat 插件可继续用 registerOverlay 追加。
        var main = HudRenderer.MAIN;
        var player = HudRenderer.PLAYER_HEALTH;
        var food = HudRenderer.FOOD_LEVEL;
        var air = HudRenderer.AIR_LEVEL;
        var exp = HudRenderer.EXPERIENCE_BAR;
        var mount = HudRenderer.MOUNT_HEALTH;
        var armor = HudRenderer.ARMOR_LEVEL;

        // Style 0 = NONE：保持空，MainOverlay 已 early-return
        // Style 1 = ABOVE_HOT_BAR_LONG：6 条全 CENTER 长条堆叠
        ORDER_ABOVE_HOT_BAR_LONG.add(pairOf(player, OverlayPosition.CENTER));
        ORDER_ABOVE_HOT_BAR_LONG.add(pairOf(food,    OverlayPosition.CENTER));
        ORDER_ABOVE_HOT_BAR_LONG.add(pairOf(air,     OverlayPosition.CENTER));
        ORDER_ABOVE_HOT_BAR_LONG.add(pairOf(exp,     OverlayPosition.CENTER));
        ORDER_ABOVE_HOT_BAR_LONG.add(pairOf(mount,   OverlayPosition.LEFT));
        ORDER_ABOVE_HOT_BAR_LONG.add(pairOf(armor,   OverlayPosition.RIGHT));

        // Style 2 = ABOVE_HOT_BAR_SHORT：同 1，仅 OverlayManager.length 配置不同（视觉差异由配置决定）
        ORDER_ABOVE_HOT_BAR_SHORT.add(pairOf(player, OverlayPosition.CENTER));
        ORDER_ABOVE_HOT_BAR_SHORT.add(pairOf(food,    OverlayPosition.CENTER));
        ORDER_ABOVE_HOT_BAR_SHORT.add(pairOf(air,     OverlayPosition.CENTER));
        ORDER_ABOVE_HOT_BAR_SHORT.add(pairOf(exp,     OverlayPosition.CENTER));
        ORDER_ABOVE_HOT_BAR_SHORT.add(pairOf(mount,   OverlayPosition.LEFT));
        ORDER_ABOVE_HOT_BAR_SHORT.add(pairOf(armor,   OverlayPosition.RIGHT));

        // Style 3 = TOP_BOTH_SIDES：血/食/经验 CENTER，气/坐骑 LEFT，护甲 RIGHT
        ORDER_TOP_BOTH_SIDES.add(pairOf(player, OverlayPosition.CENTER));
        ORDER_TOP_BOTH_SIDES.add(pairOf(food,    OverlayPosition.CENTER));
        ORDER_TOP_BOTH_SIDES.add(pairOf(air,     OverlayPosition.LEFT));
        ORDER_TOP_BOTH_SIDES.add(pairOf(exp,     OverlayPosition.CENTER));
        ORDER_TOP_BOTH_SIDES.add(pairOf(mount,   OverlayPosition.LEFT));
        ORDER_TOP_BOTH_SIDES.add(pairOf(armor,   OverlayPosition.RIGHT));

        // Style 4 = BOTTOM_BOTH_SIDES：V1 无底部锚点（renderGui.height 从顶部累加），暂同 3 的视觉
        ORDER_BOTTOM_BOTH_SIDES.add(pairOf(player, OverlayPosition.CENTER));
        ORDER_BOTTOM_BOTH_SIDES.add(pairOf(food,    OverlayPosition.CENTER));
        ORDER_BOTTOM_BOTH_SIDES.add(pairOf(air,     OverlayPosition.LEFT));
        ORDER_BOTTOM_BOTH_SIDES.add(pairOf(exp,     OverlayPosition.CENTER));
        ORDER_BOTTOM_BOTH_SIDES.add(pairOf(mount,   OverlayPosition.LEFT));
        ORDER_BOTTOM_BOTH_SIDES.add(pairOf(armor,   OverlayPosition.RIGHT));

        // Style 5 = TOP_LEFT：6 条全 LEFT
        ORDER_TOP_LEFT.add(pairOf(player, OverlayPosition.LEFT));
        ORDER_TOP_LEFT.add(pairOf(food,    OverlayPosition.LEFT));
        ORDER_TOP_LEFT.add(pairOf(air,     OverlayPosition.LEFT));
        ORDER_TOP_LEFT.add(pairOf(exp,     OverlayPosition.LEFT));
        ORDER_TOP_LEFT.add(pairOf(mount,   OverlayPosition.LEFT));
        ORDER_TOP_LEFT.add(pairOf(armor,   OverlayPosition.LEFT));

        // Style 6 = TOP_RIGHT：6 条全 RIGHT
        ORDER_TOP_RIGHT.add(pairOf(player, OverlayPosition.RIGHT));
        ORDER_TOP_RIGHT.add(pairOf(food,    OverlayPosition.RIGHT));
        ORDER_TOP_RIGHT.add(pairOf(air,     OverlayPosition.RIGHT));
        ORDER_TOP_RIGHT.add(pairOf(exp,     OverlayPosition.RIGHT));
        ORDER_TOP_RIGHT.add(pairOf(mount,   OverlayPosition.RIGHT));
        ORDER_TOP_RIGHT.add(pairOf(armor,   OverlayPosition.RIGHT));

        // Style 7 = BOTTOM_LEFT：V1 同 5
        ORDER_BOTTOM_LEFT.add(pairOf(player, OverlayPosition.LEFT));
        ORDER_BOTTOM_LEFT.add(pairOf(food,    OverlayPosition.LEFT));
        ORDER_BOTTOM_LEFT.add(pairOf(air,     OverlayPosition.LEFT));
        ORDER_BOTTOM_LEFT.add(pairOf(exp,     OverlayPosition.LEFT));
        ORDER_BOTTOM_LEFT.add(pairOf(mount,   OverlayPosition.LEFT));
        ORDER_BOTTOM_LEFT.add(pairOf(armor,   OverlayPosition.LEFT));

        // Style 8 = BOTTOM_RIGHT：V1 同 6
        ORDER_BOTTOM_RIGHT.add(pairOf(player, OverlayPosition.RIGHT));
        ORDER_BOTTOM_RIGHT.add(pairOf(food,    OverlayPosition.RIGHT));
        ORDER_BOTTOM_RIGHT.add(pairOf(air,     OverlayPosition.RIGHT));
        ORDER_BOTTOM_RIGHT.add(pairOf(exp,     OverlayPosition.RIGHT));
        ORDER_BOTTOM_RIGHT.add(pairOf(mount,   OverlayPosition.RIGHT));
        ORDER_BOTTOM_RIGHT.add(pairOf(armor,   OverlayPosition.RIGHT));
    }

    private static Pair<BaseOverlay, OverlayPosition> pairOf(BaseOverlay o, OverlayPosition p) {
        return new Pair<>(o, p);
    }

    public static List<Pair<BaseOverlay, OverlayPosition>> getCurrentOrder() {
        init(); // Wave 4 修复：确保布局顺序已被填充，与 reset() 无关的调用方也能拿到非空列表
        int style = ConfigManager.getConfig().overlay.overlayLayoutStyle;
        if (style <= 0 || style >= ORDER.size()) return ORDER_ABOVE_HOT_BAR_LONG;
        return ORDER.get(style);
    }

    /** 注册覆盖层到指定布局 */
    public static void registerOverlay(BaseOverlay overlay, OverlayPosition position) {
        registerOverlay(overlay, null, position, 0);
    }

    public static void registerOverlay(BaseOverlay newOverlay, BaseOverlay anchorOverlay,
                                        OverlayPosition position, int insertMode) {
        init(); // Wave 4 修复：注册前确保 8 个布局列表已被 init 填充，否则 isEmpty 跳过会让 compat 无法追加
        if (position == OverlayPosition.UNSPECIFIED) return;

        for (List<Pair<BaseOverlay, OverlayPosition>> order : ORDER) {
            if (order.isEmpty()) continue;  // 仅 NONE (style 0) 列表为空 → 跳过正确
            // 移除旧的同类型
            order.removeIf(p -> p.getKey().getClass() == newOverlay.getClass());

            if (anchorOverlay == null) {
                int idx = insertMode == 0 ? 0 : order.size();
                order.add(idx, new Pair<>(newOverlay, position));
            } else {
                int idx = findIndex(order, anchorOverlay.getClass());
                if (idx >= 0) {
                    order.add(idx + insertMode, new Pair<>(newOverlay, position));
                }
            }
        }
    }

    private static int findIndex(List<Pair<BaseOverlay, OverlayPosition>> list, Class<?> clazz) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getKey().getClass() == clazz) return i;
        }
        return -1;
    }

    /** 重置布局状态，每次渲染帧调用 */
    public static void reset() {
        init();
        var cfg = ConfigManager.getConfig().overlay;
        cornerLeftHeight = cfg.cornerVerticalPadding;
        cornerRightHeight = cfg.cornerVerticalPadding;
        horizontalOffset = cfg.cornerHorizontalPadding;
        length = cfg.cornerBarLength;
        leftHeight = 39;
        rightHeight = 39;
    }

    /** 渲染文本覆盖层 */
    public static void renderStrings(GuiGraphics graphics) {
        var cfg = ConfigManager.getConfig().overlay;
        float textScale = (float) cfg.overlayTextScale;

        graphics.pose().pushPose();
        graphics.pose().scale(textScale, textScale, 1.0f);
        var font = Minecraft.getInstance().font;

        for (StringRenderEntry entry : stringEntries) {
            int textWidth = font.width(entry.text);
            float x;
            if (entry.align == ALIGN_LEFT) {
                x = entry.x / textScale;
            } else if (entry.align == ALIGN_RIGHT) {
                x = entry.x / textScale - textWidth;
            } else {
                x = entry.x / textScale - textWidth / 2.0f;
            }
            graphics.pose().pushPose();
            graphics.drawString(font, entry.text, (int)x, (int)(entry.y / textScale), entry.color);
            graphics.pose().popPose();
        }

        graphics.pose().popPose();
    }

    /** 添加文本覆盖层（居中） */
    public static void addStringRender(String text, int x, int y, int color) {
        stringEntries.add(new StringRenderEntry(text, x, y, color, ALIGN_CENTER));
    }

    /** 添加文本覆盖层（指定对齐：ALIGN_LEFT / ALIGN_CENTER / ALIGN_RIGHT，x 为对应锚点） */
    public static void addStringRender(String text, int x, int y, int color, int align) {
        stringEntries.add(new StringRenderEntry(text, x, y, color, align));
    }

    public static void clearStringRenders() {
        stringEntries.clear();
    }

    // 内部类
    public static class StringRenderEntry {
        final String text;
        final int x, y, color;
        final int align;
        StringRenderEntry(String text, int x, int y, int color, int align) {
            this.text = text; this.x = x; this.y = y; this.color = color; this.align = align;
        }
    }

    public static class RenderEntry {
        final BaseOverlay overlay;
        final OverlayPosition position;
        RenderEntry(BaseOverlay overlay, OverlayPosition position) {
            this.overlay = overlay; this.position = position;
        }
    }
}
