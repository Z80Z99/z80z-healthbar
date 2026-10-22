package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.level.GameType;

/**
 * 创造/旁观模式下的 HUD 隐藏规则（"类似原版"）。
 *
 * <p>原版只用一个判断控制整块状态 HUD：{@code MultiPlayerGameMode.canHurtPlayer()}（生存/冒险）
 * 决定生命/饥饿/氧气/护甲，{@code hasExperience()}（同样仅生存/冒险）决定经验条；坐骑生命
 * （{@code renderVehicleHealth}）和快捷栏不受其控制。本类保持同一集合：
 * <ul>
 *   <li>隐藏：生命、饥饿、氧气、护甲、经验，以及兼容状态条（口渴/饱和/消耗/体力/自定义数据源）</li>
 *   <li>保留：坐骑生命、快捷栏、坐标/FPS/延迟/游戏时间等信息组件、自由文本</li>
 * </ul>
 * <p>判定逻辑拆成不依赖 MC 的纯函数（{@link #hidesFor}、{@link #isStatusType}）以便单测；
 * 只有 {@link #currentMode()} 读运行时状态。编辑器/设置页预览（{@link HudPreviewState#active}）
 * 永不隐藏，否则无法编辑。
 */
public final class HudVisibility {

    /** 当前游戏模式的简化视图（避免测试依赖 MC 类） */
    public enum Mode { SURVIVAL_LIKE, CREATIVE, SPECTATOR, UNKNOWN }

    private HudVisibility() {}

    /** 配置开关是否打开 */
    public static boolean enabled() {
        return ConfigManager.getConfig().overlay.hideStatusBarsInCreative;
    }

    /** 非生存模式（创造/旁观）是否应隐藏状态条 */
    public static boolean hidesFor(Mode mode) {
        return mode == Mode.CREATIVE || mode == Mode.SPECTATOR;
    }

    /**
     * 该组件类型是否属于"状态条"（会被创造模式隐藏）。
     * 接受带 {@code _text}/{@code _icon} 后缀的原子类型。
     */
    public static boolean isStatusType(String type) {
        if (type == null || type.isBlank()) return false;
        String t = type;
        if (t.endsWith("_text")) t = t.substring(0, t.length() - "_text".length());
        else if (t.endsWith("_icon")) t = t.substring(0, t.length() - "_icon".length());
        return switch (t) {
            case HudLayoutConfig.HEALTH, HudLayoutConfig.FOOD, HudLayoutConfig.AIR,
                    HudLayoutConfig.ARMOR, HudLayoutConfig.EXPERIENCE, "saturation_bar" -> true;
            // 兼容状态条（内建口渴/饱和/消耗/体力 + 自定义数据源 compat_custom_*）+ 旧兼容组
            default -> t.startsWith("compat_") || t.equals(HudLayoutConfig.COMPAT);
        };
    }

    /** 运行时：当前是否应隐藏状态条（开关关、编辑器预览、无玩家时都不隐藏） */
    public static boolean shouldHideStatusBars() {
        if (!enabled() || HudPreviewState.active) return false;
        return hidesFor(currentMode());
    }

    /** 读当前游戏模式；gameMode 尚未建立时回退玩家标记，仍取不到返回 UNKNOWN（不隐藏） */
    public static Mode currentMode() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return Mode.UNKNOWN;
        MultiPlayerGameMode gm = mc.gameMode;
        if (gm != null) {
            GameType t = gm.getPlayerMode();
            if (t != null) return toMode(t);
        }
        if (mc.player != null) {
            if (mc.player.isCreative()) return Mode.CREATIVE;
            if (mc.player.isSpectator()) return Mode.SPECTATOR;
            return Mode.SURVIVAL_LIKE;
        }
        return Mode.UNKNOWN;
    }

    /** MC 游戏类型 → 简化视图 */
    public static Mode toMode(GameType type) {
        if (type == null) return Mode.UNKNOWN;
        if (type == GameType.CREATIVE) return Mode.CREATIVE;
        if (type == GameType.SPECTATOR) return Mode.SPECTATOR;
        if (type == GameType.SURVIVAL || type == GameType.ADVENTURE) return Mode.SURVIVAL_LIKE;
        return Mode.UNKNOWN;
    }
}
