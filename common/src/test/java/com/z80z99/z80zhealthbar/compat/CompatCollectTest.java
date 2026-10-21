package com.z80z99.z80zhealthbar.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 兼容状态收集的共享状态回归测试。
 *
 * <p>背景（1.0.136~1.0.140 线上缺陷）：{@code collect()} 返回 50ms 帧缓存实例，
 * 单行 compat 组件渲染时就地 {@code removeIf} 过滤 → 缓存被清空 → 同窗口内后续组件
 * 拿到空表 → 口渴条以 50ms 周期高频闪烁。此处锁定"过滤不得修改输入"的不变量。
 */
class CompatCollectTest {

    private static CompatAdapters.Stat stat(String key) {
        return new CompatAdapters.Stat(key, 17f, 20f, 0xFF4488FF,
                "z80zhealthbar.stat." + key, null);
    }

    private static List<CompatAdapters.Stat> sample() {
        List<CompatAdapters.Stat> list = new ArrayList<>();
        list.add(stat("thirst.thirst"));
        list.add(stat("appleskin.saturation"));
        list.add(stat("appleskin.exhaustion"));
        list.add(stat("parcool.stamina"));
        return list;
    }

    @Test
    void filterByAdapterReturnsOnlyOwnRows() {
        var filtered = CompatAdapters.filterByAdapter(sample(), "thirst");
        assertEquals(1, filtered.size());
        assertEquals("thirst.thirst", filtered.get(0).key());

        var appleskin = CompatAdapters.filterByAdapter(sample(), "appleskin");
        assertEquals(2, appleskin.size());
    }

    @Test
    void filterByAdapterDoesNotMutateInput() {
        List<CompatAdapters.Stat> shared = sample(); // 模拟 collect() 的缓存实例
        CompatAdapters.filterByAdapter(shared, "thirst");
        CompatAdapters.filterByAdapter(shared, "appleskin");
        CompatAdapters.filterByAdapter(shared, "parcool");

        assertEquals(4, shared.size(), "shared cache must stay intact after filtering");
    }

    @Test
    void everyAdapterRowIsReachableInSameWindow() {
        List<CompatAdapters.Stat> shared = sample();
        // 模拟同一帧内四个单行组件依次渲染（顺序与预设一致）
        for (String adapter : new String[]{"thirst", "appleskin", "appleskin", "parcool"}) {
            var rows = CompatAdapters.filterByAdapter(shared, adapter);
            assertFalse(rows.isEmpty(), adapter + " must still see its rows in the same window");
        }
        assertEquals(4, shared.size());
    }

    @Test
    void filterByAdapterHandlesEmptyAndNull() {
        assertTrue(CompatAdapters.filterByAdapter(List.of(), "thirst").isEmpty());
        assertTrue(CompatAdapters.filterByAdapter(null, "thirst").isEmpty());
        assertTrue(CompatAdapters.filterByAdapter(sample(), "").isEmpty());
        assertTrue(CompatAdapters.filterByAdapter(sample(), null).isEmpty());
    }

    @Test
    void prefixMatchIsExactNotSuffix() {
        // 审计 #20:自定义源 id 撞名（custom_thirst.thirst）不得混进内建 compat_thirst 行
        List<CompatAdapters.Stat> list = new ArrayList<>();
        list.add(stat("custom_thirst.thirst"));
        list.add(stat("thirst.thirst"));
        var filtered = CompatAdapters.filterByAdapter(list, "thirst");
        assertEquals(1, filtered.size());
        assertEquals("thirst.thirst", filtered.get(0).key());
    }
}
