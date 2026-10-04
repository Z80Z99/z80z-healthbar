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

    /** 一条可展示状态：值与最大值（max=null 表示比例未知，只能显示文本） */
    public record Stat(String key, float value, Float max, int color, String langKey) {}

    private static final List<CompatibilityAdapter> ADAPTERS = new ArrayList<>();

    static {
        ADAPTERS.add(new AppleSkinAdapter());
        ADAPTERS.add(new ThirstWasTakenAdapter());
        ADAPTERS.add(new ParCoolAdapter());
    }

    private CompatAdapters() {}

    public static List<CompatibilityAdapter> all() {
        return List.copyOf(ADAPTERS);
    }

    public static CompatibilityAdapter byId(String id) {
        for (CompatibilityAdapter a : ADAPTERS) {
            if (a.id().equals(id)) return a;
        }
        return null;
    }

    /** 收集当前玩家全部可用状态（目标 MOD 未装/数据不可得/hook 关闭时自动跳过） */
    public static List<Stat> collect(Player player) {
        List<Stat> out = new ArrayList<>(4);
        for (CompatibilityAdapter adapter : ADAPTERS) {
            if (!adapter.isAvailable()) continue;
            if (!com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().compat.isHookEnabled(adapter.id())) {
                continue;
            }
            for (String key : adapter.providedStats()) {
                Float v = adapter.readStat(key);
                if (v == null) continue;
                Float max = maxOf(adapter, key, player);
                out.add(new Stat(adapter.id() + "." + key, v, max,
                        colorOf(adapter.id(), key), "z80zhealthbar.stat." + adapter.id() + "." + key));
            }
        }
        return out;
    }

    private static Float maxOf(CompatibilityAdapter adapter, String key, Player player) {
        if (adapter instanceof ThirstWasTakenAdapter) return 20f;
        if (adapter instanceof ParCoolAdapter a) return a.readMax(player);
        if (adapter instanceof AppleSkinAdapter) return switch (key) {
            case "exhaustion" -> 4f;
            case "saturation" -> 20f;
            default -> null;
        };
        return null;
    }

    private static int colorOf(String modId, String key) {
        return switch (modId) {
            case "appleskin" -> key.equals("exhaustion") ? 0xFFFFAA00 : 0xFFFFD070;
            case "thirst" -> 0xFF4488FF;
            case "parcool" -> 0xFF66CC66;
            default -> 0xFFAAAAAA;
        };
    }

    // ================= AppleSkin =================

    /** 饱和度/消耗值来自本 MOD 服务端同步（无 AppleSkin 时同样可用；AppleSkin 提供食物预览语义） */
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
        @Override public boolean isCompatible() {
            // 探测 API 类是否存在（MOD 装了但 API 变更 → 优雅降级为不显示）
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
            if (player == null || !key.equals("thirst")) return null;
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
