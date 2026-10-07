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
        /** 组件类型（空 = 从键推断：键名取 '#' 与 '.' 前段）。多实例键如 "health#2" 靠此分发渲染。 */
        public String type = "";
        /** 自定义名称（空 = 用类型默认名;列表/标题优先显示） */
        public String displayName = "";
        /** 参与同锚点堆叠（默认关 = 独立定位:改尺寸/缩放不影响其它组件的位置） */
        public boolean stack = false;
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
        /** 是否显示条本体（BAR 模式卡片+填充;关闭后只剩图标/文本部件 = 自由组合） */
        public boolean showBar = true;
        /** 数值文本模板（空 = 各组件默认格式）。变量：{health} {max_health} {absorption} {food} {food_max}
         *  {air} {air_max} {armor} {toughness} {level} {xp_percent} {mount_health} {mount_max} */
        public String textFormat = "";
        /** 是否显示状态图标（BAR 模式） */
        public boolean showIcon = true;
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
        /** 逻辑分组名（空 = 未分组）。同组组件在编辑器列表中收纳为一行,便于分开调整。 */
        public String group = "";
        /** 外部图标贴图文件名（config/z80zhealthbar/icons/ 下的 png;空 = 原版 icons.png 图标） */
        public String iconTexture = "";

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
            c.mode = mode; c.type = type; c.anchor = anchor;
            c.displayName = displayName; c.stack = stack;
            c.offsetX = offsetX; c.offsetY = offsetY;
            c.scale = scale; c.spacing = spacing;
            c.barWidth = barWidth; c.showText = showText; c.showBar = showBar; c.showIcon = showIcon;
            c.textFormat = textFormat;
            c.textAlign = textAlign; c.textOffsetX = textOffsetX; c.textOffsetY = textOffsetY;
            c.barHeight = barHeight; c.iconSide = iconSide;
            c.textAnchor = textAnchor; c.textScale = textScale;
            c.iconAnchor = iconAnchor; c.iconScale = iconScale;
            c.iconOffsetX = iconOffsetX; c.iconOffsetY = iconOffsetY;
            c.group = group; c.iconTexture = iconTexture;
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

    /** 应用预设布局（替换标准组件;保留用户自建的多实例/原子组件键,即键含 '#' 或带原子后缀） */
    public void applyPreset(String preset) {
        Map<String, ComponentLayout> fresh = presetComponents(preset);
        Map<String, ComponentLayout> merged = new LinkedHashMap<>(fresh);
        for (Map.Entry<String, ComponentLayout> e : components.entrySet()) {
            if (e.getKey().contains("#") || ADDABLE_TYPES.indexOf(baseKeyOf(e.getKey())) >= 7
                    || e.getKey().startsWith("text")) {
                merged.put(e.getKey(), e.getValue()); // 自建实例与原子组件不被预设清掉
            }
        }
        components = merged;
    }

    /** 键的类型段（剥 '#' 实例序号与 '.' 子元素后缀） */
    public static String baseKeyOf(String key) {
        String k = key == null ? "" : key;
        int hash = k.indexOf('#');
        if (hash > 0) k = k.substring(0, hash);
        int dot = k.indexOf('.');
        if (dot > 0) k = k.substring(0, dot);
        return k;
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

    /** 组件类型解析：显式 type 优先,否则取键名 '#' 前段（多实例）,再取 '.' 前段（子元素） */
    public static String typeOf(String key, ComponentLayout c) {
        if (c != null && c.type != null && !c.type.isBlank()) return c.type;
        String k = key == null ? "" : key;
        int hash = k.indexOf('#');
        if (hash > 0) k = k.substring(0, hash);
        int dot = k.indexOf('.');
        if (dot > 0) k = k.substring(0, dot);
        return k;
    }

    /** 可添加的原子组件类型（编辑器"添加组件"清单;纯文本/纯图标/自由文本） */
    public static final List<String> ADDABLE_TYPES = List.of(
            HEALTH, FOOD, AIR, ARMOR, MOUNT, EXPERIENCE, COMPAT,
            "health_text", "food_text", "air_text", "armor_text", "mount_text", "xp_text",
            "health_icon", "food_icon", "air_icon", "armor_icon", "mount_icon",
            "text");

    /** 生成某类型的新实例键（type 或 type#N,取空闲的最小 N;N=1 时省略后缀） */
    public String addInstance(String type) {
        for (int n = 1; n <= 64; n++) {
            String key = n == 1 ? type : type + "#" + n;
            if (!components.containsKey(key)) {
                ComponentLayout c = new ComponentLayout();
                c.type = type; // 显式写 type:渲染分发不依赖键名解析
                components.put(key, c);
                return key;
            }
        }
        return null;
    }

    /** 复制实例（键含 '#' 或裸类型键均可,新键 = 同类型下一空闲序号） */
    public String duplicateInstance(String key) {
        ComponentLayout src = components.get(key);
        if (src == null) return null;
        String type = typeOf(key, src);
        for (int n = 1; n <= 64; n++) {
            String nk = n == 1 ? type : type + "#" + n;
            if (!components.containsKey(nk)) {
                components.put(nk, src.copy());
                return nk;
            }
        }
        return null;
    }

    /** 删除实例（连同其 .text/.icon 子元素键）;返回是否删除 */
    public boolean removeInstance(String key) {
        boolean removed = components.remove(key) != null;
        components.remove(key + ".text");
        components.remove(key + ".icon");
        return removed;
    }

    public void resetToDefaults() {
        components = defaultComponents();
    }

    public void resetComponent(String key) {
        ComponentLayout def = defaultComponents().get(key);
        // 多实例/未知键无预设默认——回退保留当前布局而非插入 null（防 map 出现 null 值）
        components.put(key, def != null ? def : new ComponentLayout());
    }
}
