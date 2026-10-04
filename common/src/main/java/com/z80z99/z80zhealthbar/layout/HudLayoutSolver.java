package com.z80z99.z80zhealthbar.layout;

import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig.ComponentLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CUSTOM 模式布局求解器 —— 纯函数、无 MC 依赖，可单元测试。
 *
 * <p>编辑器预览与运行时渲染都通过 {@link #solve} 计算组件位置，
 * 保证"编辑器与实际游戏 HUD 位置一致"（任务书 4.4）。
 *
 * <p>规则：组件按锚点分组；组内按 components 的插入顺序（health→food→air→exp→armor→mount）
 * 堆叠；TOP 锚点向下堆叠、BOTTOM 锚点向上堆叠（BOTTOM_CENTER 基线 = 物品栏上方 39px）；
 * 水平方向：LEFT 左对齐、RIGHT 右对齐、CENTER 居中（以条宽计算后叠加拖拽偏移）。
 */
public final class HudLayoutSolver {

    /** 屏幕边距（GUI 缩放坐标） */
    public static final int MARGIN_X = 5;
    public static final int MARGIN_Y = 5;

    private HudLayoutSolver() {}

    /** 组件渲染框（左上角 + 尺寸） */
    public record Box(int x, int y, int width, int height) {}

    /**
     * @param layout  布局配置
     * @param sizes   每组件未缩放尺寸（width/height，ICON 模式通常 9×9，BAR 模式为条尺寸）
     * @param screenW/H GUI 缩放后的屏幕尺寸
     * @return key → Box（含 scale 后实际尺寸与最终位置）
     */
    public static Map<String, Box> solve(HudLayoutConfig layout, Map<String, int[]> sizes,
                                         int screenW, int screenH) {
        Map<String, Box> result = new LinkedHashMap<>();

        // 按锚点分组（保持插入顺序）
        Map<HudAnchor, List<String>> groups = new LinkedHashMap<>();
        for (HudAnchor a : HudAnchor.values()) groups.put(a, new ArrayList<>());
        for (String key : layout.components.keySet()) {
            ComponentLayout c = layout.get(key);
            if (c.modeParsed() == HudLayoutConfig.ComponentMode.OFF) continue;
            groups.get(c.anchorParsed()).add(key);
        }

        for (Map.Entry<HudAnchor, List<String>> entry : groups.entrySet()) {
            HudAnchor anchor = entry.getKey();
            List<String> keys = entry.getValue();
            if (keys.isEmpty()) continue;

            // 计算该组缩放后的总高（含间距）
            int totalH = 0;
            for (String key : keys) {
                ComponentLayout c = layout.get(key);
                int[] size = sizes.getOrDefault(key, new int[]{0, 0});
                totalH += Math.max(1, (int) Math.round(size[1] * c.scale));
                totalH += c.spacing;
            }
            totalH -= layout.get(keys.get(keys.size() - 1)).spacing;

            int baseX = anchor.baseX(screenW, MARGIN_X);
            int baseY = anchor.baseY(screenH, MARGIN_Y);

            // 组内起始 Y：TOP 从基线向下；BOTTOM 组底对齐基线（向上堆叠）
            int cursorY = anchor.isTop() || anchor.isMiddle()
                    ? baseY
                    : baseY - totalH;

            for (String key : keys) {
                ComponentLayout c = layout.get(key);
                int[] size = sizes.getOrDefault(key, new int[]{0, 0});
                int w = Math.max(1, (int) Math.round(size[0] * c.scale));
                int h = Math.max(1, (int) Math.round(size[1] * c.scale));

                int x = switch (anchor.horizontalAlign()) {
                    case -1 -> baseX;
                    case 1 -> baseX - w;
                    default -> baseX - w / 2;
                };
                x += c.offsetX;

                int y = cursorY + c.offsetY;
                result.put(key, new Box(x, y, w, h));

                cursorY += h + c.spacing;
            }
        }

        // 分离元素（文本/图标配置了独立锚点时生成独立框，单独成组不参与堆叠）
        for (String key : layout.components.keySet()) {
            ComponentLayout c = layout.get(key);
            if (c.modeParsed() == HudLayoutConfig.ComponentMode.OFF) continue;
            addDetached(result, sizes, c, key + ".text", c.textAnchorParsed(),
                    c.textOffsetX, c.textOffsetY, screenW, screenH);
            addDetached(result, sizes, c, key + ".icon", c.iconAnchorParsed(),
                    c.iconOffsetX, c.iconOffsetY, screenW, screenH);
        }
        return result;
    }

    /** 分离元素定位：单独成组（锚点基线上下对齐 + 自身偏移，不参与组件堆叠） */
    private static void addDetached(Map<String, Box> out, Map<String, int[]> sizes,
                                    ComponentLayout c, String elKey, HudAnchor anchor,
                                    int offX, int offY, int screenW, int screenH) {
        if (anchor == null) return;
        int[] size = sizes.get(elKey);
        if (size == null) return;
        int w = Math.max(1, (int) Math.round(size[0] * c.scale));
        int h = Math.max(1, (int) Math.round(size[1] * c.scale));
        int baseX = anchor.baseX(screenW, MARGIN_X);
        int baseY = anchor.baseY(screenH, MARGIN_Y);
        int x = switch (anchor.horizontalAlign()) {
            case -1 -> baseX;
            case 1 -> baseX - w;
            default -> baseX - w / 2;
        };
        int y = anchor.isTop() || anchor.isMiddle() ? baseY : baseY - h;
        out.put(elKey, new Box(x + offX, y + offY, w, h));
    }

    /** 组件未缩放尺寸（渲染器与编辑器统一使用，保证测量一致） */
    public static int[] measure(HudLayoutConfig.ComponentLayout c) {
        if (c.modeParsed() == HudLayoutConfig.ComponentMode.ICON) {
            return new int[]{54, 9}; // 6 个 9px 图标位（渲染时按实际数量收缩）
        }
        // 卡片高度 = 可配置条高（默认 9）+ 2px 度量余量（旧默认 7+4=11 保持不变）
        int barH = Math.max(5, Math.min(16, c.barHeight > 0 ? c.barHeight : 9));
        return new int[]{c.barWidth, barH + 2};
    }
}
