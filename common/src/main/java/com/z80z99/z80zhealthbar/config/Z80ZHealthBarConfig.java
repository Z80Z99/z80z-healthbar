package com.z80z99.z80zhealthbar.config;

import com.z80z99.z80zhealthbar.config.configs.*;
import com.z80z99.z80zhealthbar.mobdisplay.EntityHealthStyle;
import com.z80z99.z80zhealthbar.mobdisplay.MobDisplayMode;

public class Z80ZHealthBarConfig {
    public int configVersion = 5;

    /**
     * 实体生命值样式（EntityHealthStyle 名称字符串）。
     * 用字符串存储而非枚举：非法/未知值在解析时回退默认值而不是让整份配置加载失败。
     */
    public String entityStyle = EntityHealthStyle.ASTEORBAR.name();

    /** 旧字段（configVersion 1），加载时迁移到 entityStyle 后由迁移器置 null */
    public MobDisplayMode mobDisplayMode;

    public OverlayConfig overlay = new OverlayConfig();
    public BarStyleConfig barStyle = new BarStyleConfig();
    public PlaqueStyleConfig plaqueStyle = new PlaqueStyleConfig();
    public StyleAConfig styleA = new StyleAConfig();
    public EntityAddonsConfig entityAddons = new EntityAddonsConfig();
    public HudLayoutConfig hudLayout = new HudLayoutConfig();
    public VisibilityConfig visibility = new VisibilityConfig();
    public ColorConfig colors = new ColorConfig();
    public CompatConfig compat = new CompatConfig();
    public DamagePopupConfig damagePopup = new DamagePopupConfig();
    public DynamicFxConfig dynamicFx = new DynamicFxConfig();
    /** 用户自定义外部数据源（通用兼容框架） */
    public CustomSourceConfig customSources = new CustomSourceConfig();
    /** 自定义聊天窗口（独立窗口化聊天 + 可分离输入框;默认关） */
    public com.z80z99.z80zhealthbar.config.configs.ChatWindowConfig chatWindow =
            new com.z80z99.z80zhealthbar.config.configs.ChatWindowConfig();

    /** 解析实体样式（非法值回退 ASTEORBAR） */
    public EntityHealthStyle entityStyleParsed() {
        try {
            return EntityHealthStyle.valueOf(entityStyle);
        } catch (IllegalArgumentException e) {
            return EntityHealthStyle.ASTEORBAR;
        }
    }

    public void setEntityStyle(EntityHealthStyle style) {
        this.entityStyle = style.name();
    }
}
