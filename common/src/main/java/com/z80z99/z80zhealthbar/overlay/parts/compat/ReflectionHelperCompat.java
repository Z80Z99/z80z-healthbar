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
}
