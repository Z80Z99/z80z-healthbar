package com.z80z99.z80zhealthbar.compat;

import com.z80z99.z80zhealthbar.platform.PlatformService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 内建兼容适配器注册表（优先级：AppleSkin > Thirst Was Taken > ParCool!，任务书 7）。
 * 所有适配器经此注册；HUD 侧（ASTEORBAR 兼容条 + CUSTOM 兼容组件组）共用本数据源。
 */
public final class CompatAdapters {

    /** 一条可展示状态：值与最大值（max=null 表示比例未知，只能显示文本）。
     *  displayName 非空时优先于 langKey（自定义数据源无静态词条） */
    public record Stat(String key, float value, Float max, int color, String langKey, String displayName) {
        /** 兼容旧构造（内建适配器：走 langKey） */
        public Stat(String key, float value, Float max, int color, String langKey) {
            this(key, value, max, color, langKey, null);
        }
        /** 展示名（displayName 优先） */
        public String label() {
            return displayName != null && !displayName.isBlank()
                    ? displayName
                    : net.minecraft.network.chat.Component.translatable(langKey).getString();
        }
    }

    private static final List<CompatibilityAdapter> ADAPTERS = new ArrayList<>();

    static {
        ADAPTERS.add(new AppleSkinAdapter());
        ADAPTERS.add(new ThirstWasTakenAdapter());
        ADAPTERS.add(new ParCoolAdapter());
    }

    private CompatAdapters() {}

    /** 内建适配器 + 配置启用的自定义数据源（每次调用重建轻量包装,开销可忽略） */
    public static List<CompatibilityAdapter> all() {
        List<CompatibilityAdapter> out = new ArrayList<>(ADAPTERS);
        var cs = com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().customSources;
        if (cs != null && cs.customSources != null) {
            for (var s : cs.customSources) {
                if (s != null && s.enabled && s.paramsComplete()) out.add(new CustomSourceAdapter(s));
            }
        }
        return out;
    }

    public static CompatibilityAdapter byId(String id) {
        for (CompatibilityAdapter a : ADAPTERS) {
            if (a.id().equals(id)) return a;
        }
        // 自定义源:"custom_<id>"
        if (id != null && id.startsWith("custom_")) {
            var cs = com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().customSources;
            if (cs != null && cs.customSources != null) {
                String raw = id.substring("custom_".length());
                for (var s : cs.customSources) {
                    if (s != null && raw.equals(s.id)) return new CustomSourceAdapter(s);
                }
            }
        }
        return null;
    }

    /** 收集当前玩家全部可用状态（目标 MOD 未装/数据不可得/hook 关闭时自动跳过） */
    /** collect() 50ms 帧缓存(按 config 引用代次失效)——renderCompat 对每个 compat 单行组件
     *  每帧各调一次全量 collect 再丢弃 N-1 行(默认预设 4 次/帧,审计 P1)。 */
    private static List<Stat> collectCache;
    private static long collectCacheAt;
    private static Object collectCacheCfg;

    public static List<Stat> collect(Player player) {
        Object cfg = com.z80z99.z80zhealthbar.config.ConfigManager.getConfig();
        long now = System.currentTimeMillis();
        if (collectCache != null && cfg == collectCacheCfg && now - collectCacheAt < 50L) {
            return collectCache;
        }
        List<Stat> out = new ArrayList<>(4);
        for (CompatibilityAdapter adapter : all()) {
            if (!adapter.isAvailable()) continue;
            // 自定义源自带 enabled 判定（不要求 CompatConfig 里有 hook 字段）
            boolean custom = adapter instanceof CustomSourceAdapter;
            if (!custom
                    && !com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().compat.isHookEnabled(adapter.id())) {
                continue;
            }
            for (String key : adapter.providedStats()) {
                Float v = adapter.readStat(key);
                if (v == null) continue;
                Float max = maxOf(adapter, key, player);
                String dn = custom ? ((CustomSourceAdapter) adapter).source().name() : null;
                out.add(new Stat(adapter.id() + "." + key, v, max,
                        colorOf(adapter, key), "z80zhealthbar.stat." + adapter.id() + "." + key, dn));
            }
        }
        collectCache = out;
        collectCacheAt = now;
        collectCacheCfg = cfg;
        return out;
    }

    /**
     * 模板变量用：读取单个兼容状态值。未安装/未启用 hook/通道不兼容 → null（调用方渲染 "—"）。
     * 单次 readStat 结果同时用作兼容判定——此前 isCompatible 内部再读一次 = 双份反射(审计 P2)。
     */
    public static Float readValue(String adapterId, String statKey) {
        CompatibilityAdapter a = byId(adapterId);
        if (a == null || !a.isAvailable()) return null;
        boolean custom = a instanceof CustomSourceAdapter;
        if (!custom
                && !com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().compat.isHookEnabled(adapterId)) {
            return null;
        }
        Float v = a.readStat(statKey);
        if (v == null && !custom && !a.isCompatible()) return null; // 内建:通道不兼容
        return v;
    }

    /** 模板变量用：读取单个兼容状态的上限（未安装/未启用 → null） */
    public static Float readMax(String adapterId, String statKey) {
        CompatibilityAdapter a = byId(adapterId);
        if (a == null || !a.isAvailable()) return null;
        boolean custom = a instanceof CustomSourceAdapter;
        if (!custom
                && !com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().compat.isHookEnabled(adapterId)) {
            return null;
        }
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        return player == null ? null : maxOf(a, statKey, player);
    }

    private static Float maxOf(CompatibilityAdapter adapter, String key, Player player) {
        if (adapter instanceof CustomSourceAdapter c) return c.resolveMax(player);
        if (adapter instanceof ThirstWasTakenAdapter) return 20f;
        if (adapter instanceof ParCoolAdapter a) return a.readMax(player);
        if (adapter instanceof AppleSkinAdapter) return switch (key) {
            case "exhaustion" -> 4f;
            case "saturation" -> 20f;
            default -> null;
        };
        return null;
    }

    private static int colorOf(CompatibilityAdapter adapter, String key) {
        if (adapter instanceof CustomSourceAdapter c) {
            String col = c.source().color;
            if (col != null && !col.isBlank()) {
                Integer v = com.z80z99.z80zhealthbar.util.ColorHelper.parseColorSafe(col);
                if (v != null) return v;
            }
            return 0xFFAAAAAA;
        }
        return colorOf(adapter.id(), key);
    }

    private static int colorOf(String modId, String key) {
        return switch (modId) {
            case "appleskin" -> key.equals("exhaustion") ? 0xFFFFAA00 : 0xFFFFD070;
            // 润泽度用金色（与饱和度同语义:喝水后的缓冲层）
            case "thirst" -> key.equals("quenched") ? 0xFFFFC94D : 0xFF4488FF;
            case "parcool" -> 0xFF66CC66;
            default -> 0xFFAAAAAA;
        };
    }

    // ================= AppleSkin =================

    /** 饱和度/消耗值读取原版 FoodData 字段（本 MOD 服务端同步后为真值）,但整行仍随
     *  AppleSkin 的安装/hook 开关显隐——不装 AppleSkin 时用 saturation_bar / saturation_text
     *  组件显示同一数据（无模组依赖） */
    public static final class AppleSkinAdapter implements CompatibilityAdapter {
        @Override public String id() { return "appleskin"; }
        @Override public boolean isAvailable() { return PlatformService.get().isModLoaded("appleskin"); }
        @Override public List<String> providedStats() { return List.of("exhaustion", "saturation"); }
        @Override public boolean needsServer() { return true; }

        @Override
        public Float readStat(String key) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            var player = mc.player;
            if (player == null) return null;
            return switch (key) {
                case "exhaustion" -> player.getFoodData().getExhaustionLevel();
                case "saturation" -> player.getFoodData().getSaturationLevel();
                default -> null;
            };
        }

        // 反射元数据只解析一次（结果成败都缓存）：未装 AppleSkin 时零反射热点，
        // 装了但 API 变更时永久降级为不显示，绝不每帧 Class.forName 抛异常
        private static volatile boolean foodHelperResolved;
        private static Method canConsumeMethod;
        private static Method valuesMethod;
        private static java.lang.reflect.Field hungerField;
        private static java.lang.reflect.Field saturationField;

        /** 手持食物的修改后食物值（经 AppleSkin FoodHelper；AppleSkin 未装返回 null） */
        public static float[] heldFoodValues(Player player) {
            if (!PlatformService.get().isModLoaded("appleskin")) return null;
            if (!foodHelperResolved) {
                try {
                    Class<?> helper = Class.forName("squeek.appleskin.api.food.FoodHelper");
                    canConsumeMethod = helper.getMethod("canConsume", ItemStack.class, Player.class);
                    valuesMethod = helper.getMethod("getModifiedFoodValues", ItemStack.class, Player.class);
                } catch (Exception e) {
                    canConsumeMethod = null;
                }
                foodHelperResolved = true;
            }
            if (canConsumeMethod == null) return null;
            try {
                ItemStack held = player.getMainHandItem();
                if (!(Boolean) canConsumeMethod.invoke(null, held, player)) return null;
                Object fv = valuesMethod.invoke(null, held, player);
                if (hungerField == null) {
                    hungerField = fv.getClass().getField("hunger");
                    saturationField = fv.getClass().getField("saturation");
                }
                int hunger = hungerField.getInt(fv);
                float sat = saturationField.getFloat(fv);
                return new float[]{hunger, sat};
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ================= Thirst Was Taken =================

    static final class ThirstWasTakenAdapter implements CompatibilityAdapter {
        /** 1.4.x 玩家口渴数据通道：Capability token 持有类 / 字段 / 数据接口（getThirst → 0..20） */
        private static final String CAP_TOKEN_HOLDER = "dev.ghen.thirst.foundation.common.capability.ModCapabilities";
        private static final String CAP_TOKEN_FIELD = "PLAYER_THIRST";
        private static final String CAP_INTERFACE = "dev.ghen.thirst.foundation.common.capability.IThirst";
        private static final String[] CAP_METHODS = {"getThirst"};
        /** 润泽度（Quenched,0..20）= TWT 的"饱和度"：喝水先补它,满了再转口渴值 */
        private static final String[] CAP_METHODS_QUENCHED = {"getQuenched"};
        /** 旧版静态 API（ThirstHelper 在 1.4.x 只有物品侧查询,留作兜底） */
        private static final String[] CLASSES = {
                "dev.ghen.thirst.api.thirst.ThirstHelper",
                "dev.ghen.thirst.api.ThirstHelper",
        };
        private static final String[] METHODS = {"getThirst", "getThirstLevel", "getThirstValue"};

        @Override public String id() { return "thirst"; }
        @Override public boolean isAvailable() {
            return PlatformService.get().isModLoaded("thirst")
                    || PlatformService.get().isModLoaded("thirstwasaken");
        }
        private static boolean capabilityClassesPresent() {
            try {
                Class.forName(CAP_TOKEN_HOLDER);
                Class.forName(CAP_INTERFACE);
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        @Override public boolean isCompatible() {
            // 优先玩家数据通道（Capability）;ThirstHelper 类仅证明 mod 存在（1.4.x 它只有物品侧 API）
            if (capabilityClassesPresent()) return true;
            for (String c : CLASSES) {
                try {
                    Class.forName(c);
                    return true;
                } catch (ClassNotFoundException ignored) {
                }
            }
            return false;
        }
        @Override public List<String> providedStats() { return List.of("thirst"); }

        @Override
        public Float readStat(String key) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return null;
            if (key.equals("quenched")) {
                // 润泽度：仅 capability 通道有（1.4.x 旧静态 API 无此方法）
                return com.z80z99.z80zhealthbar.overlay.parts.compat.ReflectionHelperCompat
                        .capabilityNumber(CAP_TOKEN_HOLDER, CAP_TOKEN_FIELD, CAP_INTERFACE,
                                CAP_METHODS_QUENCHED, player);
            }
            if (!key.equals("thirst")) return null;
            Float v = com.z80z99.z80zhealthbar.overlay.parts.compat.ReflectionHelperCompat
                    .capabilityNumber(CAP_TOKEN_HOLDER, CAP_TOKEN_FIELD, CAP_INTERFACE, CAP_METHODS, player);
            if (v != null) return v;
            return com.z80z99.z80zhealthbar.overlay.parts.compat.ReflectionHelperCompat
                    .staticNumber(CLASSES, METHODS, player);
        }
    }

    // ================= ParCool! =================

    static final class ParCoolAdapter implements CompatibilityAdapter {
        private static final String CAPABILITY = "com.alrex.parcool.common.capability.Stamina";

        @Override public String id() { return "parcool"; }
        @Override public boolean isAvailable() { return PlatformService.get().isModLoaded("parcool"); }
        @Override public List<String> providedStats() { return List.of("stamina"); }

        @Override
        public Float readStat(String key) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null || !key.equals("stamina")) return null;
            return com.z80z99.z80zhealthbar.overlay.parts.compat.ReflectionHelperCompat
                    .instanceNumber(CAPABILITY, "get", new String[]{"get", "getStamina"}, player);
        }

        Float readMax(Player player) {
            Float max = com.z80z99.z80zhealthbar.overlay.parts.compat.ReflectionHelperCompat
                    .instanceNumber(CAPABILITY, "get", new String[]{"getMaxStamina"}, player);
            return max != null && max > 0 ? max : null; // 未知上限 → null（文本显示，不画比例条）
        }
    }
}
