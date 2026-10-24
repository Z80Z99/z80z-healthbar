package com.z80z99.z80zhealthbar.config.configs;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分组辅助纯函数测试（多组支持与整组移动,1.0.145）。
 */
class GroupingTest {

    private static HudLayoutConfig layoutWithGroups() {
        HudLayoutConfig layout = new HudLayoutConfig();
        layout.components.clear();
        for (String[] kg : new String[][]{
                {"health", "A"}, {"food", "A"}, {"armor", "B"},
                {"health#2", ""}, {"mount", null}}) {
            HudLayoutConfig.ComponentLayout c = new HudLayoutConfig.ComponentLayout();
            if (kg[1] != null) c.group = kg[1];
            layout.components.put(kg[0], c);
        }
        return layout;
    }

    @Test
    void groupMemberKeysMatchExactGroupAndSkipSubKeys() {
        var layout = layoutWithGroups();
        var members = HudLayoutConfig.groupMemberKeys(layout, "A");
        assertEquals(List.of("health", "food"), members, "only exact group A members");

        assertEquals(List.of("armor"), HudLayoutConfig.groupMemberKeys(layout, "B"));
        assertTrue(HudLayoutConfig.groupMemberKeys(layout, "").isEmpty(), "blank group = none");
        assertTrue(HudLayoutConfig.groupMemberKeys(layout, null).isEmpty());
        assertTrue(HudLayoutConfig.groupMemberKeys(null, "A").isEmpty());
        // 精确匹配:不存在前缀误收（组 "A" 不收 "AB" 的成员——用额外成员验证）
        layout.components.get("armor").group = "AB";
        assertEquals(List.of("health", "food"), HudLayoutConfig.groupMemberKeys(layout, "A"));
    }

    @Test
    void nextFreeNameIncrementsPastCollisions() {
        assertEquals("组 1", HudLayoutConfig.nextFreeName(Set.of(), n -> "组 " + n));
        assertEquals("组 3",
                HudLayoutConfig.nextFreeName(Set.of("组 1", "组 2"), n -> "组 " + n));
        assertEquals("g1", HudLayoutConfig.nextFreeName(null, n -> "g" + n));
        // 全 1..998 冲突 → 取 999;不抛异常
        Set<String> taken = new java.util.HashSet<>();
        for (int n = 1; n <= 998; n++) taken.add("n" + n);
        assertEquals("n999", HudLayoutConfig.nextFreeName(taken, n -> "n" + n));
    }

    @Test
    void copyPreservesGroupAssignment() {
        // "放弃改动"回滚依赖 copy() 携带 group 字段——此前已含,此处锁定防回归
        HudLayoutConfig src = layoutWithGroups();
        HudLayoutConfig copy = src.copy();
        assertEquals("A", copy.components.get("health").group);
        assertEquals("B", copy.components.get("armor").group);
        assertEquals("", copy.components.get("health#2").group);
        // 深拷贝:改副本不影响原配置
        copy.components.get("health").group = "Z";
        assertEquals("A", src.components.get("health").group);
    }

    @Test
    void groupMoveKeysExcludeIndependentMembers() {
        var layout = layoutWithGroups(); // health/food ∈ A, armor ∈ B
        layout.components.get("food").moveIndependent = true;
        assertEquals(List.of("health"), HudLayoutConfig.groupMoveKeys(layout, "A"),
                "independent member is excluded from group movement");
        assertEquals(List.of("armor"), HudLayoutConfig.groupMoveKeys(layout, "B"));
        // 全部独立 → 空表（拖拽退化为单组件移动）
        layout.components.get("health").moveIndependent = true;
        assertTrue(HudLayoutConfig.groupMoveKeys(layout, "A").isEmpty());
        // 未分组/空组名
        assertTrue(HudLayoutConfig.groupMoveKeys(layout, "").isEmpty());
    }

    @Test
    void copyPreservesMoveIndependentFlag() {
        var src = layoutWithGroups();
        src.components.get("food").moveIndependent = true;
        var copy = src.copy();
        assertTrue(copy.components.get("food").moveIndependent);
        assertFalse(copy.components.get("health").moveIndependent);
        // 深拷贝隔离
        copy.components.get("food").moveIndependent = false;
        assertTrue(src.components.get("food").moveIndependent);
    }

    @Test
    void mapMergeCountingForGroupPicker() {
        // 组选择页的组名→成员数统计口径（与 buildCustomList 折叠行一致）
        Map<String, Integer> groups = new LinkedHashMap<>();
        for (Map.Entry<String, HudLayoutConfig.ComponentLayout> e : layoutWithGroups().components.entrySet()) {
            if (e.getKey().endsWith(".text") || e.getKey().endsWith(".icon")) continue;
            String g = e.getValue() == null || e.getValue().group == null ? "" : e.getValue().group;
            if (!g.isBlank()) groups.merge(g, 1, Integer::sum);
        }
        assertEquals(2, groups.get("A"));
        assertEquals(1, groups.get("B"));
    }
}
