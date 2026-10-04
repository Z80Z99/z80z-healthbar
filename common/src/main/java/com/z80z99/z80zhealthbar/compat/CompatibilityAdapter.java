package com.z80z99.z80zhealthbar.compat;

import java.util.List;

/**
 * 第三方 MOD 兼容适配器统一接口（任务书 7.4）。
 *
 * <p>契约：
 * <ul>
 *   <li>{@link #isAvailable()} 只做 ModList 检测，结果可缓存；目标 MOD 不存在时一切方法安全返回空值。</li>
 *   <li>{@link #readStat} 只读客户端合法可见数据；数据不可得时返回 null，绝不返回伪造的 0 值。</li>
 *   <li>适配器不得在类加载期触碰目标 MOD 类（避免 NoClassDefFoundError），访问一律反射 + 失败静默。</li>
 *   <li>新增兼容 MOD = 新增一个适配器并在 CompatManager 注册，不改动 HUD 核心。</li>
 * </ul>
 */
public interface CompatibilityAdapter {

    /** 目标 MOD id（用于检测与 hook 配置） */
    String id();

    /** 展示名（lang key 由调用方按 "z80zhealthbar.compat.<id>" 约定解析） */
    default String displayName() { return id(); }

    /** 目标 MOD 是否已安装（ModList 检测） */
    boolean isAvailable();

    /** 当前版本是否兼容（数据通道探测结果；不可用=false 表示降级） */
    default boolean isCompatible() { return isAvailable(); }

    /** 可提供的状态类型 key（如 "thirst"、"stamina"、"saturation"） */
    List<String> providedStats();

    /**
     * 读取状态。数值以 Float 返回；不可得返回 null。
     * @param statKey providedStats 之一
     */
    Float readStat(String statKey);

    /** 该数据是否需要服务端安装本 MOD（网络同步）才能准确 */
    default boolean needsServer() { return false; }

    /** 附加信息（如数据来源说明），供兼容页展示；可为 null */
    default String notes() { return null; }
}
