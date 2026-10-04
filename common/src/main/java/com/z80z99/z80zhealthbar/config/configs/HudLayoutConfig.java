package com.z80z99.z80zhealthbar.config.configs;

import com.z80z99.z80zhealthbar.layout.HudAnchor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CUSTOM 模式的组件级 HUD 布局（任务书 4.2 CUSTOM：不同状态分别选择样式/布局/位置）。
 * 编辑器（HudLayoutScreen）与运行时渲染（CustomHudRenderer）共用本配置与
 * HudLayoutSolver，保证"编辑器预览 = 实际渲染"。
 */
public class HudLayoutConfig {

    /** 组件显示模式 */
    public enum ComponentMode { BAR, ICON, OFF }

    /** 数值文本水平对齐（BAR 模式） */
    public enum TextAlign { LEFT, CENTER, RIGHT }

    /** 图标相对条形的位置（BAR 模式） */
    public enum IconSide { LEFT, RIGHT }

    /** 单组件布局 */
    public static class ComponentLayout {
        public String mode = ComponentMode.BAR.name();
        public String anchor = HudAnchor.BOTTOM_CENTER.name();
        /** 拖拽偏移（px，GUI 缩放坐标） */
        public int offsetX = 0;
        public int offsetY = 0;
        /** 组件缩放（0.5..2） */
        public double scale = 1.0;
        /** 与同锚点下一组件的间距（px） */
        public int spacing = 4;
        /** 条宽（px，仅 BAR 模式） */
        public int barWidth = 120;
        /** 是否显示数值文本 */
        public boolean showText = true;
        /** 数值文本水平对齐（BAR 模式；默认居中） */
        public String textAlign = TextAlign.CENTER.name();
        /** 数值文本微调偏移（px，正值向右/向下） */
        public int textOffsetX = 0;
        public int textOffsetY = 0;
        /** 条高（px；5..16，默认 9 使 8px 字体完整落在条内） */
        public int barHeight = 9;
        /** 图标位置：LEFT=条左侧（默认），RIGHT=条右侧 */
        public String iconSide = IconSide.LEFT.name();
        /** 文本独立锚点（空 = 跟随条；设置后文本脱离条，独立锚定定位） */
        public String textAnchor = "";
        /** 文本附加缩放（独立于组件缩放） */
        public double textScale = 1.0;
        /** 图标独立锚点（空 = 跟随条，按 iconSide 位于条侧） */
        public String iconAnchor = "";
        /** 图标附加缩放（独立于组件缩放） */
        public double iconScale = 1.0;
        /** 图标微调偏移（px，正值向右/向下；跟随条时相对条，独立时相对锚点） */
        public int iconOffsetX = 0;
        public int iconOffsetY = 0;

        public ComponentMode modeParsed() {
            try {
                return ComponentMode.valueOf(mode);
            } catch (IllegalArgumentException e) {
                return ComponentMode.BAR;
            }
        }

        public HudAnchor anchorParsed() {
            try {
                return HudAnchor.valueOf(anchor);
            } catch (IllegalArgumentException e) {
                return HudAnchor.BOTTOM_CENTER;
            }
        }

        public TextAlign textAlignParsed() {
            try {
                return TextAlign.valueOf(textAlign);
            } catch (IllegalArgumentException | NullPointerException e) {
                return TextAlign.CENTER;
            }
        }

        public IconSide iconSideParsed() {
            try {
                return IconSide.valueOf(iconSide);
            } catch (IllegalArgumentException | NullPointerException e) {
                return IconSide.LEFT;
            }
        }

        /** 文本独立锚点；null = 跟随条 */
        public HudAnchor textAnchorParsed() {
            return anchorOrNull(textAnchor);
        }

        /** 图标独立锚点；null = 跟随条 */
        public HudAnchor iconAnchorParsed() {
            return anchorOrNull(iconAnchor);
        }

        private static HudAnchor anchorOrNull(String s) {
            if (s == null || s.isBlank()) return null;
            try {
                return HudAnchor.valueOf(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public ComponentLayout copy() {
            ComponentLayout c = new ComponentLayout();
            c.mode = mode; c.anchor = anchor;
            c.offsetX = offsetX; c.offsetY = offsetY;
            c.scale = scale; c.spacing = spacing;
            c.barWidth = barWidth; c.showText = showText;
            c.textAlign = textAlign; c.textOffsetX = textOffsetX; c.textOffsetY = textOffsetY;
            c.barHeight = barHeight; c.iconSide = iconSide;
            c.textAnchor = textAnchor; c.textScale = textScale;
            c.iconAnchor = iconAnchor; c.iconScale = iconScale;
            c.iconOffsetX = iconOffsetX; c.iconOffsetY = iconOffsetY;
            return c;
        }
    }

    /** 六大核心组件（插入顺序 = 同锚点内的默认堆叠顺序） */
    public static final String HEALTH = "health";
    public static final String FOOD = "food";
    public static final String AIR = "air";
    public static final String EXPERIENCE = "experience";
    public static final String ARMOR = "armor";
    public static final String MOUNT = "mount";
    /** 兼容状态组件组（thirst/stamina/exhaustion 等，动态行） */
    public static final String COMPAT = "compat";

    public Map<String, ComponentLayout> components = defaultComponents();

    /** 编辑器悬浮属性面板位置（GUI 坐标；-1 = 默认右上） */
    public int panelX = -1;
    public int panelY = -1;

    /** 预设布局标识（编辑器内选择并一键应用） */
    public static final List<String> PRESETS = List.of("MODERN", "CLASSIC", "MINIMAL", "SIDE");

    public static Map<String, ComponentLayout> defaultComponents() {
        return presetComponents("MODERN");
    }

    /** 应用预设布局（替换全部组件布局；编辑器"应用预设"按钮调用） */
    public void applyPreset(String preset) {
        components = presetComponents(preset);
    }

    /**
     * 预设布局定义。map 顺序 = 同锚点内自上而下的堆叠顺序（底部锚点组整体上堆），
     * 每个预设按期望的视觉层序构造 map。
     *
     * <ul>
     *   <li><b>MODERN</b>（默认设计）：生命条居中主视觉、细经验条紧随其下（贴合原版垂直次序）；
     *       饥饿/氧气居右、护甲居左，均与物品栏同水平线；兼容属性叠在氧气上方、坐骑条在生命上方。</li>
     *   <li><b>CLASSIC</b>：贴近原版分区——护甲/生命为图标居左、氧气/饥饿为图标居右，
     *       经验为居中细条。</li>
     *   <li><b>MINIMAL</b>：极简——仅生命 + 饥饿两条居中细条（无文字无图标），其余关闭。</li>
     *   <li><b>SIDE</b>：侧栏——六项状态栏纵向堆叠在左上角，经验条保留底部居中。</li>
     * </ul>
     */
    public static Map<String, ComponentLayout> presetComponents(String preset) {
        Map<String, ComponentLayout> m = new LinkedHashMap<>();
        String p = preset == null ? "" : preset;

        // 各预设的堆叠顺序（自上而下）
        String[] order = switch (p) {
            case "CLASSIC" -> new String[]{ARMOR, HEALTH, AIR, FOOD, EXPERIENCE, MOUNT, COMPAT};
            case "MINIMAL" -> new String[]{FOOD, HEALTH, AIR, EXPERIENCE, ARMOR, MOUNT, COMPAT};
            case "SIDE" -> new String[]{HEALTH, FOOD, AIR, ARMOR, MOUNT, COMPAT, EXPERIENCE};
            default -> new String[]{MOUNT, HEALTH, EXPERIENCE, COMPAT, AIR, FOOD, ARMOR};
        };
        for (String key : order) m.put(key, new ComponentLayout());

        switch (p) {
            case "CLASSIC" -> {
                for (String key : new String[]{HEALTH, ARMOR}) {
                    m.get(key).anchor = HudAnchor.BOTTOM_LEFT.name();
                    m.get(key).mode = ComponentMode.ICON.name();
                }
                for (String key : new String[]{FOOD, AIR}) {
                    m.get(key).anchor = HudAnchor.BOTTOM_RIGHT.name();
                    m.get(key).mode = ComponentMode.ICON.name();
                }
                m.get(EXPERIENCE).barWidth = 182;
                m.get(EXPERIENCE).barHeight = 5;
                m.get(EXPERIENCE).showText = false;
                m.get(MOUNT).barWidth = 140;
                m.get(COMPAT).barWidth = 120;
            }
            case "MINIMAL" -> {
                for (String key : new String[]{HEALTH, FOOD}) {
                    m.get(key).barWidth = 182;
                    m.get(key).barHeight = 5;
                    m.get(key).showText = false;
                }
                m.get(EXPERIENCE).mode = ComponentMode.OFF.name();
                m.get(AIR).mode = ComponentMode.OFF.name();
                m.get(ARMOR).mode = ComponentMode.OFF.name();
                m.get(COMPAT).mode = ComponentMode.OFF.name();
                m.get(MOUNT).barWidth = 182;
                m.get(MOUNT).barHeight = 5;
                m.get(MOUNT).showText = false;
            }
            case "SIDE" -> {
                for (String key : new String[]{HEALTH, FOOD, AIR, ARMOR, MOUNT, COMPAT}) {
                    m.get(key).anchor = HudAnchor.TOP_LEFT.name();
                    m.get(key).barWidth = 120;
                }
                m.get(EXPERIENCE).barWidth = 182;
                m.get(EXPERIENCE).barHeight = 5;
                m.get(EXPERIENCE).showText = false;
            }
            default -> {
                m.get(HEALTH).barWidth = 140;
                m.get(EXPERIENCE).barWidth = 182;
                m.get(EXPERIENCE).barHeight = 5;
                m.get(FOOD).anchor = HudAnchor.BOTTOM_RIGHT.name();
                m.get(FOOD).barWidth = 81;
                m.get(AIR).anchor = HudAnchor.BOTTOM_RIGHT.name();
                m.get(AIR).barWidth = 81;
                m.get(ARMOR).anchor = HudAnchor.BOTTOM_LEFT.name();
                m.get(ARMOR).barWidth = 81;
                m.get(MOUNT).barWidth = 140;
                m.get(COMPAT).anchor = HudAnchor.BOTTOM_RIGHT.name();
                m.get(COMPAT).barWidth = 81;
            }
        }
        return m;
    }

    /** 取组件布局（缺失/损坏时回退默认并写回） */
    public ComponentLayout get(String key) {
        ComponentLayout c = components.get(key);
        if (c == null) {
            c = new ComponentLayout();
            components.put(key, c);
        }
        return c;
    }

    public void resetToDefaults() {
        components = defaultComponents();
    }

    public void resetComponent(String key) {
        components.put(key, defaultComponents().get(key));
    }
}
