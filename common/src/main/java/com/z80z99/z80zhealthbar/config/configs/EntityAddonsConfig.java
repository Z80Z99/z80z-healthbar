package com.z80z99.z80zhealthbar.config.configs;

/**
 * 实体显示附加组件（任务书 3.2）。
 *
 * <p>样式 B（牌匾）中护甲/韧性/氧气是牌匾固有行，由 PlaqueStyleConfig 的同行开关控制；
 * 样式 A/C（条形）下这些信息默认不显示，玩家主动开启后以牌匾图标行形式叠加在主条下方，
 * 保证"非重叠信息可作为可选附加组件"且默认不重复绘制。
 */
public class EntityAddonsConfig {
    public boolean armorRow = false;
    public boolean toughnessRow = false;
    public boolean airRow = false;
    /** 附加行与主条间距（像素） */
    public int rowGap = 2;
}
