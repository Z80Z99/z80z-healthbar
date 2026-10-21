package com.z80z99.z80zhealthbar.datasource;

import com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource;
import com.z80z99.z80zhealthbar.overlay.parts.compat.ReflectionHelperCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.function.Function;

/**
 * 候选实测：构造**临时** CustomSource（不加入配置）并调用现有反射通道读一次。
 *
 * <p>用于三处：成员页显示 `17 / 20`、扫描排序的“实测成功”加分、变化探测会话的 reader。
 * 全部走 {@link ReflectionHelperCompat} 的缓存路径；失败返回 null（显示「读取失败」）。
 */
public final class CandidateProbe {

    private CandidateProbe() {}

    /** 构造值方法的临时源（按通道填好写回规则对应的字段） */
    public static CustomSource tempSource(String modId, int channel, String holderClass,
                                          String holderField, String ifaceClass,
                                          String readMethod, String valueMethod) {
        CustomSource s = new CustomSource();
        s.id = "probe";
        s.enabled = true;
        s.modId = modId == null ? "" : modId;
        s.channel = channel;
        s.holderClass = holderClass == null ? "" : holderClass;
        s.holderField = holderField == null ? "" : holderField;
        s.ifaceClass = ifaceClass == null ? "" : ifaceClass;
        s.readMethod = readMethod == null ? "" : readMethod;
        s.valueMethod = valueMethod == null ? "" : valueMethod;
        return s;
    }

    /** 读一次数值；无玩家或读取失败返回 null */
    public static Float read(CustomSource src) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc == null ? null : mc.player;
        if (p == null || src == null) return null;
        try {
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
            return null;
        }
    }

    /** 读上限（maxMethod → staticMax）;失败返回 null */
    public static Float readMax(CustomSource src) {
        var adapter = new com.z80z99.z80zhealthbar.compat.CustomSourceAdapter(src);
        Player p = Minecraft.getInstance() == null ? null : Minecraft.getInstance().player;
        if (p == null) return null;
        try {
            return adapter.resolveMax(p);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 包装成 ProbeSession 的 reader（值方法） */
    public static Function<Void, Float> readerOf(CustomSource src) {
        return ignored -> read(src);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
