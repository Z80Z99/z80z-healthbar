package com.z80z99.z80zhealthbar.mobdisplay;

/**
 * 实体生命值样式（任务书 3.1/3.2）。同一时刻只绘制所选样式的生命值显示；
 * 护甲/韧性/氧气等非血量信息经 EntityAddonsConfig 作为可选附加组件叠加。
 */
public enum EntityHealthStyle {
    /** 关闭实体生命值显示 */
    OFF,
    /** 样式 A：YDM's Mob Health Bar —— 贴图外框条（含心形行变体）+ 名称 + 数值 */
    MOBHEALTHBAR,
    /** 样式 B：Mob Plaques —— 原版图标牌匾（心形/护甲/韧性/气泡） */
    MOBPLAQUES,
    /** 样式 C：AsteorBar —— 动态色长条 + 吸收环 + 数值文本 */
    ASTEORBAR;

    /** 旧配置 MobDisplayMode 迁移映射 */
    public static EntityHealthStyle fromLegacy(MobDisplayMode legacy) {
        if (legacy == null) return ASTEORBAR;
        return switch (legacy) {
            case BARS -> ASTEORBAR;
            case PLAQUES -> MOBPLAQUES;
            case BOTH -> ASTEORBAR; // BOTH 属旧式重复绘制，迁移到 C 并提示可开附加组件
        };
    }
}
