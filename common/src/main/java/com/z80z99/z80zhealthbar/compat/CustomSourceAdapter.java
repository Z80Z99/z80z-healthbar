package com.z80z99.z80zhealthbar.compat;

import com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource;
import com.z80z99.z80zhealthbar.overlay.parts.compat.ReflectionHelperCompat;
import com.z80z99.z80zhealthbar.platform.PlatformService;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * 用户自定义数据源的适配器桥接（通用兼容框架）。
 * <p>把一条 {@link CustomSource} 配置包装成标准 {@link CompatibilityAdapter},
 * 从而自动获得 collect（兼容条组/compat_ 单行条）与 readValue（模板变量）三条消费通道。
 * <p>id 恒为 {@code custom_<id>} 与内建适配器命名天然隔离;Stat key = {@code custom_<id>.custom_<id>}。
 */
public final class CustomSourceAdapter implements CompatibilityAdapter {

    private final CustomSource src;
    /** isCompatible 结果缓存(5s 失效重试;热路径防错误配置每帧全反射) */
    private transient boolean compatibleCached;
    private transient long compatibleCacheUntil;

    public CustomSourceAdapter(CustomSource src) {
        this.src = src;
    }

    public CustomSource source() {
        return src;
    }

    @Override
    public String id() {
        return "custom_" + src.id;
    }

    @Override
    public String displayName() {
        return src.name();
    }

    @Override
    public boolean isAvailable() {
        if (!src.enabled || !src.paramsComplete()) return false;
        // 通道 4（手持物品）不需要目标 MOD;其它通道要求 modId 已装（留空 = 不检查,便于测试）
        if (src.channel == 4 || src.modId == null || src.modId.isBlank()) return true;
        return PlatformService.get() != null && PlatformService.get().isModLoaded(src.modId);
    }

    @Override
    public boolean isCompatible() {
        // 结果缓存:isCompatible 会做一次全反射,而它在 shouldCancelOverlay(每 overlay 每帧)
        // 与 readValue(模板变量/第二层)路径上被高频调用——错误配置曾每帧抛异常风暴(审计 S4)。
        // 配置热重载时由 onConfigReload → clearCaches 之外,这里按"上次探测时间"5s 失效重试。
        long now = System.currentTimeMillis();
        if (compatibleCacheUntil > now) return compatibleCached;
        compatibleCached = probeCompatible();
        compatibleCacheUntil = now + 5000L;
        return compatibleCached;
    }

    private boolean probeCompatible() {
        // 通道 4 与原版 API 无第三方类依赖,恒可用;其余通道首次读取失败即视为不兼容
        if (src.channel == 4) return true;
        return readStat(src.id) != null || src.staticMax > 0;
    }

    @Override
    public List<String> providedStats() {
        return List.of(src.id);
    }

    @Override
    public Float readStat(String statKey) {
        Player p = net.minecraft.client.Minecraft.getInstance().player;
        if (p == null) return null;
        try {
            boolean hasArgs = (src.readParamTypes != null && !src.readParamTypes.isBlank())
                    || (src.valueParamTypes != null && !src.valueParamTypes.isBlank());
            if (hasArgs) return readWithArgs(p);
            return switch (src.channel) {
                case 0 -> ReflectionHelperCompat.capabilityNumber(
                        src.holderClass, src.holderField, src.ifaceClass,
                        new String[]{src.readMethod}, p);
                case 2 -> ReflectionHelperCompat.instanceNumber(
                        src.holderClass, src.readMethod, new String[]{src.valueMethod}, p);
                case 3 -> isBlank(src.valueMethod)
                        ? ReflectionHelperCompat.playerMethodNumber(src.readMethod, p)
                        : ReflectionHelperCompat.playerChainNumber(src.readMethod, src.valueMethod, p);
                case 4 -> switch (src.itemMode) {
                    case 1 -> ReflectionHelperCompat.heldItemNbtInt(src.readMethod, p);
                    case 2 -> ReflectionHelperCompat.heldItemMethodNumber(src.readMethod, p);
                    default -> ReflectionHelperCompat.heldItemDurability(p);
                };
                default -> ReflectionHelperCompat.staticNumber(
                        new String[]{src.holderClass}, new String[]{src.readMethod}, p);
            };
        } catch (Throwable t) {
            return null; // 配置写错绝不崩游戏
        }
    }

    /** 带参数方法的读取路径（仅通道 1/2/3;通道 0/4 不支持参数,返回 null 由诊断行报读取失败） */
    private Float readWithArgs(Player p) {
        return switch (src.channel) {
            case 1 -> ReflectionHelperCompat.staticNumberWithArgs(
                    src.holderClass, src.readMethod, p,
                    src.readParamTypes, src.readArgs);
            case 2 -> ReflectionHelperCompat.instanceNumberWithArgs(
                    src.holderClass, src.readMethod, src.readParamTypes, src.readArgs,
                    src.valueMethod, src.valueParamTypes, src.valueArgs, p);
            case 3 -> ReflectionHelperCompat.playerMethodNumberWithArgs(
                    src.readMethod, src.readParamTypes, src.readArgs, p);
            default -> null;
        };
    }

    /** 上限:maxMethod（同通道）→ staticMax → 原版耐久模式自带 max → null（仅文本）。
     *  由 {@link CompatAdapters#maxOf} 调用（接口无 readMax 方法）。 */
    public Float resolveMax(Player player) {
        if (src.channel == 4 && src.itemMode == com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.ITEM_VANILLA_DURABILITY) {
            Float m = ReflectionHelperCompat.heldItemMaxDurability(player);
            if (m != null) return m;
        }
        if (src.maxMethod != null && !src.maxMethod.isBlank()) {
            try {
                Float m = switch (src.channel) {
                    case 0 -> ReflectionHelperCompat.capabilityNumber(
                            src.holderClass, src.holderField, src.ifaceClass,
                            new String[]{src.maxMethod}, player);
                    case 2 -> ReflectionHelperCompat.instanceNumber(
                            src.holderClass, src.readMethod, new String[]{src.maxMethod}, player);
                    case 3 -> isBlank(src.valueMethod)
                            ? ReflectionHelperCompat.playerMethodNumber(src.maxMethod, player)
                            : ReflectionHelperCompat.playerChainNumber(src.readMethod, src.maxMethod, player);
                    case 4 -> ReflectionHelperCompat.heldItemMethodNumber(src.maxMethod, player);
                    default -> ReflectionHelperCompat.staticNumber(
                            new String[]{src.holderClass}, new String[]{src.maxMethod}, player);
                };
                if (m != null) return m;
            } catch (Throwable ignored) {
            }
        }
        return src.staticMax > 0 ? src.staticMax : null;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
