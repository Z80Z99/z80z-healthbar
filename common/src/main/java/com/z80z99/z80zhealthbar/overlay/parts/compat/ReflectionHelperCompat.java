package com.z80z99.z80zhealthbar.overlay.parts.compat;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * 反射辅助：安全访问第三方 mod API，避免编译时依赖与运行期 NoClassDefFoundError。
 * 失败一律返回 null —— 调用方按"数据不可得"处理，绝不把未知伪装成 0（任务书 7 原则）。
 */
public final class ReflectionHelperCompat {
    private ReflectionHelperCompat() {}

    /** 依次尝试候选类路径与静态方法（Player → Number） */
    public static Float staticNumber(String[] classNames, String[] methodNames, Player player) {
        for (String cn : classNames) {
            Class<?> clazz;
            try {
                clazz = Class.forName(cn);
            } catch (ClassNotFoundException e) {
                continue;
            }
            for (String mn : methodNames) {
                try {
                    Method m = clazz.getMethod(mn, Player.class);
                    Object r = m.invoke(null, player);
                    if (r instanceof Number n) return n.floatValue();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    /** 工厂静态方法（Player → 实例）后调用实例方法（无参 → Number） */
    public static Float instanceNumber(String className, String factoryMethod,
                                       String[] valueMethods, Player player) {
        try {
            Class<?> clazz = Class.forName(className);
            Method factory = clazz.getMethod(factoryMethod, Player.class);
            Object instance = factory.invoke(null, player);
            if (instance == null) return null;
            for (String vm : valueMethods) {
                try {
                    Method m = instance.getClass().getMethod(vm);
                    Object r = m.invoke(instance);
                    if (r instanceof Number n) return n.floatValue();
                } catch (Exception ignored) {
                }
            }
        } catch (Throwable t) {
            Z80ZHealthBar.LOGGER.debug("[Compat] unavailable: {}.{}", className, factoryMethod);
        }
        return null;
    }

    /**
     * Forge Capability 反射读取：静态 Capability token 字段 → {@code player.getCapability(token)} →
     * 能力实例上的无参方法 → Number。方法优先在 public 接口上解析（invoke 的可见性检查基于声明类，
     * 非 public 实现类经由 public 接口方法调用是合法的）。
     *
     * <p>Thirst Was Taken 1.4.x 的玩家口渴数据即走此通道——其 ThirstHelper API 只有物品侧查询
     * （getThirst(ItemStack)），玩家数据在 {@code ModCapabilities.PLAYER_THIRST} capability 里。
     */
    public static Float capabilityNumber(String tokenHolderClass, String tokenField,
                                         String capabilityInterface, String[] valueMethods, Player player) {
        try {
            Object token = Class.forName(tokenHolderClass).getField(tokenField).get(null);
            Method getCap = player.getClass().getMethod("getCapability", token.getClass());
            Object capOpt = getCap.invoke(player, token);
            // getCapability 返回 LazyOptional<IThirst> 包装——必须先解包成能力实例,
            // 直接在包装上反射数据方法必然 IllegalArgumentException（实测踩坑）
            Object cap = unwrapLazy(capOpt);
            if (cap == null) return null;
            Class<?> itf = Class.forName(capabilityInterface);
            for (String vm : valueMethods) {
                try {
                    Object r = itf.getMethod(vm).invoke(cap);
                    if (r instanceof Number n) return n.floatValue();
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable t) {
            Z80ZHealthBar.LOGGER.debug("[Compat] capability unavailable: {}.{}", tokenHolderClass, tokenField);
        }
        return null;
    }

    /** 解包 Forge LazyOptional（resolve() → Optional → get）;非包装对象原样返回 */
    private static Object unwrapLazy(Object value) throws Exception {
        if (value == null) return null;
        try {
            Object opt = value.getClass().getMethod("resolve").invoke(value);
            if (opt == null) return value;
            if ((Boolean) opt.getClass().getMethod("isPresent").invoke(opt)) {
                return opt.getClass().getMethod("get").invoke(opt);
            }
            return null; // capability 未附加
        } catch (NoSuchMethodException e) {
            return value; // 没有 resolve() —— 不是 LazyOptional
        }
    }

    // ===== 以下为通用数据源框架（CustomSourceAdapter）使用的扩展通道 =====
    // 反射结果按 类名/方法名 缓存（含失败 sentinel）,避免每帧 Class.forName/getMethod 与异常开销。

    private record MethodKey(String owner, String name, String paramType) {}
    private static final java.util.Map<MethodKey, Method> METHOD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<MethodKey> MISSING_METHODS =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** 缓存式静态方法查找（paramType 为参数类全名,/分隔多个参数）;失败缓存,不重复抛异常 */
    private static Method findMethod(String owner, String name, Class<?>... params) {
        StringBuilder sig = new StringBuilder(params.length == 0 ? "" : params[0].getName());
        for (int i = 1; i < params.length; i++) sig.append('/').append(params[i].getName());
        MethodKey key = new MethodKey(owner, name, sig.toString());
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;
        if (MISSING_METHODS.contains(key)) return null;
        try {
            Method m = Class.forName(owner).getMethod(name, params);
            METHOD_CACHE.put(key, m);
            return m;
        } catch (Throwable t) {
            MISSING_METHODS.add(key);
            return null;
        }
    }

    /** 通道 3:Player 实例上的无参方法（如 player.experienceLevel 式的 getter） */
    public static Float playerMethodNumber(String methodName, Player player) {
        try {
            Method m = findMethod(player.getClass().getName(), methodName);
            if (m == null) m = findMethod("net.minecraft.world.entity.player.Player", methodName);
            if (m == null) return null;
            Object r = m.invoke(player);
            return r instanceof Number n ? n.floatValue() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 通道 4a:手持物品原版耐久（value = maxDamage - damage） */
    public static Float heldItemDurability(Player player) {
        try {
            var stack = player.getMainHandItem();
            if (stack == null || stack.isEmpty()) return null;
            int max = stack.getMaxDamage();
            if (max <= 0) return null; // 无耐久物品
            return (float) (max - stack.getDamageValue());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 通道 4a 上限:手持物品最大耐久 */
    public static Float heldItemMaxDurability(Player player) {
        try {
            var stack = player.getMainHandItem();
            if (stack == null || stack.isEmpty()) return null;
            int max = stack.getMaxDamage();
            return max > 0 ? (float) max : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 通道 4b:手持物品 NBT 整型（key 可由点分嵌套,如 "GunData/Damage"） */
    public static Float heldItemNbtInt(String key, Player player) {
        try {
            var stack = player.getMainHandItem();
            if (stack == null || stack.isEmpty() || !stack.hasTag()) return null;
            var tag = stack.getTag();
            if (tag == null) return null;
            String k = key == null ? "" : key.trim();
            if (k.isEmpty()) return null;
            if (k.contains("/")) {
                String[] parts = k.split("/");
                var cur = tag;
                for (int i = 0; i < parts.length - 1; i++) {
                    if (!cur.contains(parts[i], net.minecraft.nbt.Tag.TAG_COMPOUND)) return null;
                    cur = cur.getCompound(parts[i]);
                }
                k = parts[parts.length - 1];
                if (!cur.contains(k, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) return null;
                return cur.getFloat(k);
            }
            if (!tag.contains(k, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) return null;
            return tag.getFloat(k);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 通道 4c:手持物品上的方法（无参 → Number;如 TACZ 的自定义 API） */
    public static Float heldItemMethodNumber(String methodName, Player player) {
        try {
            var stack = player.getMainHandItem();
            if (stack == null || stack.isEmpty()) return null;
            Method m = findMethod(stack.getClass().getName(), methodName);
            if (m == null) {
                for (Class<?> itf : stack.getClass().getInterfaces()) {
                    m = findMethod(itf.getName(), methodName);
                    if (m != null) break;
                }
            }
            if (m == null) return null;
            Object r = m.invoke(stack);
            return r instanceof Number n ? n.floatValue() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 清空反射缓存（配置热重载/源变更时调用,避免旧类名缓存残留） */
    public static void clearCaches() {
        METHOD_CACHE.clear();
        MISSING_METHODS.clear();
    }
}
