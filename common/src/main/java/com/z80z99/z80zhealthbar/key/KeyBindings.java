package com.z80z99.z80zhealthbar.key;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.gui.HudLayoutScreen;
import com.z80z99.z80zhealthbar.gui.ModSettingsScreen;
import com.z80z99.z80zhealthbar.mobdisplay.EntityHealthStyle;
import com.z80z99.z80zhealthbar.overlay.HudStyle;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 快捷键（任务书 6.1）：六项均可改键。
 * 默认键位避开原 MOD 键位（H=mobhealthbar、J=Mob Plaques、F8/F10=AsteorBar）
 * 与原版常用键；未绑定键可从 控制设置→Z80Z Health Bar 绑定。
 */
public final class KeyBindings {
    public static final String CATEGORY = "z80zhealthbar.key.category";
    public static final List<KeyMapping> ALL = new ArrayList<>();

    /** 显示/隐藏玩家 HUD（默认 K） */
    public static final KeyMapping TOGGLE_PLAYER_HUD = create(
            "z80zhealthbar.key.toggle_overlay", GLFW.GLFW_KEY_K);
    /** 显示/隐藏实体状态栏（默认 M） */
    public static final KeyMapping TOGGLE_ENTITY_BAR = create(
            "z80zhealthbar.key.toggle_mob_bar", GLFW.GLFW_KEY_M);
    /** 打开设置界面（默认未绑定；也可经 ModMenu/暂停菜单进入） */
    public static final KeyMapping OPEN_SETTINGS = create(
            "z80zhealthbar.key.open_settings", InputConstants.UNKNOWN.getValue());
    /** 循环玩家 HUD 样式（默认未绑定） */
    public static final KeyMapping CYCLE_HUD_STYLE = create(
            "z80zhealthbar.key.cycle_hud_style", InputConstants.UNKNOWN.getValue());
    /** 循环实体生命值样式（默认未绑定） */
    public static final KeyMapping CYCLE_ENTITY_STYLE = create(
            "z80zhealthbar.key.cycle_entity_style", InputConstants.UNKNOWN.getValue());
    /** 打开 HUD 布局编辑器（默认未绑定） */
    public static final KeyMapping OPEN_LAYOUT_EDITOR = create(
            "z80zhealthbar.key.open_layout_editor", InputConstants.UNKNOWN.getValue());
    /** 从磁盘重载配置（默认未绑定；配合配置热重载监视器做免重启调试） */
    public static final KeyMapping RELOAD_CONFIG = create(
            "z80zhealthbar.key.reload_config", InputConstants.UNKNOWN.getValue());

    private static KeyMapping create(String name, int key) {
        KeyMapping km = new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY);
        ALL.add(km);
        return km;
    }

    public static void handleKeyPresses() {
        var mc = Minecraft.getInstance();
        var cfg = ConfigManager.getConfig();
        if (mc.player == null) return;

        while (TOGGLE_PLAYER_HUD.consumeClick()) {
            cfg.overlay.enableOverlay = !cfg.overlay.enableOverlay;
            feedback(mc, "z80zhealthbar.msg.hud_toggle",
                    onOff(cfg.overlay.enableOverlay));
        }
        while (TOGGLE_ENTITY_BAR.consumeClick()) {
            cfg.barStyle.enableHealthBar = !cfg.barStyle.enableHealthBar;
            feedback(mc, "z80zhealthbar.msg.entity_toggle",
                    onOff(cfg.barStyle.enableHealthBar));
        }
        while (OPEN_SETTINGS.consumeClick()) {
            mc.setScreen(ModSettingsScreen.create(mc.screen));
        }
        while (CYCLE_HUD_STYLE.consumeClick()) {
            var style = nextEnum(HudStyle.values(),
                    HudStyle.valueOf(hudStyleOr(cfg.overlay.hudStyle)));
            cfg.overlay.hudStyle = style.name();
            ConfigManager.saveConfig();
            feedback(mc, "z80zhealthbar.msg.hud_style",
                    Component.translatable("z80zhealthbar.style.hudstyle." + style.name().toLowerCase()));
        }
        while (CYCLE_ENTITY_STYLE.consumeClick()) {
            EntityHealthStyle next = nextEnum(EntityHealthStyle.values(), cfg.entityStyleParsed());
            cfg.setEntityStyle(next);
            ConfigManager.saveConfig();
            feedback(mc, "z80zhealthbar.msg.entity_style",
                    Component.translatable("z80zhealthbar.style.entityhealthstyle." + next.name().toLowerCase()));
        }
        while (OPEN_LAYOUT_EDITOR.consumeClick()) {
            cfg.overlay.hudStyle = HudStyle.CUSTOM.name();
            ConfigManager.saveConfig();
            mc.setScreen(new HudLayoutScreen(mc.screen));
        }
        while (RELOAD_CONFIG.consumeClick()) {
            ConfigManager.loadConfig();
            feedback(mc, "z80zhealthbar.msg.config_reloaded",
                    Component.translatable("z80zhealthbar.msg.config_reloaded"));        }
    }

    private static String hudStyleOr(String s) {
        try {
            return com.z80z99.z80zhealthbar.overlay.HudStyle.valueOf(s).name();
        } catch (Exception e) {
            return "ASTEORBAR";
        }
    }

    private static <E extends Enum<E>> E nextEnum(E[] values, E current) {
        return values[(current.ordinal() + 1) % values.length];
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "z80zhealthbar.msg.on" : "z80zhealthbar.msg.off");
    }

    private static void feedback(Minecraft mc, String key, Component arg) {
        mc.player.displayClientMessage(Component.translatable(key, arg), true);
    }
}
