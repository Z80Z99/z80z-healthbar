package com.z80z99.z80zhealthbar.layout;

import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CUSTOM 布局求解器测试 —— 验证任务书 4.4 的关键不变量：
 * 编辑器与运行时共用 solve()，因此求解器正确 = 布局一致。
 */
class HudLayoutSolverTest {

    private static final int W = 1920 / 3;  // 640（模拟 GUI scale 3）
    private static final int H = 1080 / 3;  // 360

    private Map<String, int[]> sizes(HudLayoutConfig layout) {
        Map<String, int[]> sizes = new LinkedHashMap<>();
        for (String key : layout.components.keySet()) {
            sizes.put(key, HudLayoutSolver.measure(layout.get(key)));
        }
        return sizes;
    }

    @Test
    void bottomCenterAnchorSitsAboveHotbar() {
        HudLayoutConfig layout = new HudLayoutConfig();
        var boxes = HudLayoutSolver.solve(layout, sizes(layout), W, H);
        var health = boxes.get(HudLayoutConfig.HEALTH);
        assertNotNull(health);
        // BOTTOM_CENTER 基线 = H-39，组向上堆叠：底部应在基线附近
        assertTrue(health.y() + health.height() <= H - 39 + 1, "health box should end at hotbar baseline");
        assertTrue(health.y() > H / 2, "health box should be in lower half");
        // 居中
        assertEquals(W / 2 - health.width() / 2, health.x());
    }

    @Test
    void sameAnchorStacksVertically() {
        // 显式构造同锚点两组件（不依赖任何预设布局）：food 先、air 后 → air 在 food 下方
        HudLayoutConfig layout = new HudLayoutConfig();
        layout.components.clear();
        var foodLayout = new HudLayoutConfig.ComponentLayout();
        foodLayout.anchor = "BOTTOM_RIGHT";
        foodLayout.stack = true;
        var airLayout = new HudLayoutConfig.ComponentLayout();
        airLayout.anchor = "BOTTOM_RIGHT";
        airLayout.stack = true;
        layout.components.put(HudLayoutConfig.FOOD, foodLayout);
        layout.components.put(HudLayoutConfig.AIR, airLayout);
        var boxes = HudLayoutSolver.solve(layout, sizes(layout), W, H);
        var food = boxes.get(HudLayoutConfig.FOOD);
        var air = boxes.get(HudLayoutConfig.AIR);
        assertNotNull(food);
        assertNotNull(air);
        assertTrue(air.y() > food.y(), "later component stacks below earlier in same anchor group");
        assertTrue(air.x() <= W - HudLayoutSolver.MARGIN_X, "right-anchored stays inside screen");
    }

    @Test
    void offComponentsExcluded() {
        HudLayoutConfig layout = new HudLayoutConfig();
        layout.get(HudLayoutConfig.ARMOR).mode = HudLayoutConfig.ComponentMode.OFF.name();
        layout.get(HudLayoutConfig.MOUNT).mode = HudLayoutConfig.ComponentMode.OFF.name();
        var boxes = HudLayoutSolver.solve(layout, sizes(layout), W, H);
        assertFalse(boxes.containsKey(HudLayoutConfig.ARMOR));
        assertFalse(boxes.containsKey(HudLayoutConfig.MOUNT));
        assertTrue(boxes.containsKey(HudLayoutConfig.HEALTH));
    }

    @Test
    void dragOffsetsApplied() {
        HudLayoutConfig layout = new HudLayoutConfig();
        var base = HudLayoutSolver.solve(layout, sizes(layout), W, H).get(HudLayoutConfig.HEALTH);
        layout.get(HudLayoutConfig.HEALTH).offsetX = 17;
        layout.get(HudLayoutConfig.HEALTH).offsetY = -9;
        var moved = HudLayoutSolver.solve(layout, sizes(layout), W, H).get(HudLayoutConfig.HEALTH);
        assertEquals(base.x() + 17, moved.x());
        assertEquals(base.y() - 9, moved.y());
    }

    @Test
    void scaleAffectsBoxSize() {
        HudLayoutConfig layout = new HudLayoutConfig();
        var cl = layout.get(HudLayoutConfig.HEALTH);
        var base = HudLayoutSolver.solve(layout, sizes(layout), W, H).get(HudLayoutConfig.HEALTH);
        cl.scale = 2.0;
        var boxes = HudLayoutSolver.solve(layout, sizes(layout), W, H);
        var scaled = boxes.get(HudLayoutConfig.HEALTH);
        assertEquals(base.width() * 2, scaled.width());
        assertTrue(scaled.height() >= base.height() * 2 - 1);
    }

    @Test
    void topAnchorStacksDownwardFromMargin() {
        HudLayoutConfig layout = new HudLayoutConfig();
        layout.get(HudLayoutConfig.HEALTH).anchor = "TOP_LEFT";
        layout.get(HudLayoutConfig.HEALTH).stack = true;
        layout.get(HudLayoutConfig.ARMOR).anchor = "TOP_LEFT";
        layout.get(HudLayoutConfig.ARMOR).stack = true;
        var boxes = HudLayoutSolver.solve(layout, sizes(layout), W, H);
        var health = boxes.get(HudLayoutConfig.HEALTH);
        var armor = boxes.get(HudLayoutConfig.ARMOR);
        assertEquals(HudLayoutSolver.MARGIN_X, health.x());
        assertEquals(HudLayoutSolver.MARGIN_Y, health.y());
        assertTrue(armor.y() > health.y());
    }

    @Test
    void iconModeMeasuredAsNinePixelRow() {
        var cl = new HudLayoutConfig.ComponentLayout();
        cl.mode = HudLayoutConfig.ComponentMode.ICON.name();
        int[] size = HudLayoutSolver.measure(cl);
        assertEquals(9, size[1]);
        assertTrue(size[0] > 0);
    }

    @Test
    void detachedTextGetsOwnAnchorBox() {
        HudLayoutConfig layout = new HudLayoutConfig();
        var health = layout.get(HudLayoutConfig.HEALTH);
        health.textAnchor = "TOP_RIGHT";
        health.textOffsetX = -5;   // text offset stacks on the detached anchor
        health.textOffsetY = 3;
        Map<String, int[]> sz = sizes(layout);
        sz.put(HudLayoutConfig.HEALTH + ".text", new int[]{40, 8}); // simulate non-empty value text
        var boxes = HudLayoutSolver.solve(layout, sz, W, H);
        var text = boxes.get(HudLayoutConfig.HEALTH + ".text");
        var bar = boxes.get(HudLayoutConfig.HEALTH);
        assertNotNull(text, "detached text box exists when textAnchor set");
        // TOP_RIGHT: right edge at margin, top at margin; offsets stacked
        assertEquals(W - HudLayoutSolver.MARGIN_X - 40 - 5, text.x());
        assertEquals(HudLayoutSolver.MARGIN_Y + 3, text.y());
        assertNotNull(bar, "bar still laid out independently");
    }

    @Test
    void detachedIconFollowsBottomAnchor() {
        HudLayoutConfig layout = new HudLayoutConfig();
        var food = layout.get(HudLayoutConfig.FOOD);
        food.iconAnchor = "BOTTOM_LEFT";
        Map<String, int[]> sz = sizes(layout);
        sz.put(HudLayoutConfig.FOOD + ".icon", new int[]{9, 9});
        var boxes = HudLayoutSolver.solve(layout, sz, W, H);
        var icon = boxes.get(HudLayoutConfig.FOOD + ".icon");
        assertNotNull(icon);
        // BOTTOM_LEFT: bottom-aligned to baseline, left at margin
        assertEquals(HudLayoutSolver.MARGIN_X, icon.x());
        assertEquals(H - HudLayoutSolver.MARGIN_Y - 9, icon.y());
    }

    @Test
    void noDetachedBoxesWhenAnchorsEmpty() {
        HudLayoutConfig layout = new HudLayoutConfig();
        Map<String, int[]> sz = sizes(layout);
        sz.put(HudLayoutConfig.HEALTH + ".text", new int[]{40, 8});
        var boxes = HudLayoutSolver.solve(layout, sz, W, H);
        assertFalse(boxes.containsKey(HudLayoutConfig.HEALTH + ".text"),
                "empty textAnchor = follow bar, no detached box");
    }

    @Test
    void presetsProduceValidOnScreenLayouts() {
        for (String preset : HudLayoutConfig.PRESETS) {
            HudLayoutConfig layout = new HudLayoutConfig();
            layout.applyPreset(preset);
            Map<String, int[]> sz = new LinkedHashMap<>();
            for (String key : layout.components.keySet()) {
                sz.put(key, HudLayoutSolver.measure(layout.get(key)));
            }
            var boxes = HudLayoutSolver.solve(layout, sz, W, H);
            assertFalse(boxes.isEmpty(), preset + " has visible components");
            for (var e : boxes.entrySet()) {
                var b = e.getValue();
                assertTrue(b.x() >= 0 && b.y() >= 0, preset + ": " + e.getKey() + " on screen");
                assertTrue(b.x() + b.width() <= W, preset + ": " + e.getKey() + " fits width");
                assertTrue(b.y() + b.height() <= H, preset + ": " + e.getKey() + " fits height");
            }
        }
    }

    @Test
    void invalidAnchorOrModeFallsBackGracefully() {
        HudLayoutConfig layout = new HudLayoutConfig();
        layout.get(HudLayoutConfig.HEALTH).anchor = "NO_SUCH_ANCHOR";
        layout.get(HudLayoutConfig.HEALTH).mode = "???";
        assertDoesNotThrow(() -> HudLayoutSolver.solve(layout, sizes(layout), W, H));
        var boxes = HudLayoutSolver.solve(layout, sizes(layout), W, H);
        assertTrue(boxes.containsKey(HudLayoutConfig.HEALTH), "fallback anchor keeps component visible");
    }
}
