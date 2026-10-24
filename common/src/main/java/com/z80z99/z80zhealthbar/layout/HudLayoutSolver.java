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

    // ================= 原版图标行计数（测量与绘制共用,避免两处公式漂移） =================

    /** 单个图标格宽度（原版 icons.png 9×9） */
    public static final int ICON_W = 9;

    public static int healthHeartCount(float maxHealth) {
        return Math.max(0, (int) Math.ceil(maxHealth / 2f));
    }

    /** 吸收心形数量（mode 4 = 关闭,不绘制） */
    public static int absorptionHeartCount(float absorption, int absorptionMode) {
        if (absorptionMode == 4 || absorption <= 0) return 0;
        return Math.max(0, (int) Math.ceil(absorption / 2f));
    }

    /** 饱食度图标固定 10 个 */
    public static int foodIconCount() {
        return 10;
    }

    /** 护甲图标数：2 点护甲 = 1 个图标,最多 10 个 */
    public static int armorIconCount(int armor) {
        return Math.max(0, Math.min(10, (int) Math.ceil(armor / 2f)));
    }

    /** 氧气气泡数：按当前/上限比例,最多 10 个 */
    public static int airBubbleCount(int air, int maxAir) {
        int max = Math.max(1, maxAir);
        return Math.max(0, Math.min(10, (int) Math.ceil(air / (float) max * 10)));
    }

    /** 图标行盒体尺寸（至少保留 1 格,避免空状态时选择框缩成 1px 无法点中） */
    public static int[] iconRowSize(int icons) {
        return new int[]{Math.max(1, icons) * ICON_W, ICON_W};
    }

    /** 组件盒体在屏幕内钳制（组件比屏幕大时贴左上,不做压缩变形） */
    private static int clampPos(int v, int size, int screen) {
        return Math.max(0, Math.min(v, Math.max(0, screen - size)));
    }

    /**
     * 比例缩放因子 k（scaleMode=1 时按参考分辨率折算,否则恒 1）。
     *
     * <p>取两轴最小值（uniform）——HUD 元素不做非等比拉伸;宽高比不同的屏幕上
     * 边缘/居中锚点本就相对屏幕（天然适配）,内部偏移与尺寸按小轴统一缩放。
     * 钳制 [0.4, 2.5] 防极端参考值把组件缩没或撑爆。
     */
    public static double scaleK(HudLayoutConfig layout, int screenW, int screenH) {
        if (layout == null || layout.scaleMode != 1) return 1.0;
        int rw = layout.referenceWidth <= 0 ? 960 : layout.referenceWidth;
        int rh = layout.referenceHeight <= 0 ? 540 : layout.referenceHeight;
        double k = Math.min(screenW / (double) rw, screenH / (double) rh);
        return Math.max(0.4, Math.min(2.5, k));
    }

    /** 方向键微移的 offset 步长：屏幕步长 ÷k,保底 1——k>2 时 round(1/k)=0 会让按键完全失效（审计 #1） */
    public static int nudgeStep(int screenStepPx, double k) {
        if (k <= 0.0001) return screenStepPx;
        return Math.max(1, (int) Math.round(screenStepPx / k));
    }

    /**
     * @param layout  布局配置
     * @param sizes   每组件未缩放尺寸（width/height，ICON 模式通常 9×9，BAR 模式为条尺寸）
     * @param screenW/H GUI 缩放后的屏幕尺寸
     * @return key → Box（含 scale 后实际尺寸与最终位置）
     */
    public static Map<String, Box> solve(HudLayoutConfig layout, Map<String, int[]> sizes,
                                         int screenW, int screenH) {
        Map<String, Box> result = new LinkedHashMap<>();
        // 比例缩放因子:位置(offset/spacing)与尺寸(size×scale)统一乘 k(1.0.144 参考分辨率模式)
        double K = scaleK(layout, screenW, screenH);

        // 按锚点分组（保持插入顺序）。stack=false（默认）的组件不参与堆叠游标——
        // 各自按锚点+偏移独立定位,改尺寸/缩放不会推移其它组件（实测反馈）
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

            // 计算该组参与堆叠的组件总高（含间距;stack=false 的不参与）
            List<String> stacked = new java.util.ArrayList<>();
            for (String key : keys) {
                if (layout.get(key).stack) stacked.add(key);
            }

            int totalH = 0;
            for (String key : stacked) {
                ComponentLayout c = layout.get(key);
                int[] size = sizes.getOrDefault(key, new int[]{0, 0});
                totalH += Math.max(1, (int) (int) Math.round(size[1] * c.scale * K));
                totalH += (int) Math.round(c.spacing * K);
            }
            if (!stacked.isEmpty()) totalH -= Math.round(layout.get(stacked.get(stacked.size() - 1)).spacing * K);

            int baseX = anchor.baseX(screenW, MARGIN_X);
            int baseY = anchor.baseY(screenH, MARGIN_Y);

            // 组内起始 Y：TOP 从基线向下；BOTTOM 组底对齐基线（向上堆叠）
            int cursorY = anchor.isTop() || anchor.isMiddle()
                    ? baseY
                    : baseY - totalH;

            for (String key : keys) {
                ComponentLayout c = layout.get(key);
                int[] size = sizes.getOrDefault(key, new int[]{0, 0});
                int w = Math.max(1, (int) (int) Math.round(size[0] * c.scale * K));
                int h = Math.max(1, (int) (int) Math.round(size[1] * c.scale * K));

                int x = switch (anchor.horizontalAlign()) {
                    case -1 -> baseX;
                    case 1 -> baseX - w;
                    default -> baseX - w / 2;
                };
                x += (int) Math.round(c.offsetX * K);

                int y;
                if (c.stack) {
                    y = cursorY + (int) Math.round(c.offsetY * K);
                    cursorY += h + (int) Math.round(c.spacing * K);
                } else {
                    // 独立定位：锚点垂直基线 + 自身偏移（BOTTOM 组以基线为下缘向上）
                    int yFromAnchor = anchor.isTop() || anchor.isMiddle()
                            ? baseY
                            : baseY - h;
                    y = yFromAnchor + (int) Math.round(c.offsetY * K);
                }
                result.put(key, new Box(clampPos(x, w, screenW), clampPos(y, h, screenH), w, h));
            }
        }

        // 分离元素（文本/图标配置了独立锚点时生成独立框，单独成组不参与堆叠）
        for (String key : layout.components.keySet()) {
            ComponentLayout c = layout.get(key);
            if (c.modeParsed() == HudLayoutConfig.ComponentMode.OFF) continue;
            addDetached(result, sizes, c, key + ".text", c.textAnchorParsed(),
                    c.textOffsetX, c.textOffsetY, c.textScale, K, screenW, screenH);
            addDetached(result, sizes, c, key + ".icon", c.iconAnchorParsed(),
                    c.iconOffsetX, c.iconOffsetY, c.iconScale, K, screenW, screenH);
        }
        return result;
    }

    /** 分离元素定位：单独成组（锚点基线上下对齐 + 自身偏移，不参与组件堆叠）。
     *  盒体尺寸 = 组件缩放 × 元素自身缩放 × 比例因子 K，与渲染端 pose.scale(scale × textScale/iconScale × K) 一致。 */
    private static void addDetached(Map<String, Box> out, Map<String, int[]> sizes,
                                    ComponentLayout c, String elKey, HudAnchor anchor,
                                    int offX, int offY, double elementScale, double K,
                                    int screenW, int screenH) {
        if (anchor == null) return;
        int[] size = sizes.get(elKey);
        if (size == null) return;
        double ek = c.scale * (elementScale <= 0 ? 1.0 : elementScale) * K;
        int w = Math.max(1, (int) Math.round(size[0] * ek));
        int h = Math.max(1, (int) Math.round(size[1] * ek));
        int baseX = anchor.baseX(screenW, MARGIN_X);
        int baseY = anchor.baseY(screenH, MARGIN_Y);
        int x = switch (anchor.horizontalAlign()) {
            case -1 -> baseX;
            case 1 -> baseX - w;
            default -> baseX - w / 2;
        };
        int y = anchor.isTop() || anchor.isMiddle() ? baseY : baseY - h;
        out.put(elKey, new Box(clampPos(x + (int) Math.round(offX * K), w, screenW),
                clampPos(y + (int) Math.round(offY * K), h, screenH), w, h));
    }

    /** 组件未缩放尺寸（渲染器与编辑器统一使用，保证测量一致） */
    public static int[] measure(HudLayoutConfig.ComponentLayout c) {
        if (c.modeParsed() == HudLayoutConfig.ComponentMode.ICON) {
            return new int[]{54, 9}; // 6 个 9px 图标位（渲染时按实际数量收缩）
        }
        // hotbar 组件:盒体 = 2px 边距 + 槽格累计宽（隐藏槽保留占位,与渲染排布一致）× 可见槽最高。
        // 类型判定不能只看 c.type——旧配置该字段可能为空串（typeOf 靠键名回退,此处无键名,
        // 用 hotbarSlots 存在与否兜底,实测空 type 曾使盒体退化为 measureBar 细条）
        if (HudLayoutConfig.HOTBAR.equals(c.type) || (c.hotbarSlots != null && !c.hotbarSlots.isEmpty())) {
            int totalW = 0, maxH = 0;
            for (int i = 0; i < 9; i++) {
                var s = c.hotbarSlot(i);
                totalW += Math.max(8, Math.min(64, s.slotW));
                if (!s.hidden) maxH = Math.max(maxH, Math.max(8, Math.min(64, s.slotH)));
            }
            return new int[]{Math.max(2 + totalW, 22), Math.max(2 + maxH, 22)};
        }
        return measureBar(c);
    }

    /** 条形度量（忽略 ICON 档）：只有卡片+文本形态的组件用——compat 行 / 饱和度条在渲染端
     *  没有图标形态,ICON 档下若按图标尺寸度量,编辑器选择框会比实际绘制的内容小一圈 */
    public static int[] measureBar(HudLayoutConfig.ComponentLayout c) {
        // 卡片高度 = 可配置条高（默认 9）+ 2px 度量余量（旧默认 7+4=11 保持不变）
        int barH = Math.max(5, Math.min(16, c.barHeight > 0 ? c.barHeight : 9));
        return new int[]{c.barWidth, barH + 2};
    }
}
