package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.Z80ZHealthBarConfig;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import com.z80z99.z80zhealthbar.overlay.StyleAutoSwitcher.WindowState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户样式 + 窗口状态自动切换的纯函数/配置层测试（1.0.147）。
 * <p>注意:StyleAutoSwitcher 的 tick/detect 依赖 MC 运行时,不在单测范围;
 * 此处只测 stateOf/boundStyleName/findStyle/save-load 深拷与 validator 清理。
 */
class UserStyleTest {

    @Test
    void stateOfGivesFullscreenPrecedence() {
        assertEquals(WindowState.FULLSCREEN, StyleAutoSwitcher.stateOf(true, true));
        assertEquals(WindowState.FULLSCREEN, StyleAutoSwitcher.stateOf(true, false));
        assertEquals(WindowState.MAXIMIZED, StyleAutoSwitcher.stateOf(false, true));
        assertEquals(WindowState.WINDOWED, StyleAutoSwitcher.stateOf(false, false));
    }

    @Test
    void boundStyleNameMapsThreeStates() {
        var ly = new HudLayoutConfig();
        ly.styleFullscreen = "F";
        ly.styleMaximized = "";
        ly.styleWindowed = "W";
        assertEquals("F", StyleAutoSwitcher.boundStyleName(ly, WindowState.FULLSCREEN));
        assertEquals("", StyleAutoSwitcher.boundStyleName(ly, WindowState.MAXIMIZED));
        assertEquals("W", StyleAutoSwitcher.boundStyleName(ly, WindowState.WINDOWED));
        assertEquals("", StyleAutoSwitcher.boundStyleName(null, WindowState.FULLSCREEN));
    }

    @Test
    void saveAndLoadAreDeepCopies() {
        var ly = new HudLayoutConfig();
        var style = new HudLayoutConfig.UserStyle();
        style.name = "S";
        StyleAutoSwitcher.saveLiveToStyle(ly, style);
        assertEquals(ly.components.size(), style.components.size());

        // 存样式后改 live → 样式不受影响
        var key = ly.components.keySet().iterator().next();
        ly.components.get(key).offsetX = 555;
        assertNotEquals(555, style.components.get(key).offsetX, "style is isolated from live edits");

        // 载入 → live 恢复为快照值
        ly.components.get(key).barWidth = 300;
        StyleAutoSwitcher.loadStyleToLive(ly, style);
        assertNotEquals(300, ly.components.get(key).barWidth);
        assertEquals(style.scaleMode, ly.scaleMode);
        assertEquals(style.referenceWidth, ly.referenceWidth);

        // 载入后再改 live → 样式仍不受影响
        ly.scaleMode = 1;
        assertEquals(0, style.scaleMode);
    }

    @Test
    void findStyleMatchesExactName() {
        var ly = new HudLayoutConfig();
        var a = new HudLayoutConfig.UserStyle();
        a.name = "样式 1";
        ly.userStyles.add(a);
        assertSame(a, StyleAutoSwitcher.findStyle(ly, "样式 1"));
        assertNull(StyleAutoSwitcher.findStyle(ly, "样式 1 2"), "exact match only");
        assertNull(StyleAutoSwitcher.findStyle(ly, ""));
        assertNull(StyleAutoSwitcher.findStyle(ly, null));
    }

    @Test
    void validatorCleansStyleLibrary() {
        Z80ZHealthBarConfig cfg = ConfigManager.getConfig();
        var ly = cfg.hudLayout;
        ly.userStyles = new java.util.ArrayList<>();
        // 正常样式(带越界值,应被钳制) + 空组件样式 + null 项 + 重名样式
        var good = new HudLayoutConfig.UserStyle();
        good.name = "S1";
        var cl = new HudLayoutConfig.ComponentLayout();
        cl.scale = 99.0;
        good.components.put("health", cl);
        var dup = good.copy();
        dup.components.put("food", new HudLayoutConfig.ComponentLayout());
        var empty = new HudLayoutConfig.UserStyle();
        empty.name = "E";
        ly.userStyles.add(good);
        ly.userStyles.add(dup);
        ly.userStyles.add(empty);
        ly.userStyles.add(null);
        ly.styleFullscreen = "S1";
        ly.styleMaximized = "GONE";
        com.z80z99.z80zhealthbar.config.ConfigValidator.validate(cfg);

        assertEquals(2, ly.userStyles.size(), "empty + null styles removed");
        var s0 = ly.userStyles.get(0);
        var s1 = ly.userStyles.get(1);
        assertEquals("S1", s0.name);
        assertNotEquals("S1", s1.name, "duplicate renamed");
        assertEquals(2.0, s0.components.get("health").scale, "style components clamped");
        assertEquals("S1", ly.styleFullscreen);
        assertEquals("", ly.styleMaximized, "dangling slot cleared");
    }

    @Test
    void validatorTrimsToTwentyStyles() {
        Z80ZHealthBarConfig cfg = ConfigManager.getConfig();
        var ly = cfg.hudLayout;
        ly.userStyles = new java.util.ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            var st = new HudLayoutConfig.UserStyle();
            st.name = "S" + i;
            st.components.put("health", new HudLayoutConfig.ComponentLayout());
            ly.userStyles.add(st);
        }
        com.z80z99.z80zhealthbar.config.ConfigValidator.validate(cfg);
        assertEquals(20, ly.userStyles.size(), "cap at 20, trimmed by index");
        assertEquals("S1", ly.userStyles.get(0).name);
        assertEquals("S20", ly.userStyles.get(19).name);
    }

    @Test
    void copyCarriesStyleLibraryAndSlots() {
        var ly = new HudLayoutConfig();
        var st = new HudLayoutConfig.UserStyle();
        st.name = "A";
        st.components.put("health", new HudLayoutConfig.ComponentLayout());
        ly.userStyles.add(st);
        ly.autoStyleSwitch = true;
        ly.styleWindowed = "A";
        var copy = ly.copy();
        assertEquals(1, copy.userStyles.size());
        assertEquals("A", copy.userStyles.get(0).name);
        assertTrue(copy.autoStyleSwitch);
        assertEquals("A", copy.styleWindowed);
        // 深拷贝隔离
        copy.userStyles.get(0).name = "B";
        assertEquals("A", ly.userStyles.get(0).name);
        ly.userStyles.clear();
        assertEquals(1, copy.userStyles.size());
    }
}
