package com.z80z99.z80zhealthbar.config;

import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 样式剪贴板编解码测试（1.0.148 分享功能）。
 */
class StyleCodecTest {

    private HudLayoutConfig.UserStyle sample() {
        var st = new HudLayoutConfig.UserStyle();
        st.name = "我的布局";
        var cl = new HudLayoutConfig.ComponentLayout();
        cl.offsetX = 123;
        cl.barWidth = 150;
        st.components.put("health", cl);
        return st;
    }

    @Test
    void exportParseRoundTrip() {
        String json = StyleCodec.export(sample());
        var back = StyleCodec.parse(json);
        assertNotNull(back);
        assertEquals("我的布局", back.name);
        assertEquals(123, back.components.get("health").offsetX);
        assertEquals(150, back.components.get("health").barWidth);
    }

    @Test
    void parseRejectsGarbage() {
        assertNull(StyleCodec.parse(null));
        assertNull(StyleCodec.parse(""));
        assertNull(StyleCodec.parse("not json"));
        // Gson 宽松：{"name":123} 会被强转为 name="123"（非致命,sanitize 兜底）——不拒收
        var lenient = StyleCodec.parse("{\"name\":123}");
        assertNotNull(lenient);
        assertEquals("123", lenient.name);
    }

    @Test
    void sanitizeClampsAndUniquifies() {
        var st = sample();
        var cl = st.components.get("health");
        cl.scale = 99.0;                 // 越界 → 钳到 2.0
        cl.offsetX = 100000;             // 越界 → 钳到 1000
        st.scaleMode = 7;                // → 1
        st.referenceWidth = -5;          // → 默认 960
        st.name = "  ";                  // 空白 → fallback
        assertTrue(StyleCodec.sanitize(st, Set.of(), n -> "样式" + n));
        assertEquals(2.0, cl.scale);
        assertEquals(1000, cl.offsetX);
        assertEquals(1, st.scaleMode);
        assertEquals(960, st.referenceWidth);
        assertEquals("样式1", st.name);

        // 撞名 → 追加序号
        st.name = "样式1";
        assertTrue(StyleCodec.sanitize(st, Set.of("样式1"), n -> "样式" + n));
        assertEquals("样式1 2", st.name);
    }

    @Test
    void sanitizeRejectsEmptyComponents() {
        var st = new HudLayoutConfig.UserStyle();
        assertFalse(StyleCodec.sanitize(st, Set.of(), n -> "x"), "null components rejected");
        st.components.put("", new HudLayoutConfig.ComponentLayout());
        assertFalse(StyleCodec.sanitize(st, Set.of(), n -> "x"), "blank keys removed → empty rejected");
        // 子元素键清除后非空 → 接受
        st.components.put("health", new HudLayoutConfig.ComponentLayout());
        assertTrue(StyleCodec.sanitize(st, Set.of(), n -> "x"));
        assertFalse(st.components.containsKey(""));
    }
}
