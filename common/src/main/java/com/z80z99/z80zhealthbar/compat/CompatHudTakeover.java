package com.z80z99.z80zhealthbar.compat;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import com.z80z99.z80zhealthbar.overlay.HudRenderer;
import com.z80z99.z80zhealthbar.overlay.HudStyle;

import java.util.HashSet;
import java.util.Set;

/**
 * 兼容 MOD 原版 HUD 接管（2026-10-08）。
 *
 * <p>用户需求："检测到有这个模组，则自动创建一个它的数据组件顶掉它原来的 HUD"。
 * 本模组此前**从不屏蔽**第三方 HUD（只追加自己的一份）——本类提供**可选**接管：
 * <ul>
 *   <li>设置页兼容节每项新增"接管原 HUD"开关（默认关）；</li>
 *   <li>开启后由平台层（Forge {@code RenderGuiOverlayEvent.Pre}）取消对方 overlay 的渲染
 *       （ThirstWasTaken={@code thirst:thirst_level}、ParCool={@code parcool:hud.stamina.host}）;</li>
 *   <li>自定义 HUD 模式下自动创建对应数据组件（compat_thirst/compat_stamina）；
 *       长条模式由兼容状态条组自动显示，无需组件。</li>
 * </ul>
 *
 * <p><b>安全网</b>：仅当"我方确实会绘制该数据"时才取消对方渲染——
 * （开关开 + 兼容 hook 开 + 数据通道兼容 + HUD 风格会显示该行）。
 * 任何一环不满足则不接管，绝不出现"对方被隐藏而自己也'没画"的空窗。
 */
public final class CompatHudTakeover {

    private CompatHudTakeover() {}

    /** 已处理过"自动创建组件"的适配器（会话内一次性;用户之后手动删除不再复活） */
    private static final Set<String> ENSURED = new HashSet<>();
    /** 上次 tick 的接管开关状态（开关沿变化时重置 ENSURED——重新打开触发一次创建） */
    private static final java.util.Map<String, Boolean> LAST_ON = new java.util.HashMap<>();
    /** "已取消过"每适配器一次性的调试日志标记 */
    private static final Set<String> CANCEL_LOGGED = new HashSet<>();

    /** overlay id → 适配器 id 映射（Forge 端 byId 使用） */
    public static String adapterForOverlay(String namespace, String path) {
        if ("thirst".equals(namespace) && "thirst_level".equals(path)) return "thirst";
        if ("parcool".equals(namespace) && "hud.stamina.host".equals(path)) return "parcool";
        return null;
    }

    /** 该适配器的"接管原 HUD"开关是否打开（含兼容 hook 总开关） */
    public static boolean takeoverEnabled(String adapterId) {
        var compat = ConfigManager.getConfig().compat;
        return switch (adapterId) {
            case "thirst" -> compat.hookThirstWasTaken && compat.takeoverThirst;
            case "parcool" -> compat.hookParcool && compat.takeoverParcool;
            default -> false;
        };
    }

    /**
     * 是否应取消指定 overlay 的渲染（Forge 端每帧查询）。
     * 除开关外还需满足：数据通道兼容 + 我方当前渲染路径确实会显示该数据。
     */
    public static boolean shouldCancelOverlay(String namespace, String path) {
        String adapterId = adapterForOverlay(namespace, path);
        if (adapterId == null || !takeoverEnabled(adapterId)) return false;
        CompatibilityAdapter adapter = CompatAdapters.byId(adapterId);
        if (adapter == null || !adapter.isCompatible()) return false;
        return rendersStat(adapterId);
    }

    /** 我方当前 HUD 配置是否会绘制该适配器的数据 */
    public static boolean rendersStat(String adapterId) {
        var cfg = ConfigManager.getConfig();
        if (!cfg.overlay.enableOverlay) return false;
        HudStyle style = HudRenderer.hudStyleParsed();
        if (style == HudStyle.CUSTOM) {
            // 自定义模式:需对应组件存在且未关闭（由 tickEnsure 自动创建/维护）
            String key = componentKey(adapterId);
            var c = cfg.hudLayout.peek(key);
            return c != null && c.modeParsed() != HudLayoutConfig.ComponentMode.OFF;
        }
        if (style == HudStyle.ASTEORBAR) {
            // 长条模式:兼容状态条组渲染全部可用数据;布局 0 = 本模式绘制关闭
            return cfg.overlay.overlayLayoutStyle != 0;
        }
        return false; // VANILLA 模式不绘制自定义内容,不能接管
    }

    /** 适配器的自定义组件键 */
    public static String componentKey(String adapterId) {
        return switch (adapterId) {
            case "thirst" -> "compat_thirst";
            case "parcool" -> "compat_stamina";
            default -> "compat_" + adapterId;
        };
    }

    /**
     * 每客户端 tick 调用（轻量）：接管开关打开且为自定义模式时，
     * 确保对应数据组件存在（不存在则按长条兼容列的默认参数创建并持久化）。
     * 一次性语义：创建/确认后本次会话不再检查——用户之后手动删除组件不会被复活。
     */
    public static void tickEnsure() {
        ensureOne("thirst");
        ensureOne("parcool");
    }

    private static void ensureOne(String adapterId) {
        boolean on = takeoverEnabled(adapterId);
        // 开关沿变化 → 重新允许一次创建（关掉再打开 = 明确意图）
        if (!Boolean.valueOf(on).equals(LAST_ON.get(adapterId))) {
            LAST_ON.put(adapterId, on);
            ENSURED.remove(adapterId);
        }
        if (!on || ENSURED.contains(adapterId)) return;
        HudStyle style = HudRenderer.hudStyleParsed();
        if (style != HudStyle.CUSTOM) return; // 长条/VANILLA 无需组件（VANILLA 本就不接管）
        CompatibilityAdapter adapter = CompatAdapters.byId(adapterId);
        if (adapter == null || !adapter.isAvailable()) return; // MOD 未装:等待（不标记,装入后下个 tick 处理）
        var cfg = ConfigManager.getConfig();
        String key = componentKey(adapterId);
        if (cfg.hudLayout.peek(key) == null) {
            var c = new HudLayoutConfig.ComponentLayout();
            c.type = key;
            c.anchor = "BOTTOM_RIGHT"; // 与 MODERN 预设的右列兼容行一致
            c.barWidth = 81;
            c.stack = true;
            cfg.hudLayout.components.put(key, c);
            ConfigManager.saveConfig();
            Z80ZHealthBar.LOGGER.info("[Compat] auto-created {} component (HUD takeover enabled)", key);
        }
        ENSURED.add(adapterId);
    }

    /** "本帧取消了该 overlay"的一次性调试记录（平台层调用;诊断用） */
    public static void logCancelOnce(String adapterId) {
        if (CANCEL_LOGGED.add(adapterId)) {
            Z80ZHealthBar.LOGGER.info("[Compat] taking over {} HUD (original overlay cancelled)", adapterId);
        }
    }

    /** 配置热重载时调用：允许重新检查一次（防止重载前已 ensured 导致新配置下漏创建） */
    public static void onConfigReload() {
        ENSURED.clear();
        LAST_ON.clear();
    }
}
