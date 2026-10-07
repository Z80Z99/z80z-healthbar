package com.z80z99.z80zhealthbar.gui;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig.ComponentLayout;
import com.z80z99.z80zhealthbar.layout.HudAnchor;
import com.z80z99.z80zhealthbar.layout.HudLayoutSolver;
import com.z80z99.z80zhealthbar.overlay.BarLayouts;
import com.z80z99.z80zhealthbar.overlay.CustomHudRenderer;
import com.z80z99.z80zhealthbar.overlay.HudRenderer;
import com.z80z99.z80zhealthbar.overlay.HudStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 游戏内 HUD 布局编辑器（任务书 4.4）——**三种样式的统一编辑中心**。
 *
 * <ul>
 *   <li>面板顶部"编辑样式"行可在 原版 / 长条(AsteorBar) / 自定义 之间切换，
 *       并直接写入配置（预览 = 游戏内实际渲染）。</li>
 *   <li>长条样式：面板展示其全部布局参数（布局样式/条长/条高/边距/文本缩放等）。</li>
 *   <li>自定义样式：元素点选拖动（条/文本/图标），条宽/条高/对齐/锚点/缩放等全部参数，
 *       另含四套预设（现代/经典/极简/侧栏）。</li>
 *   <li>悬浮属性面板可像独立窗口一样拖动（标题栏或面板空白处），位置持久化到配置。</li>
 *   <li>预览 = 实际渲染：自定义走 {@link CustomHudRenderer#render}，
 *       长条走 {@link HudRenderer} 实路径（样式覆写），两处均在 GUI 缩放坐标系内所见即所得。</li>
 * </ul>
 */
public final class HudLayoutScreen extends Screen {

    // ---- 悬浮面板布局常量（GUI 坐标） ----
    private static final int PANEL_W = 178;
    private static final int TITLE_H = 14;
    private static final int HEADER_H = 11;
    private static final int ROW_H = 16;
    private static final int ROW_GAP = 2;
    private static final int BTN_H = 14;
    private static final int STEP_W = 22;
    private static final int CYCLE_W = 84;
    /** 行内控件区左缘（(?) 说明标记不得越过此处,否则会盖住控件并抢走点击） */
    private static final int CONTROL_LEFT = 76;

    private final Screen parent;
    private String selected = HudLayoutConfig.HEALTH;
    private int draggingX, draggingY;
    private int dragStartOffX, dragStartOffY;
    private boolean dragging;

    /** 长条样式：预览中选中的条 + 拖拽状态（BarLayouts.KEYS 之一） */
    private String selectedAsteorBar;
    /** 自定义样式："添加组件"动作当前选择的类型（点击即添加该类型实例并轮换） */
    private String addType = HudLayoutConfig.HEALTH;
    private String draggingAsteorBar;
    private int barGrabDX, barGrabDY;
    /** 状态图标拆分拖拽：图标与条可分离摆放（写回 iconOff*） */
    private boolean draggingAsteorIcon;
    private int iconGrabX, iconGrabY, iconStartOffX, iconStartOffY;
    /** 数值文本拆分拖拽：文本与条可分离摆放（写回 textOff*） */
    private boolean draggingAsteorText;
    private int textGrabX, textGrabY, textStartOffX, textStartOffY;

    // ---- 悬浮面板状态 ----
    private final List<PEntry> panelEntries = new ArrayList<>();
    private int panelX, panelY, panelH;
    private boolean panelDragging;
    /** 面板滚动条拖拽中 */
    private boolean panelScrollbarDragging;
    private int panelGrabX, panelGrabY;
    /** 面板滚动（px,平滑逼近目标;滚轮悬停面板时滚动,面板外滚轮仍是组件缩放） */
    private float panelScroll, panelScrollTarget;
    private int panelContentH;
    /** 当前选择的预设布局（点"应用预设"按钮生效） */
    private String presetSel = "MODERN";

    public HudLayoutScreen(Screen parent) {
        super(Component.translatable("z80zhealthbar.editor.title"));
        this.parent = parent;
    }

    private HudLayoutConfig layout() {
        return ConfigManager.getConfig().hudLayout;
    }

    /** 当前编辑的 HUD 样式（直读配置；编辑器可编辑全部样式） */
    private static HudStyle hudStyle() {
        try {
            return HudStyle.valueOf(ConfigManager.getConfig().overlay.hudStyle);
        } catch (IllegalArgumentException | NullPointerException e) {
            return HudStyle.CUSTOM;
        }
    }

    private boolean editingCustom() {
        return hudStyle() == HudStyle.CUSTOM;
    }

    private ComponentLayout sel() {
        return layout().get(baseKey(selected));
    }

    /** 子元素键（health.text / health.icon）→ 所属组件键 */
    private static String baseKey(String s) {
        if (s.endsWith(".text") || s.endsWith(".icon")) return s.substring(0, s.lastIndexOf('.'));
        return s;
    }

    private int dragStartX(String s) {
        ComponentLayout c = layout().get(baseKey(s));
        if (s.endsWith(".text")) return c.textOffsetX;
        if (s.endsWith(".icon")) return c.iconOffsetX;
        return c.offsetX;
    }

    private int dragStartY(String s) {
        ComponentLayout c = layout().get(baseKey(s));
        if (s.endsWith(".text")) return c.textOffsetY;
        if (s.endsWith(".icon")) return c.iconOffsetY;
        return c.offsetY;
    }

    private void applyDrag(String s, int x, int y) {
        ComponentLayout c = layout().get(baseKey(s));
        if (s.endsWith(".text")) {
            c.textOffsetX = Math.max(-1000, Math.min(1000, x));
            c.textOffsetY = Math.max(-1000, Math.min(1000, y));
        } else if (s.endsWith(".icon")) {
            c.iconOffsetX = Math.max(-500, Math.min(500, x));
            c.iconOffsetY = Math.max(-500, Math.min(500, y));
        } else {
            c.offsetX = x;
            c.offsetY = y;
        }
    }

    // ================= 面板模型 =================

    /** 面板行内控件（记录相对面板左缘的 x） */
    private record PW(AbstractWidget widget, int relX) {}

    /** 面板页：LIST = 清单+全局操作;EDIT = 对象参数页;ADD = 类型选择;GROUP = 分组成员;SUB_TEXT/SUB_ICON = 子组件页 */
    private enum PanelPage { LIST, EDIT, ADD, GROUP, SUB_TEXT, SUB_ICON }

    private PanelPage panelPage = PanelPage.LIST;
    /** GROUP 页当前查看的分组名 */
    private String currentGroup = "";

    // ---- 设置说明（(?) 标记：点击显示对应设置的说明文字） ----
    /** 当前显示的说明键;null = 未显示 */
    private String helpKey;
    /** (?) 命中区（每帧渲染时重建:视口外的行不记录） */
    private record HelpHit(int x, int y, int w, int h, String key) {}
    private final List<HelpHit> helpHits = new ArrayList<>();

    /** 行标签 → 说明键（.label 后缀剥离;语言文件无译文时返回 null = 不画 (?)） */
    private String tooltipKeyFor(String labelKey) {
        if (labelKey == null) return null;
        String base = labelKey.endsWith(".label")
                ? labelKey.substring(0, labelKey.length() - ".label".length()) : labelKey;
        String tk = base + ".tooltip";
        return Component.translatable(tk).getString().equals(tk) ? null : tk;
    }

    /** 设置说明浮框（底部居中;跟随 (?) 点击切换） */
    private void renderHelpBox(GuiGraphics g) {
        var lines = font.split(Component.translatable(helpKey), Math.min(330, width - 60));
        int lineH = 10, pad = 6;
        int boxW = 0;
        for (var l : lines) boxW = Math.max(boxW, font.width(l));
        int boxH = lines.size() * lineH + pad * 2;
        int bx = (width - boxW) / 2 - pad;
        int by = Math.max(4, height - 42 - boxH);
        g.fill(bx + 2, by + 2, bx + boxW + pad * 2 + 2, by + boxH + 2, 0x50000000);
        g.fill(bx, by, bx + boxW + pad * 2, by + boxH, 0xE60E1218);
        g.renderOutline(bx, by, boxW + pad * 2, boxH, 0x907FD4FF);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), bx + pad, by + pad + i * lineH, 0xFFD0D8E0);
        }
    }

    // ---- 显式保存机制（10）：进入编辑器快照,改动标脏,保存/放弃/未保存退出确认 ----
    /** 进入编辑器时的**全量配置 JSON** 快照（放弃 = 还原到此）。覆盖 hudLayout 与 overlay 两段——
     *  编辑器同时修改两者,只快照布局会让"放弃"半回滚（长条页参数被静默保留） */
    private String savedSnapshot;
    private boolean dirty;
    /** 退出确认页显示中（保存/放弃 二选一） */
    private boolean pendingUnsaved;

    private void markDirty() {
        dirty = true;
    }

    private void saveChanges() {
        ConfigManager.saveConfig();
        dirty = false;
        pendingUnsaved = false;
    }

    /** 放弃改动：全量还原到进入编辑器时的快照（实例被替换 → 面板位置需按新配置复位） */
    private void discardChanges() {
        ConfigManager.restoreJson(savedSnapshot);
        HudLayoutConfig l = layout();
        panelX = l.panelX < 0 ? width - PANEL_W - 6 : l.panelX;
        panelY = l.panelY < 0 ? 84 : l.panelY;
        dirty = false;
        pendingUnsaved = false;
    }

    /** 面板行：节标题 / 参数行（label + 值 + 控件组）/ 列表行（动态名）/ note 说明行 */
    private static final class PEntry {
        final String headerKey;
        final String labelKey;
        final Supplier<String> value;
        /** 动态标签（列表行实例名;非空时优先于 labelKey） */
        Supplier<String> labelDyn;
        /** note 说明行（按面板内宽换行绘制、按行数占高）——显式标记,不再靠 value==null 猜 */
        boolean note;
        final List<PW> widgets = new ArrayList<>();
        int relY;
        int valueRight = -1;

        PEntry(String headerKey, String labelKey, Supplier<String> value) {
            this.headerKey = headerKey;
            this.labelKey = labelKey;
            this.value = value;
        }

        /** note 说明行工厂 */
        static PEntry note(String labelKey) {
            PEntry e = new PEntry(null, labelKey, null);
            e.note = true;
            return e;
        }

        boolean isHeader() { return headerKey != null; }

        /** note 行（无值行）换行后的行数（layoutPanel 计算,渲染与占高共用） */
        int noteLines = 1;
    }

    /** 扁平小按钮：面板内的 −/+ 与循环行（比原版按钮更紧凑，贴合深色面板） */
    private final class FlatButton extends AbstractWidget {
        private final Runnable action;
        private final Supplier<Component> label;

        FlatButton(int w, int h, Supplier<Component> label, Runnable action) {
            super(0, 0, w, h, Component.empty());
            this.label = label;
            this.action = action;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int bg = isHoveredOrFocused() ? 0x90506A88 : 0x60303C4C;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0x40FFFFFF);
            Component text = label.get();
            g.drawCenteredString(font, text, getX() + getWidth() / 2,
                    getY() + (getHeight() - 8) / 2, 0xFFE6F0FF);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    // ================= 构建 =================

    @Override
    protected void init() {
        // 显式保存机制：首次 init 拍快照（rebuild 触发的重复 init 不覆盖）
        if (savedSnapshot == null) {
            savedSnapshot = ConfigManager.snapshotJson();
        }
        buildPanelEntries();
        // 面板默认右上（曾拖动过则用记录值）
        panelX = layout().panelX < 0 ? width - PANEL_W - 6 : layout().panelX;
        panelY = layout().panelY < 0 ? 84 : layout().panelY;
        layoutPanel();
        clampPanel();
        layoutPanel();
        // 屏幕上的常驻按钮已全部移除：组件选择底栏/预设按钮/重置/完成均整合进属性面板
        // （组件选择 = 点击预览元素;预设/重置/完成 = 面板动作行）
    }

    private static String nextPreset(String cur) {
        List<String> list = HudLayoutConfig.PRESETS;
        int i = list.indexOf(cur);
        return list.get((i + 1) % list.size());
    }

    private FlatButton flat(int w, int h, Supplier<Component> label, Runnable action, int x, int y) {
        FlatButton b = new FlatButton(w, h, label, action);
        b.setX(x);
        b.setY(y);
        return b;
    }

    /** 组织面板行（多级菜单）：LIST/ADD/GROUP/SUB_TEXT/SUB_ICON/EDIT 按层级路由 */
    private void buildPanelEntries() {
        panelEntries.clear();
        // 未保存确认页（全屏级）：保存 / 放弃
        if (pendingUnsaved) {
            panelEntries.add(PEntry.note("z80zhealthbar.editor.unsaved_note"));
            panelEntries.add(cycler("z80zhealthbar.editor.save",
                    () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                    () -> {
                        saveChanges();
                        ConfigManager.saveConfig();
                        if (minecraft != null) minecraft.setScreen(parent);
                    }));
            panelEntries.add(cycler("z80zhealthbar.editor.discard",
                    () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                    () -> {
                        discardChanges();
                        ConfigManager.saveConfig();
                        if (minecraft != null) minecraft.setScreen(parent);
                    }));
            return;
        }
        HudStyle st = hudStyle();
        if (st == HudStyle.VANILLA) {
            panelEntries.add(PEntry.note("z80zhealthbar.editor.vanilla_note"));
            panelEntries.add(doneRow());
            return;
        }
        boolean asteor = st == HudStyle.ASTEORBAR;
        switch (panelPage) {
            case ADD -> buildAddPage();
            case GROUP -> buildGroupPage();
            case SUB_TEXT -> buildSubText();
            case SUB_ICON -> buildSubIcon();
            case EDIT -> {
                if (asteor && selectedAsteorBar == null) { panelPage = PanelPage.LIST; buildAsteorList(); }
                else if (asteor) buildAsteorEdit();
                else if (isAtomicSelected()) buildAtomicEdit();
                else buildCustomEdit();
            }
            default -> {
                if (asteor) buildAsteorList(); else buildCustomList();
            }
        }
    }

    /** 选中键是否为原子组件（纯文本/纯图标/自由文本——编辑页即其子组件参数页） */
    private boolean isAtomicSelected() {
        // 只读探查:selected 可能是子元素键（health.text）,经 get() 会把该键插入配置 →
        // 渲染端按独立元素画出重复文本/图标,并被保存进文件（实测幽灵组件）
        String t = HudLayoutConfig.typeOf(selected, layout().peek(selected));
        // saturation_bar 走完整编辑页（有条形/文本部件）;信息类文本与原子文本/图标走子组件页
        return (t.endsWith("_text") || t.endsWith("_icon") || t.equals("text"))
                && !t.equals("saturation_bar");
    }

    /** 组件显示名：自定义名称优先,否则类型中文名 + 实例号（多实例辨识） */
    private String displayNameOf(String key) {
        ComponentLayout cc = layout().peek(key);
        if (cc != null && cc.displayName != null && !cc.displayName.isBlank()) {
            return cc.displayName;
        }
        String disp = Component.translatable("z80zhealthbar.hud.component."
                + HudLayoutConfig.baseKeyOf(key)).getString();
        int hash = key.indexOf('#');
        return hash > 0 ? disp + " #" + key.substring(hash + 1) : disp;
    }

    /** 列表行：动态实例名 + 动作按钮（文字可定制,如 调整/添加） */
    private PEntry listRow(Supplier<String> name, String btnKey, Runnable enter) {
        PEntry e = new PEntry(null, null, null);
        e.labelDyn = name;
        FlatButton b = new FlatButton(CYCLE_W, BTN_H,
                () -> Component.translatable(btnKey), enter);
        addRenderableWidget(b);
        e.widgets.add(new PW(b, PANEL_W - 4 - CYCLE_W));
        return e;
    }

    private PEntry doneRow() {
        return cycler("z80zhealthbar.editor.done",
                () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                this::onClose);
    }

    /** 返回行（单按钮,无重复标签） */
    private PEntry backRow() {
        PEntry e = new PEntry(null, null, null);
        e.labelDyn = () -> "";
        FlatButton b = new FlatButton(120, BTN_H,
                () -> Component.translatable("z80zhealthbar.editor.back_list"),
                () -> {
                    panelPage = PanelPage.LIST;
                    rebuildWidgets();
                });
        addRenderableWidget(b);
        e.widgets.add(new PW(b, PANEL_W - 4 - 120));
        return e;
    }

    /** 自定义样式 · 列表页：分组收纳 + 组件实例（含子组件行） + 全局操作 */
    private void buildCustomList() {
        var comps = ConfigManager.getConfig().hudLayout.components;
        Map<String, java.util.List<String>> groups = new LinkedHashMap<>();
        java.util.List<String> ungrouped = new java.util.ArrayList<>();
        for (String key : comps.keySet()) {
            if (key.endsWith(".text") || key.endsWith(".icon")) continue; // 子组件跟随主组件行
            ComponentLayout cc = comps.get(key);
            String g = cc == null || cc.group == null ? "" : cc.group;
            if (g.isBlank()) ungrouped.add(key);
            else groups.computeIfAbsent(g, k -> new java.util.ArrayList<>()).add(key);
        }
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.instances", null, null));
        // 分组行（组名 + 成员数 → 组页）
        for (Map.Entry<String, java.util.List<String>> g : groups.entrySet()) {
            String gn = g.getKey();
            panelEntries.add(listRow(() -> gn + " (" + groups.get(gn).size() + ")",
                    "z80zhealthbar.editor.adjust", () -> {
                        currentGroup = gn;
                        panelPage = PanelPage.GROUP;
                        rebuildWidgets();
                    }));
        }
        // 未分组组件行（已拆分的子组件缩进跟随其后）
        for (String key : ungrouped) {
            ComponentLayout mc = comps.get(key);
            String name = displayNameOf(key);
            panelEntries.add(listRow(() -> name, "z80zhealthbar.editor.adjust", () -> {
                selected = key;
                panelPage = PanelPage.EDIT;
                rebuildWidgets();
            }));
            // 子组件是否拆分由主组件锚点字段判定（拆分数据存在主组件字段,map 无独立键）;
            // 已分组的父组件其子件收纳在组页,列表不再重复显示
            boolean textDetached = mc != null && mc.showText && mc.textAnchorParsed() != null
                    && (mc.group == null || mc.group.isBlank());
            boolean iconDetached = mc != null && mc.showIcon && mc.iconAnchorParsed() != null
                    && (mc.group == null || mc.group.isBlank());
            if (textDetached) {
                String subDisp = displayNameOf(key)
                        + " · " + Component.translatable("z80zhealthbar.editor.sub_text").getString();
                String subKey = key;
                panelEntries.add(listRow(() -> "   " + subDisp, "z80zhealthbar.editor.adjust", () -> {
                    selected = subKey + ".text";
                    panelPage = PanelPage.SUB_TEXT;
                    rebuildWidgets();
                }));
            }
            if (iconDetached) {
                String subDisp = displayNameOf(key)
                        + " · " + Component.translatable("z80zhealthbar.editor.sub_icon").getString();
                String subKey = key;
                panelEntries.add(listRow(() -> "   " + subDisp, "z80zhealthbar.editor.adjust", () -> {
                    selected = subKey + ".icon";
                    panelPage = PanelPage.SUB_ICON;
                    rebuildWidgets();
                }));
            }
        }
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.actions", null, null));
        // 添加组件（进入类型选择页,全部可添加类型逐行点选）
        panelEntries.add(listRow(() -> Component.translatable("z80zhealthbar.editor.add_component").getString(),
                "z80zhealthbar.editor.add", () -> {
                    panelPage = PanelPage.ADD;
                    rebuildWidgets();
                }));
        // 预设布局（一步应用:点击 = 应用当前显示的整套设计并轮换;自建实例不清除）
        panelEntries.add(cycler("z80zhealthbar.editor.preset.apply",
                () -> Component.translatable("z80zhealthbar.editor.preset." + presetSel).getString(),
                () -> {
                    layout().applyPreset(presetSel);
                    presetSel = nextPreset(presetSel);
                    rebuildWidgets();
                }));
        panelEntries.add(confirmRow("z80zhealthbar.editor.reset_all", () -> {
            layout().resetToDefaults();
            panelPage = PanelPage.LIST;
            markDirty();
            rebuildWidgets();
        }));
        // 显式保存/放弃（改动后可见;放弃回滚到进入编辑器时）
        if (dirty) {
            panelEntries.add(cycler("z80zhealthbar.editor.save",
                    () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                    this::saveChanges));
            panelEntries.add(cycler("z80zhealthbar.editor.discard",
                    () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                    () -> {
                        discardChanges();
                        panelPage = PanelPage.LIST;
                        rebuildWidgets();
                    }));
        }
        panelEntries.add(doneRow());
    }

    /** 二段确认行（防误触）：首次点击变为"再次点击确认",3 秒内再点执行,超时还原 */
    private long confirmUntil;

    private PEntry confirmRow(String labelKey, Runnable action) {
        return cycler(labelKey, () -> System.currentTimeMillis() < confirmUntil
                        ? Component.translatable("z80zhealthbar.editor.confirm_reset").getString()
                        : Component.translatable("z80zhealthbar.editor.done").getString(),
                () -> {
                    if (System.currentTimeMillis() < confirmUntil) {
                        confirmUntil = 0;
                        action.run();
                    } else {
                        confirmUntil = System.currentTimeMillis() + 3000;
                    }
                });
    }

    /** 添加组件 · 类型选择页：全部可添加类型逐行列出,点击即创建实例并进入其编辑页 */
    private void buildAddPage() {
        panelEntries.add(backRow());
        panelEntries.add(new PEntry("z80zhealthbar.editor.pick_component", null, null));
        // 分类分组（多级菜单:状态条 / 文本 / 图标 / 信息类）
        String[][] cats = {
                {"z80zhealthbar.editor.cat_bars", "health", "food", "air", "armor", "mount", "experience",
                        "compat", "saturation_bar"},
                {"z80zhealthbar.editor.cat_texts", "health_text", "food_text", "air_text", "armor_text",
                        "mount_text", "xp_text", "saturation_text", "text"},
                {"z80zhealthbar.editor.cat_icons", "health_icon", "food_icon", "air_icon", "armor_icon", "mount_icon"},
                {"z80zhealthbar.editor.cat_info", "coords_text", "fps_text", "biome_text", "time_text"},
                {"z80zhealthbar.editor.cat_compat", "compat_saturation", "compat_exhaustion",
                        "compat_thirst", "compat_stamina"},
        };
        for (String[] cat : cats) {
            panelEntries.add(new PEntry(cat[0], null, null));
            for (int i = 1; i < cat.length; i++) {
                String type = cat[i];
                String dn = Component.translatable("z80zhealthbar.hud.component." + type).getString();
                panelEntries.add(listRow(() -> dn, "z80zhealthbar.editor.add", () -> {
                    String k = layout().addInstance(type);
                    if (k != null) {
                        selected = k;
                        panelPage = PanelPage.EDIT;
                    }
                    rebuildWidgets();
                }));
            }
        }
    }

    /** 拆分即组合：拆分子件（文本/图标锚点脱离"跟随"）时,父组件自动并入以其类型命名的组合;
     *  已在组合中的不重复建组。子件通过父件的组合收纳进组页统一管理。 */
    private void autoGroupOnDetach(ComponentLayout c) {
        boolean detached = (c.textAnchorParsed() != null) || (c.iconAnchorParsed() != null);
        if (detached && (c.group == null || c.group.isBlank())) {
            String base = HudLayoutConfig.baseKeyOf(baseKey(selected));
            String name0 = Component.translatable("z80zhealthbar.hud.component." + base).getString();
            ComponentLayout self = c;
            // 避免与其它既有组重名导致意外并组:重名时追加序号
            boolean exists = ConfigManager.getConfig().hudLayout.components.values().stream()
                    .anyMatch(cc -> cc != null && cc != self && name0.equals(cc.group));
            c.group = exists ? name0 + " 2" : name0;
        }
    }

    /** 分组 · 成员页：组重命名 + 组内组件清单（点击编辑成员）+ 解散分组 */
    private void buildGroupPage() {
        panelEntries.add(backRow());
        panelEntries.add(new PEntry(null, "z80zhealthbar.editor.selected_component",
                () -> currentGroup));
        // 组重命名（写回组内全部成员的 group 字段）
        panelEntries.add(textInput("z80zhealthbar.editor.rename_group",
                () -> currentGroup, v -> {
                    if (v.isBlank() || v.equals(currentGroup)) return;
                    for (ComponentLayout cc : ConfigManager.getConfig().hudLayout.components.values()) {
                        if (cc != null && currentGroup.equals(cc.group)) cc.group = v;
                    }
                    currentGroup = v;
                }));
        for (String key : ConfigManager.getConfig().hudLayout.components.keySet()) {
            if (key.endsWith(".text") || key.endsWith(".icon")) continue;
            ComponentLayout cc = ConfigManager.getConfig().hudLayout.components.get(key);
            if (cc == null || !currentGroup.equals(cc.group)) continue;
            panelEntries.add(listRow(() -> displayNameOf(key), "z80zhealthbar.editor.adjust", () -> {
                selected = key;
                panelPage = PanelPage.EDIT;
                rebuildWidgets();
            }));
            // 组合收纳拆分子件：文本/图标作为组合成员列出（配置在父件字段,无独立键）
            if (cc.showText && cc.textAnchorParsed() != null) {
                String td = displayNameOf(key) + " · "
                        + Component.translatable("z80zhealthbar.editor.sub_text").getString();
                String tk = key;
                panelEntries.add(listRow(() -> "   " + td, "z80zhealthbar.editor.adjust", () -> {
                    selected = tk + ".text";
                    panelPage = PanelPage.SUB_TEXT;
                    rebuildWidgets();
                }));
            }
            if (cc.showIcon && cc.iconAnchorParsed() != null) {
                String id2 = displayNameOf(key) + " · "
                        + Component.translatable("z80zhealthbar.editor.sub_icon").getString();
                String ik = key;
                panelEntries.add(listRow(() -> "   " + id2, "z80zhealthbar.editor.adjust", () -> {
                    selected = ik + ".icon";
                    panelPage = PanelPage.SUB_ICON;
                    rebuildWidgets();
                }));
            }
        }
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.actions", null, null));
        panelEntries.add(cycler("z80zhealthbar.editor.ungroup",
                () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                () -> {
                    for (ComponentLayout cc : ConfigManager.getConfig().hudLayout.components.values()) {
                        if (cc != null && currentGroup.equals(cc.group)) cc.group = "";
                    }
                    currentGroup = "";
                    panelPage = PanelPage.LIST;
                    rebuildWidgets();
                }));
        panelEntries.add(backRow());
    }

    /** 文本子组件页：主组件的文本部件参数（独立层级,互不干扰） */
    private void buildSubText() {
        ComponentLayout c = layout().get(baseKey(selected));
        panelEntries.add(backRow());
        panelEntries.add(subTitleRow("z80zhealthbar.editor.sub_text"));
        panelEntries.add(toggle("z80zhealthbar.editor.show_text", () -> c.showText, v -> c.showText = v));
        panelEntries.add(textInput("z80zhealthbar.editor.text_format",
                () -> c.textFormat == null ? "" : c.textFormat, v -> c.textFormat = v));
        panelEntries.add(cycler("z80zhealthbar.editor.text_align",
                () -> Component.translatable("z80zhealthbar.editor.align."
                        + c.textAlignParsed().name().toLowerCase(Locale.ROOT)).getString(),
                () -> {
                    var vals = HudLayoutConfig.TextAlign.values();
                    c.textAlign = vals[(c.textAlignParsed().ordinal() + 1) % vals.length].name();
                }));
        panelEntries.add(stepper("z80zhealthbar.editor.text_x",
                () -> c.textOffsetX, v -> c.textOffsetX = (int) Math.round(v), -999, 999, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.text_y",
                () -> c.textOffsetY, v -> c.textOffsetY = (int) Math.round(v), -999, 999, 1));
        panelEntries.add(cycler("z80zhealthbar.editor.text_anchor",
                () -> anchorLabel(c.textAnchor), () -> {
                    c.textAnchor = nextAnchor(c.textAnchor);
                    c.textOffsetX = 0;
                    c.textOffsetY = 0;
                    autoGroupOnDetach(c); // 拆分即组合:父组件与拆分子件自动成组
                    rebuildWidgets();
                }));
        panelEntries.add(stepper1("z80zhealthbar.editor.text_scale",
                () -> c.textScale, v -> c.textScale = v, 0.25, 3.0, 0.1));
        panelEntries.add(PEntry.note("z80zhealthbar.editor.text_split_note"));
        // ---- 动作：拆分子件可取消拆分（回跟随条）;原子文本组件可复制/删除实例 ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.actions", null, null));
        if (isAtomicSelected()) {
            panelEntries.add(cycler("z80zhealthbar.editor.duplicate_component",
                    () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                    () -> {
                        String nk = layout().duplicateInstance(baseKey(selected));
                        if (nk != null) {
                            selected = nk;
                            rebuildWidgets();
                        }
                    }));
            panelEntries.add(cycler("z80zhealthbar.editor.delete_component",
                    () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                    () -> {
                        layout().removeInstance(baseKey(selected));
                        selected = HudLayoutConfig.HEALTH;
                        panelPage = PanelPage.LIST;
                        rebuildWidgets();
                    }));
        } else {
            panelEntries.add(cycler("z80zhealthbar.editor.detach_off",
                    () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                    () -> {
                        c.textAnchor = "";
                        c.textOffsetX = 0;
                        c.textOffsetY = 0;
                        selected = baseKey(selected);
                        panelPage = PanelPage.EDIT;
                        rebuildWidgets();
                    }));
        }
        panelEntries.add(backRow());
    }

    /** 图标子组件页：主组件的图标部件参数 + 图标来源（原版/外部贴图） */
    private void buildSubIcon() {
        ComponentLayout c = layout().get(baseKey(selected));
        panelEntries.add(backRow());
        panelEntries.add(subTitleRow("z80zhealthbar.editor.sub_icon"));
        panelEntries.add(toggle("z80zhealthbar.editor.show_icon", () -> c.showIcon, v -> c.showIcon = v));
        panelEntries.add(cycler("z80zhealthbar.editor.icon_side",
                () -> Component.translatable("z80zhealthbar.editor.icon."
                        + c.iconSideParsed().name().toLowerCase(Locale.ROOT)).getString(),
                () -> {
                    var vals = HudLayoutConfig.IconSide.values();
                    c.iconSide = vals[(c.iconSideParsed().ordinal() + 1) % vals.length].name();
                }));
        panelEntries.add(cycler("z80zhealthbar.editor.icon_anchor",
                () -> anchorLabel(c.iconAnchor), () -> {
                    c.iconAnchor = nextAnchor(c.iconAnchor);
                    c.iconOffsetX = 0;
                    c.iconOffsetY = 0;
                    autoGroupOnDetach(c); // 拆分即组合:父组件与拆分子件自动成组
                    rebuildWidgets();
                }));
        panelEntries.add(stepper("z80zhealthbar.editor.icon_x",
                () -> c.iconOffsetX, v -> c.iconOffsetX = (int) Math.round(v), -500, 500, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.icon_y",
                () -> c.iconOffsetY, v -> c.iconOffsetY = (int) Math.round(v), -500, 500, 1));
        panelEntries.add(stepper1("z80zhealthbar.editor.icon_scale",
                () -> c.iconScale, v -> c.iconScale = v, 0.25, 3.0, 0.1));
        // 图标来源：原版 icons.png 或 config/z80zhealthbar/icons/ 下的外部贴图（点击轮换）
        java.util.List<String> sources = new java.util.ArrayList<>();
        sources.add("");
        sources.addAll(com.z80z99.z80zhealthbar.overlay.IconTextures.available());
        panelEntries.add(cycler("z80zhealthbar.editor.icon_source",
                () -> (c.iconTexture == null || c.iconTexture.isEmpty())
                        ? Component.translatable("z80zhealthbar.editor.icon_source_vanilla").getString()
                        : c.iconTexture,
                () -> {
                    int i = sources.indexOf(c.iconTexture == null ? "" : c.iconTexture);
                    c.iconTexture = sources.get(Math.floorMod(i + 1, sources.size()));
                }));
        panelEntries.add(PEntry.note("z80zhealthbar.editor.icon_source_note"));
        // ---- 动作：拆分子件可取消拆分;原子图标组件可复制/删除实例 ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.actions", null, null));
        if (isAtomicSelected()) {
            panelEntries.add(cycler("z80zhealthbar.editor.duplicate_component",
                    () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                    () -> {
                        String nk = layout().duplicateInstance(baseKey(selected));
                        if (nk != null) {
                            selected = nk;
                            rebuildWidgets();
                        }
                    }));
            panelEntries.add(cycler("z80zhealthbar.editor.delete_component",
                    () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                    () -> {
                        layout().removeInstance(baseKey(selected));
                        selected = HudLayoutConfig.HEALTH;
                        panelPage = PanelPage.LIST;
                        rebuildWidgets();
                    }));
        } else {
            panelEntries.add(cycler("z80zhealthbar.editor.detach_off",
                    () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                    () -> {
                        c.iconAnchor = "";
                        c.iconOffsetX = 0;
                        c.iconOffsetY = 0;
                        selected = baseKey(selected);
                        panelPage = PanelPage.EDIT;
                        rebuildWidgets();
                    }));
        }
        panelEntries.add(backRow());
    }

    /** 原子组件编辑页（纯文本/纯图标/自由文本——内容即其子部件参数） */
    private void buildAtomicEdit() {
        if (HudLayoutConfig.typeOf(selected, layout().get(selected)).endsWith("_icon")) {
            buildSubIcon();
        } else {
            buildSubText();
        }
    }

    /** 子组件页标题行（类型中文名 · 部件名） */
    private PEntry subTitleRow(String partKey) {
        String base = HudLayoutConfig.baseKeyOf(baseKey(selected));
        String disp = Component.translatable("z80zhealthbar.hud.component." + base).getString()
                + " · " + Component.translatable(partKey).getString();
        return new PEntry(null, "z80zhealthbar.editor.selected_component", () -> disp);
    }

    /** 长条样式 · 列表页：状态条清单 + 布局/显示/动态/上限全局参数 + 全局操作 */
    private void buildAsteorList() {
        var o = ConfigManager.getConfig().overlay;
        // ---- 状态条清单（点击进入该条的组件级编辑页） ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.bars", null, null));
        for (String key : BarLayouts.KEYS) {
            String dn = Component.translatable("z80zhealthbar.hud.component." + key).getString();
            panelEntries.add(listRow(() -> dn + (BarLayouts.visible(key) ? "" : " (OFF)"),
                    "z80zhealthbar.editor.adjust", () -> {
                selectedAsteorBar = key;
                panelPage = PanelPage.EDIT;
                rebuildWidgets();
            }));
        }
        // ---- 布局参数 ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.asteorbar", null, null));
        // 标签统一用 .label 键——不带后缀的键内容含 "%s"（Forge 配置界面格式化用）,直接渲染会残留 "%s";
        // 缺 .label 的键此前显示原始键名（实测"配置项翻译呢"）
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.hud.layoutStyle.label",
                () -> o.overlayLayoutStyle, v -> o.overlayLayoutStyle = (int) Math.round(v), 0, 8, 1,
                v -> v <= 0 ? "OFF" : String.valueOf((int) Math.round(v))));
        panelEntries.add(stepper("z80zhealthbar.option.overlay.cornerBarLength.label",
                () -> o.cornerBarLength, v -> o.cornerBarLength = (int) Math.round(v), 20, 400, 4));
        panelEntries.add(stepper("z80zhealthbar.option.overlay.overlayBarInnerHeight.label",
                () -> o.overlayBarInnerHeight, v -> o.overlayBarInnerHeight = (int) Math.round(v), 1, 12, 1));
        panelEntries.add(stepper("z80zhealthbar.option.overlay.overlayBarVerticalMargin.label",
                () -> o.overlayBarVerticalMargin, v -> o.overlayBarVerticalMargin = (int) Math.round(v), 0, 16, 1));
        panelEntries.add(stepper("z80zhealthbar.option.overlay.cornerHorizontalPadding.label",
                () -> o.cornerHorizontalPadding, v -> o.cornerHorizontalPadding = (int) Math.round(v), 0, 50, 1));
        panelEntries.add(stepper("z80zhealthbar.option.overlay.cornerVerticalPadding.label",
                () -> o.cornerVerticalPadding, v -> o.cornerVerticalPadding = (int) Math.round(v), 0, 50, 1));
        panelEntries.add(stepper("z80zhealthbar.option.overlay.overlayBarTextOffsetY.label",
                () -> o.overlayBarTextOffsetY, v -> o.overlayBarTextOffsetY = (int) Math.round(v), -50, 50, 1));
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.overlay.overlayTextScale.label",
                () -> o.overlayTextScale, v -> o.overlayTextScale = v, 0.25, 4.0, 0.05,
                v -> Math.round(v * 100) + "%", v -> v / 100.0));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.mountHealthOnLeftSide.label",
                () -> o.mountHealthOnLeftSide, v -> o.mountHealthOnLeftSide = v));
        // 自由摆放提示：预览中直接拖拽任意条/图标/数值文本即可调整;单条的组件级参数点列表进入其编辑页
        panelEntries.add(PEntry.note("z80zhealthbar.editor.asteor.note"));
        // ---- 显示开关 ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.asteor_display", null, null));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.displayHealthText",
                () -> o.displayHealthText, v -> o.displayHealthText = v));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.displayFoodText",
                () -> o.displayFoodText, v -> o.displayFoodText = v));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.displaySaturation",
                () -> o.displaySaturation, v -> o.displaySaturation = v));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.displayExperienceLevel",
                () -> o.displayExperienceLevel, v -> o.displayExperienceLevel = v));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.displayExperienceProgress",
                () -> o.displayExperienceProgress, v -> o.displayExperienceProgress = v));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.displayArmorToughness",
                () -> o.displayArmorToughness, v -> o.displayArmorToughness = v));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.overwriteVanillaArmorBar",
                () -> o.overwriteVanillaArmorBar, v -> o.overwriteVanillaArmorBar = v));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.overwriteVanillaExperienceBar",
                () -> o.overwriteVanillaExperienceBar, v -> o.overwriteVanillaExperienceBar = v));
        // ---- 动态与闪烁 ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.asteor_dynamic", null, null));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.enableHealthBlink",
                () -> o.enableHealthBlink, v -> o.enableHealthBlink = v));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.enableFoodBlink",
                () -> o.enableFoodBlink, v -> o.enableFoodBlink = v));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.shakeHealthAndFoodWhileLow",
                () -> o.shakeHealthAndFoodWhileLow, v -> o.shakeHealthAndFoodWhileLow = v));
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.overlay.lowHealthRate.label",
                () -> o.lowHealthRate, v -> o.lowHealthRate = v, 0.05, 0.95, 0.01,
                v -> Math.round(v * 100) + "%", v -> v / 100.0));
        panelEntries.add(cycler("z80zhealthbar.option.overlay.absorptionMode.label",
                () -> Component.translatable("z80zhealthbar.overlay.absorptionMode."
                        + Math.min(1, Math.max(0, o.absorptionMode))).getString(),
                () -> o.absorptionMode = (o.absorptionMode + 1) % 2));
        panelEntries.add(stepper("z80zhealthbar.option.overlay.hideUnchangingBarAfterSeconds.label",
                () -> o.hideUnchangingBarAfterSeconds, v -> o.hideUnchangingBarAfterSeconds = (int) Math.round(v),
                0, 600, 5));
        // ---- 数值上限（0 = 跟随实际值;长条/自定义两样式共用） ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.asteor_caps", null, null));
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.overlay.fullHealthValue.label",
                () -> o.fullHealthValue, v -> o.fullHealthValue = (int) Math.round(v), 0, 100000, 1,
                v -> v <= 0 ? "—" : String.valueOf((int) Math.round(v))));
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.overlay.fullFoodLevelValue.label",
                () -> o.fullFoodLevelValue, v -> o.fullFoodLevelValue = (int) Math.round(v), 0, 40, 1,
                v -> v <= 0 ? "—" : String.valueOf((int) Math.round(v))));
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.overlay.fullArmorValue.label",
                () -> o.fullArmorValue, v -> o.fullArmorValue = (int) Math.round(v), 0, 100, 1,
                v -> v <= 0 ? "—" : String.valueOf((int) Math.round(v))));
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.overlay.fullSaturationValue.label",
                () -> o.fullSaturationValue, v -> o.fullSaturationValue = v, 0, 40, 0.5,
                v -> v <= 0 ? "—" : String.format(java.util.Locale.ROOT, "%.1f", v)));
        // ---- 全局操作 ----
        panelEntries.add(confirmRow("z80zhealthbar.editor.asteor.bar_all",
                () -> {
                    BarLayouts.resetAll();
                    rebuildWidgets();
                }));
        panelEntries.add(doneRow());
    }

    /** 长条样式 · 编辑页：选中条的组件级参数（多级菜单第二级） */
    private void buildAsteorEdit() {
        var p = BarLayouts.get(selectedAsteorBar);
        panelEntries.add(backRow());
        // 标题：条名
        panelEntries.add(new PEntry(null, "z80zhealthbar.editor.selected_component",
                () -> Component.translatable("z80zhealthbar.hud.component." + selectedAsteorBar).getString()));
        panelEntries.add(toggle("z80zhealthbar.editor.asteor.visible",
                () -> p.visible, v -> p.visible = v));
        panelEntries.add(toggle("z80zhealthbar.editor.asteor.free",
                () -> p.free, v -> p.free = v));
        panelEntries.add(stepper("z80zhealthbar.editor.asteor.x",
                () -> p.x, v -> p.x = (int) Math.round(v), 0, 2000, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.asteor.y",
                () -> p.y, v -> p.y = (int) Math.round(v), 0, 2000, 1));
        panelEntries.add(toggle("z80zhealthbar.editor.asteor.icon",
                () -> p.showIcon, v -> p.showIcon = v));
        // 饱和度显示方式（仅饱食度条;0=覆盖 1=右侧追加 2=顶部细条 3=底部细条 4=关闭）
        if ("food".equals(selectedAsteorBar)) {
            panelEntries.add(cycler("z80zhealthbar.editor.sat_mode",
                    () -> Component.translatable("z80zhealthbar.editor.sat_mode."
                            + Math.max(0, Math.min(4, p.saturationMode))).getString(),
                    () -> p.saturationMode = (p.saturationMode + 1) % 5));
        }
        panelEntries.add(stepper("z80zhealthbar.editor.asteor.icon_x",
                () -> p.iconOffX, v -> p.iconOffX = (int) Math.round(v), -500, 500, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.asteor.icon_y",
                () -> p.iconOffY, v -> p.iconOffY = (int) Math.round(v), -500, 500, 1));
        panelEntries.add(toggle("z80zhealthbar.editor.asteor.text",
                () -> p.showText, v -> p.showText = v));
        panelEntries.add(stepper("z80zhealthbar.editor.asteor.text_x",
                () -> p.textOffX, v -> p.textOffX = (int) Math.round(v), -500, 500, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.asteor.text_y",
                () -> p.textOffY, v -> p.textOffY = (int) Math.round(v), -500, 500, 1));
        // ---- 动作 ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.actions", null, null));
        panelEntries.add(cycler("z80zhealthbar.editor.asteor.bar_one",
                () -> Component.translatable("z80zhealthbar.editor.asteor.reset").getString(),
                () -> {
                    BarLayouts.reset(selectedAsteorBar);
                    rebuildWidgets();
                }));
        panelEntries.add(backRow());
    }

    /** 自定义样式 · 编辑页：选中组件的全部参数（多级菜单第二级） */
    private void buildCustomEdit() {
        ComponentLayout c = sel();
        panelEntries.add(backRow());
        // 组件标题：自定义名称优先,否则类型中文名 + 实例号（多实例辨识）
        panelEntries.add(new PEntry(null, "z80zhealthbar.editor.selected_component", () -> displayNameOf(selected)));
        // 自定义名称（空 = 类型默认名;列表/标题/框选标签均优先显示）
        panelEntries.add(textInput("z80zhealthbar.editor.display_name",
                () -> c.displayName == null ? "" : c.displayName, v -> c.displayName = v));
        // 组件显示形式:长条/图标/关闭(作用于当前选中组件)
        panelEntries.add(cycler("z80zhealthbar.editor.component_mode",
                () -> Component.translatable("z80zhealthbar.editor.mode."
                        + c.modeParsed().name().toLowerCase(Locale.ROOT)).getString(),
                () -> {
                    var vals = HudLayoutConfig.ComponentMode.values();
                    c.mode = vals[(c.modeParsed().ordinal() + 1) % vals.length].name();
                    rebuildWidgets();
                }));

        // ---- 组件（位置/缩放/间距:所有显示形式通用;原先散在底栏,现集中入面板） ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.component", null, null));
        if (c.modeParsed() == HudLayoutConfig.ComponentMode.BAR) {
            // 三部件独立开关（自由组合:只留图标/只留文本/条+图标+文本）
            panelEntries.add(toggle("z80zhealthbar.editor.show_bar",
                    () -> c.showBar, v -> c.showBar = v));
            panelEntries.add(toggle("z80zhealthbar.editor.show_icon",
                    () -> c.showIcon, v -> c.showIcon = v));
        }
        panelEntries.add(toggle("z80zhealthbar.editor.stack",
                () -> c.stack, v -> c.stack = v));
        // 饱和度显示方式（仅饱食度组件;0=覆盖 1=右侧追加 2=顶部细条 3=底部细条 4=关闭）
        if (HudLayoutConfig.typeOf(selected, c).equals(HudLayoutConfig.FOOD)) {
            panelEntries.add(cycler("z80zhealthbar.editor.sat_mode",
                    () -> Component.translatable("z80zhealthbar.editor.sat_mode."
                            + Math.max(0, Math.min(4, c.saturationMode))).getString(),
                    () -> c.saturationMode = (c.saturationMode + 1) % 5));
        }
        panelEntries.add(cycler("z80zhealthbar.editor.anchor",
                () -> Component.translatable("z80zhealthbar.editor.anchor."
                        + c.anchorParsed().name().toLowerCase(Locale.ROOT)).getString(),
                () -> {
                    c.anchor = nextHudAnchor(c.anchorParsed()).name();
                    c.offsetX = 0;
                    c.offsetY = 0;
                    rebuildWidgets();
                }));
        panelEntries.add(stepper("z80zhealthbar.editor.pos_x",
                () -> c.offsetX, v -> c.offsetX = (int) Math.round(v), -999, 999, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.pos_y",
                () -> c.offsetY, v -> c.offsetY = (int) Math.round(v), -999, 999, 1));
        panelEntries.add(stepper1("z80zhealthbar.editor.component_scale",
                () -> c.scale, v -> c.scale = v, 0.5, 2.0, 0.1));
        panelEntries.add(stepper("z80zhealthbar.editor.spacing",
                () -> c.spacing, v -> c.spacing = (int) Math.round(v), 0, 12, 1));
        // 组件透明度（0..100）/ 动态 HUD（无变化淡出,0=关）/ 旋转角度（绕组件中心）
        panelEntries.add(stepper("z80zhealthbar.editor.opacity",
                () -> c.opacity, v -> c.opacity = (int) Math.round(v), 0, 100, 5));
        panelEntries.add(stepper("z80zhealthbar.editor.idle_fade",
                () -> c.idleFadeSecs, v -> c.idleFadeSecs = (int) Math.round(v), 0, 60, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.rotation",
                () -> c.rotation, v -> c.rotation = (int) Math.round(v), -180, 180, 5));

        if (c.modeParsed() == HudLayoutConfig.ComponentMode.OFF) {
            panelEntries.add(PEntry.note("z80zhealthbar.editor.off_mode_note"));
            return;
        }
        if (c.modeParsed() != HudLayoutConfig.ComponentMode.BAR) {
            panelEntries.add(PEntry.note("z80zhealthbar.editor.icon_mode_note"));
            return;
        }

        // ---- 条形 ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.bar", null, null));
        panelEntries.add(stepper("z80zhealthbar.editor.bar_width",
                () -> c.barWidth, v -> c.barWidth = (int) Math.round(v), 40, 400, 4));
        panelEntries.add(stepper("z80zhealthbar.editor.bar_height",
                () -> c.barHeight, v -> c.barHeight = (int) Math.round(v), 5, 16, 1));

        // ---- 子组件入口（文本/图标各一个独立参数页,与主组件互不干扰） ----
        // 类型过滤：无该部件的类型不显示入口（如经验无状态图标,compat 无文本无图标）
        String selType = HudLayoutConfig.typeOf(selected, c);
        if (selType.equals(HudLayoutConfig.COMPAT) || selType.startsWith("compat_")) {
            // compat 特判：多行动态行的文本部件参数（开关/模板/对齐/偏移/缩放;
            // 行间距由上方"间距"行控制,渲染端已接入）
            panelEntries.add(toggle("z80zhealthbar.editor.show_text",
                    () -> c.showText, v -> c.showText = v));
            panelEntries.add(textInput("z80zhealthbar.editor.text_format",
                    () -> c.textFormat == null ? "" : c.textFormat, v -> c.textFormat = v));
            panelEntries.add(PEntry.note("z80zhealthbar.editor.compat_fmt_note"));
            if (c.showText) {
                panelEntries.add(cycler("z80zhealthbar.editor.text_align",
                        () -> Component.translatable("z80zhealthbar.editor.align."
                                + c.textAlignParsed().name().toLowerCase(Locale.ROOT)).getString(),
                        () -> {
                            var vals = HudLayoutConfig.TextAlign.values();
                            c.textAlign = vals[(c.textAlignParsed().ordinal() + 1) % vals.length].name();
                        }));
                panelEntries.add(stepper("z80zhealthbar.editor.text_x",
                        () -> c.textOffsetX, v -> c.textOffsetX = (int) Math.round(v), -999, 999, 1));
                panelEntries.add(stepper("z80zhealthbar.editor.text_y",
                        () -> c.textOffsetY, v -> c.textOffsetY = (int) Math.round(v), -999, 999, 1));
                panelEntries.add(stepper1("z80zhealthbar.editor.text_scale",
                        () -> c.textScale, v -> c.textScale = v, 0.25, 3.0, 0.1));
            }
            // 动作（复制/删除/重置/分组）：此前 compat 分支提前 return,添加的 compat_* 组件
            // 在编辑器内无法删除/复制（实测"加上去就撤不掉",只能手改 JSON）
            addComponentActions(selected);
            panelEntries.add(groupToggleRow(c));
            panelEntries.add(backRow());
            return;
        }
        if (!selType.equals(HudLayoutConfig.COMPAT)) {
            panelEntries.add(listRow(() -> Component.translatable("z80zhealthbar.editor.sub_text").getString()
                            + (c.textAnchorParsed() != null
                            ? " · " + Component.translatable("z80zhealthbar.editor.sub_detached").getString() : ""),
                    "z80zhealthbar.editor.adjust", () -> {
                        selected = baseKey(selected) + ".text";
                        panelPage = PanelPage.SUB_TEXT;
                        rebuildWidgets();
                    }));
        }
        boolean typeHasIcon = selType.equals(HudLayoutConfig.HEALTH) || selType.equals(HudLayoutConfig.FOOD)
                || selType.equals(HudLayoutConfig.AIR) || selType.equals(HudLayoutConfig.ARMOR)
                || selType.equals(HudLayoutConfig.MOUNT);
        if (typeHasIcon) {
            panelEntries.add(listRow(() -> Component.translatable("z80zhealthbar.editor.sub_icon").getString()
                            + (c.iconAnchorParsed() != null
                            ? " · " + Component.translatable("z80zhealthbar.editor.sub_detached").getString() : ""),
                    "z80zhealthbar.editor.adjust", () -> {
                        selected = baseKey(selected) + ".icon";
                        panelPage = PanelPage.SUB_ICON;
                        rebuildWidgets();
                    }));
        }
        // ---- 动作（复制/删除/重置/分组;全局动作在列表页） ----
        addComponentActions(baseKey(selected));
        panelEntries.add(groupToggleRow(c));
        panelEntries.add(backRow());
    }

    /** 组件动作行（复制/删除/重置）：自定义编辑页与 compat 编辑页共用 */
    private void addComponentActions(String key) {
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.actions", null, null));
        panelEntries.add(cycler("z80zhealthbar.editor.duplicate_component",
                () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                () -> {
                    String nk = layout().duplicateInstance(key);
                    if (nk != null) {
                        selected = nk;
                        rebuildWidgets();
                    }
                }));
        panelEntries.add(cycler("z80zhealthbar.editor.delete_component",
                () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                () -> {
                    layout().removeInstance(key);
                    selected = HudLayoutConfig.HEALTH;
                    panelPage = PanelPage.LIST;
                    rebuildWidgets();
                }));
        panelEntries.add(cycler("z80zhealthbar.editor.reset_component",
                () -> Component.translatable("z80zhealthbar.editor.done").getString(),
                () -> layout().resetComponent(key)));
    }

    /** 分组开关行：未分组 → 并入既有组/新建"组1";已分组 → 移出 */
    private PEntry groupToggleRow(ComponentLayout c) {
        return cycler("z80zhealthbar.editor.toggle_group",
                () -> (c.group == null || c.group.isBlank())
                        ? Component.translatable("z80zhealthbar.editor.join_group").getString()
                        : Component.translatable("z80zhealthbar.editor.leave_group").getString(),
                () -> {
                    if (c.group == null || c.group.isBlank()) {
                        // 新成员并入含多个组件的既有组需先在组页操作;此处直接建组/加入唯一组
                        String existing = ConfigManager.getConfig().hudLayout.components.values().stream()
                                .map(cc -> cc == null ? "" : cc.group)
                                .filter(g -> g != null && !g.isBlank()).findFirst().orElse("");
                        c.group = existing.isBlank() ? "组1" : existing;
                    } else {
                        c.group = "";
                    }
                    panelPage = PanelPage.LIST;
                    rebuildWidgets();
                });
    }

    /** HudAnchor 循环下一项（面板 cycler 用；底栏 CycleButton 移除后的替代） */
    private static HudAnchor nextHudAnchor(HudAnchor cur) {
        HudAnchor[] vals = HudAnchor.values();
        return vals[(cur.ordinal() + 1) % vals.length];
    }

    private String anchorLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.translatable("z80zhealthbar.editor.anchor.follow").getString();
        }
        return Component.translatable("z80zhealthbar.editor.anchor." + raw.toLowerCase(Locale.ROOT)).getString();
    }

    private static String nextAnchor(String raw) {
        HudAnchor[] vals = HudAnchor.values();
        String[] all = new String[vals.length + 1];
        all[0] = "";
        for (int i = 0; i < vals.length; i++) all[i + 1] = vals[i].name();
        int cur = 0;
        String r = raw == null ? "" : raw;
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(r)) { cur = i; break; }
        }
        return all[(cur + 1) % all.length];
    }

    /** 整数参数行：标签 + 值 + [−][+] */
    private PEntry stepper(String labelKey, IntSupplier get, DoubleConsumer set,
                           int min, int max, int step) {
        return stepper1(labelKey, () -> (double) get.getAsInt(), set, min, max, step);
    }

    /** 数值参数行（double）：标签 + 值 + [−][+] */
    private PEntry stepper1(String labelKey, DoubleSupplier get, DoubleConsumer set,
                            double min, double max, double step) {
        return stepper1Fmt(labelKey, get, set, min, max, step, v -> step < 1
                ? String.format(Locale.ROOT, "%.1f", v)
                : String.valueOf((int) Math.round(v)));
    }

    /** 数值参数行（自定义显示格式，如 OFF / 百分比）。值区域为可编辑文本框（输入回车/失焦写回,± 步进保留） */
    private PEntry stepper1Fmt(String labelKey, DoubleSupplier get, DoubleConsumer set,
                               double min, double max, double step, DoubleFunction<String> fmt) {
        return stepper1Fmt(labelKey, get, set, min, max, step, fmt, v -> v);
    }

    /**
     * 数值参数行（自定义显示格式 + 显示→存储解析）。
     *
     * @param parse 文本数值（已去 %）→ 存储值。百分比行的显示为"105%",存储为 1.05——
     *              此前两者同义,± 一次即把 105 当 105 写回并被钳到上限（实测"点一下蹦到最大"）
     */
    private PEntry stepper1Fmt(String labelKey, DoubleSupplier get, DoubleConsumer set,
                               double min, double max, double step, DoubleFunction<String> fmt,
                               DoubleFunction<Double> parse) {
        PEntry e = new PEntry(null, labelKey, null);
        int minusX = PANEL_W - 4 - STEP_W * 2 - 2;
        int plusX = PANEL_W - 4 - STEP_W;
        // 文本框放数值区（标签右侧到 − 按钮前）——此前从标签处 x=6 起,深色框直接盖住行标签
        int boxX = CONTROL_LEFT;
        int boxW = Math.max(28, minusX - 4 - boxX);
        EditBox box = new EditBox(font, 0, 0, boxW, BTN_H, Component.translatable(labelKey));
        box.setMaxLength(24);
        // 过滤器需放行"当前显示串":OFF/— 等占位文本必须能被程序化回显——
        // EditBox.setValue 过滤失败即整体忽略,步进后数值区会停在旧值（实测 ± 不回显）
        String[] displayNow = {fmt.apply(get.getAsDouble())};
        box.setFilter(s -> s.matches("[\\-0-9.%]*") || s.equals(displayNow[0]));
        box.setValue(displayNow[0]);
        box.setResponder(s -> {
            if (s.isEmpty()) return; // 清空/中间态不落盘
            try {
                double v = parse.apply(s.endsWith("%") ? Double.parseDouble(s.substring(0, s.length() - 1))
                        : Double.parseDouble(s));
                set.accept(Math.max(min, Math.min(max, v)));
                if (box.isFocused()) markDirty(); // 键入即标脏;± 点击由控件路径标脏
            } catch (NumberFormatException ignored) {
                // 非法输入（"-"/"."/占位串 等）不落盘,保留原值
            }
        });
        addRenderableWidget(box);
        e.widgets.add(new PW(box, boxX));
        FlatButton minus = new FlatButton(STEP_W, BTN_H, () -> Component.literal("\u2212"), () -> {
            set.accept(Math.max(min, roundStep(get.getAsDouble() - step, step)));
            displayNow[0] = fmt.apply(get.getAsDouble());
            box.setValue(displayNow[0]);
        });
        FlatButton plus = new FlatButton(STEP_W, BTN_H, () -> Component.literal("+"), () -> {
            set.accept(Math.min(max, roundStep(get.getAsDouble() + step, step)));
            displayNow[0] = fmt.apply(get.getAsDouble());
            box.setValue(displayNow[0]);
        });
        addRenderableWidget(minus);
        addRenderableWidget(plus);
        e.widgets.add(new PW(minus, minusX));
        e.widgets.add(new PW(plus, plusX));
        return e;
    }

    private static double roundStep(double v, double step) {
        if (step >= 1) return Math.rint(v);
        return Math.round(v * 100) / 100.0;
    }

    /** 循环参数行：标签 + [当前值] 按钮（点击轮换） */
    private PEntry cycler(String labelKey, Supplier<String> valueText, Runnable next) {
        PEntry e = new PEntry(null, labelKey, valueText);
        int x = PANEL_W - 4 - CYCLE_W;
        e.valueRight = -1; // 值显示在按钮内
        FlatButton cycle = new FlatButton(CYCLE_W, BTN_H, () -> Component.literal(valueText.get()), next);
        addRenderableWidget(cycle);
        e.widgets.add(new PW(cycle, x));
        return e;
    }

    /** 布尔参数行：标签 + [开/关] 按钮 */
    private PEntry toggle(String labelKey, Supplier<Boolean> get, Consumer<Boolean> set) {
        return cycler(labelKey, () -> Component.translatable(
                        get.get() ? "z80zhealthbar.msg.on" : "z80zhealthbar.msg.off").getString(),
                () -> set.accept(!get.get()));
    }

    /** 文本输入行：标签 + EditBox（实时写回;文本模板等字符串配置用） */
    private PEntry textInput(String labelKey, Supplier<String> get, Consumer<String> set) {
        PEntry e = new PEntry(null, labelKey, null);
        int w = 92;
        EditBox box = new EditBox(font, 0, 0, w, 14, Component.translatable(labelKey));
        box.setMaxLength(160);
        box.setValue(get.get());
        box.setResponder(v -> {
            set.accept(v);
            if (box.isFocused()) markDirty(); // 键入即标脏（构建期的程序化 setValue 不标）
        });
        addRenderableWidget(box);
        e.widgets.add(new PW(box, PANEL_W - 4 - w));
        return e;
    }

    /** 按面板原点更新全部控件位置，并计算面板高度（内容超高时启用滚动,行按滚动物画出裁剪） */
    private void layoutPanel() {
        // 1) 内容坐标（不含滚动）——同时得出内容总高。
        //    note 行（无值无节标题）按面板内宽换行,行数决定占高——此前固定单行高,长文案溢出面板
        int cursor = TITLE_H + 4;
        for (PEntry e : panelEntries) {
            if (e.isHeader()) {
                e.relY = cursor + 2;
                cursor += HEADER_H;
            } else if (e.note) {
                e.noteLines = Math.max(1, font.split(Component.translatable(e.labelKey), PANEL_W - 12).size());
                e.relY = cursor;
                cursor += e.noteLines * 10 + 6;
            } else {
                e.relY = cursor;
                cursor += ROW_H + ROW_GAP;
            }
        }
        panelContentH = cursor + 2;

        // 2) 可视高度：面板不超过底栏上缘；内容不足时面板收缩贴合
        int bodyH = Math.min(panelContentH - TITLE_H - 2, height - 28 - panelY - TITLE_H - 4);
        bodyH = Math.max(bodyH, TITLE_H);
        panelH = TITLE_H + 2 + bodyH;

        // 3) 滚动钳制 + 平滑逼近（与设置页滚动同一手感）
        int maxScroll = Math.max(0, panelContentH - panelH);
        panelScrollTarget = Math.max(0, Math.min(maxScroll, panelScrollTarget));
        panelScroll += (panelScrollTarget - panelScroll) * 0.35f;
        if (Math.abs(panelScrollTarget - panelScroll) < 0.5f) panelScroll = panelScrollTarget;
        int scroll = Math.round(panelScroll);

        // 4) 控件定位 + 视口裁剪（整行露出才可见,避免半行按钮可点）
        for (PEntry e : panelEntries) {
            if (e.isHeader()) continue;
            int y = panelY + e.relY - scroll;
            boolean inView = y >= panelY + TITLE_H + 2 && y + ROW_H <= panelY + panelH - 1;
            for (PW pw : e.widgets) {
                pw.widget().setX(panelX + pw.relX());
                pw.widget().setY(y + (ROW_H - pw.widget().getHeight()) / 2);
                pw.widget().visible = inView;
            }
        }
    }

    private void clampPanel() {
        panelX = Math.max(2, Math.min(panelX, Math.max(2, width - PANEL_W - 2)));
        panelY = Math.max(2, Math.min(panelY, Math.max(2, height - 28 - panelH)));
    }

    // ================= 渲染 =================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HudStyle st = hudStyle();
        layoutPanel(); // 每帧推进面板平滑滚动（内容超高时）

        // 1) 预览 = 实际渲染路径（自定义也用模拟战斗数据驱动——真实玩家数据在创造模式下
        //    全为 0/空,经验等级数字等组件会"看似不渲染"）
        if (st == HudStyle.CUSTOM) {
            com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.update(System.currentTimeMillis());
            com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active = true;
            try {
                CustomHudRenderer.render(graphics, partialTick);
            } finally {
                com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active = false;
            }
        } else if (st == HudStyle.ASTEORBAR) {
            renderAsteorPreview(graphics, partialTick);
        }

        // 2) 对齐辅助线（三分线；仅自定义样式下有意义）
        if (st == HudStyle.CUSTOM) {
            int c = 0x30FFFFFF;
            graphics.fill(width / 2, 0, width / 2 + 1, height, c);
            graphics.fill(0, height / 2, width, height / 2 + 1, c);
            graphics.fill(width / 3, 0, width / 3 + 1, height, 0x18FFFFFF);
            graphics.fill(width * 2 / 3, 0, width * 2 / 3 + 1, height, 0x18FFFFFF);
        }

        // 3) 元素框 + 选中高亮（自定义 = 测量求解;长条 = 渲染时记录的矩形）
        if (st == HudStyle.CUSTOM) {
            Map<String, int[]> sizes = CustomHudRenderer.measureAll(layout(), minecraft == null ? null : minecraft.player);
            Map<String, HudLayoutSolver.Box> boxes = HudLayoutSolver.solve(layout(), sizes, width, height);
            for (Map.Entry<String, HudLayoutSolver.Box> e : boxes.entrySet()) {
                HudLayoutSolver.Box box = e.getValue();
                boolean isSel = e.getKey().equals(selected);
                int color = isSel ? 0x8040FF40 : 0x50FFFFFF;
                graphics.renderOutline(box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, color);
                if (isSel) {
                    String suffix = e.getKey().endsWith(".text")
                            ? Component.translatable("z80zhealthbar.editor.suffix_text").getString()
                            : e.getKey().endsWith(".icon")
                            ? Component.translatable("z80zhealthbar.editor.suffix_icon").getString() : "";
                    graphics.drawCenteredString(font,
                            Component.translatable("z80zhealthbar.hud.component."
                                    + HudLayoutConfig.baseKeyOf(baseKey(e.getKey()))).getString() + suffix,
                            box.x() + box.width() / 2, box.y() - 10, 0xFF40FF40);
                }
            }
        } else if (st == HudStyle.ASTEORBAR) {
            // 长条样式：预览渲染时各条/图标记录了矩形,直接框选（未渲染的条如坐骑无框）
            for (String key : BarLayouts.KEYS) {
                int[] r = BarLayouts.lastRect(key);
                if (r != null) {
                    boolean isSel = key.equals(selectedAsteorBar);
                    graphics.renderOutline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2,
                            isSel ? 0x8040FF40 : 0x50FFFFFF);
                    if (isSel) {
                        graphics.drawCenteredString(font,
                                Component.translatable("z80zhealthbar.hud.component." + key).getString(),
                                r[0] + r[2] / 2, r[1] - 10, 0xFF40FF40);
                    }
                }
                int[] ir = BarLayouts.lastIconRect(key);
                if (ir != null) {
                    boolean iconSel = key.equals(selectedAsteorBar);
                    graphics.renderOutline(ir[0] - 1, ir[1] - 1, ir[2] + 2, ir[3] + 2,
                            iconSel ? 0x8040FF40 : 0x30FFFFFF);
                }
                int[] tr = BarLayouts.lastTextRect(key);
                if (tr != null) {
                    boolean textSel = key.equals(selectedAsteorBar);
                    graphics.renderOutline(tr[0] - 1, tr[1] - 1, tr[2] + 2, tr[3] + 2,
                            textSel ? 0x8040FF40 : 0x30FFFFFF);
                }
            }
        }

        // 4) 悬浮面板底板（深色 + 青色强调 + 投影）
        renderPanelChrome(graphics);

        // 5) 控件（含面板行 / 底栏 / 重置按钮）
        super.render(graphics, mouseX, mouseY, partialTick);

        // 6) 面板文本（标签/值/节标题；控件之上补文字）
        renderPanelTexts(graphics, mouseX, mouseY);
        // 设置说明浮框（点击 (?) 后显示,点击别处/再点同处/Esc 关闭）
        if (helpKey != null) renderHelpBox(graphics);

        graphics.drawCenteredString(font, getTitle(), width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("z80zhealthbar.editor.hint"),
                width / 2, height - 34, 0xFFCFCFCF);
    }

    /** 长条样式预览：走游戏内实渲染路径（临时覆写样式为 ASTEORBAR,随后还原）;
     *  模拟战斗驱动数据（氧气/吸收等按循环演示）,渲染时各条记录矩形供框选/拖拽 */
    private void renderAsteorPreview(GuiGraphics graphics, float partialTick) {
        if (minecraft == null || minecraft.player == null) return;
        BarLayouts.clearRects();
        com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.update(System.currentTimeMillis());
        com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active = true;
        HudRenderer.setStyleOverride(HudStyle.ASTEORBAR);
        try {
            HudRenderer.onPreRender(minecraft.gui);
            HudRenderer.render(graphics, partialTick);
        } finally {
            HudRenderer.setStyleOverride(null);
            com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active = false;
        }
    }

    /** 面板底板：投影 + 深色底 + 标题栏 + 青色描边 + 节分隔线 */
    private void renderPanelChrome(GuiGraphics g) {
        g.fill(panelX + 3, panelY + 3, panelX + PANEL_W + 3, panelY + panelH + 3, 0x50000000);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xE60E1218);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + TITLE_H, 0xFF1B2836);
        g.fill(panelX, panelY + TITLE_H - 1, panelX + PANEL_W, panelY + TITLE_H, 0x907FD4FF);
        g.renderOutline(panelX, panelY, PANEL_W, panelH, 0x507FD4FF);

        g.drawString(font, "\u2261 " + Component.translatable("z80zhealthbar.editor.panel.title").getString(),
                panelX + 5, panelY + 3, 0xFFCFE6FF);
        String drag = Component.translatable("z80zhealthbar.editor.panel.drag").getString();
        g.drawString(font, drag, panelX + PANEL_W - 5 - font.width(drag), panelY + 3, 0x8088A0B8);

        int scroll = Math.round(panelScroll);
        for (PEntry e : panelEntries) {
            if (!e.isHeader()) continue;
            int y = panelY + e.relY - scroll;
            if (y < panelY + TITLE_H + 2 || y + HEADER_H > panelY + panelH) continue; // 视口外
            g.fill(panelX + 4, y + HEADER_H - 3, panelX + PANEL_W - 4,
                    y + HEADER_H - 2, 0x20FFFFFF);
        }

        // 内容超高时画滚动条（右缘 2px 轨道 + 青色滑块）
        int maxScroll = Math.max(0, panelContentH - panelH);
        if (maxScroll > 0) {
            int trackX = panelX + PANEL_W - 4;
            int trackY0 = panelY + TITLE_H + 2, trackY1 = panelY + panelH - 3;
            int bodyView = Math.max(1, trackY1 - trackY0);
            int thumbH = Math.max(10, bodyView * panelH / panelContentH);
            thumbH = Math.min(thumbH, bodyView);
            int thumbY = trackY0 + (bodyView - thumbH) * (int) panelScroll / maxScroll;
            g.fill(trackX, trackY0, trackX + 2, trackY1, 0x30FFFFFF);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x907FD4FF);
        }
    }

    /** 面板滚动条命中测试（拖拽用;false = 无滚动条或不在轨道上） */
    private boolean panelScrollbarHit(double mx, double my) {
        int maxScroll = Math.max(0, panelContentH - panelH);
        if (maxScroll <= 0) return false;
        return mx >= panelX + PANEL_W - 7 && mx <= panelX + PANEL_W
                && my >= panelY + TITLE_H && my <= panelY + panelH;
    }

    /** 面板滚动条拖拽定位：把滚动中心对到鼠标 y */
    private void panelScrollbarDrag(double my) {
        int trackY0 = panelY + TITLE_H + 2, trackY1 = panelY + panelH - 3;
        int bodyView = Math.max(1, trackY1 - trackY0);
        int thumbH = Math.min(bodyView, Math.max(10, bodyView * panelH / panelContentH));
        int maxScroll = Math.max(0, panelContentH - panelH);
        double frac = (my - trackY0 - thumbH / 2.0) / Math.max(1, bodyView - thumbH);
        panelScrollTarget = (float) Math.max(0, Math.min(maxScroll, frac * maxScroll));
        layoutPanel();
    }

    /** 面板文本：节标题 / 行标签 / 行值（右对齐到控件左侧；随滚动位移,视口外不画）;参数行标签后画 (?) 说明标记 */
    private void renderPanelTexts(GuiGraphics g, int mouseX, int mouseY) {
        helpHits.clear();
        int scroll = Math.round(panelScroll);
        for (PEntry e : panelEntries) {
            int y = panelY + e.relY - scroll;
            if (e.isHeader()) {
                if (y >= panelY + TITLE_H + 2 && y + HEADER_H <= panelY + panelH) {
                    g.drawString(font, Component.translatable(e.headerKey), panelX + 6, y + 1, 0xFF7FD4FF);
                }
                continue;
            }
            if (e.note) {
                // note 行：按面板内宽换行绘制（layoutPanel 已按行数计高）
                if (y < panelY + TITLE_H + 2 || y + e.noteLines * 10 > panelY + panelH - 1) continue;
                var lines = font.split(Component.translatable(e.labelKey), PANEL_W - 12);
                for (int i = 0; i < lines.size(); i++) {
                    g.drawString(font, lines.get(i), panelX + 6, y + i * 10, 0xFFB8C4D0);
                }
                continue;
            }
            if (y < panelY + TITLE_H + 2 || y + ROW_H > panelY + panelH - 1) continue;
            String lbl = e.labelDyn != null ? e.labelDyn.get()
                    : (e.labelKey != null ? Component.translatable(e.labelKey).getString() : "");
            // (?) 说明标记：有译文的参数行才画;点击切换显示说明浮框。
            // 标记必须落在控件区左缘之前（英文长标签会自动改放标签左侧）,否则会盖住循环/步进
            // 按钮并抢走它的点击（helpHits 先于控件分发）
            String tk = e.labelDyn == null ? tooltipKeyFor(e.labelKey) : null;
            int labelX = panelX + 6;
            int qx = -1;
            if (tk != null) {
                int after = labelX + font.width(lbl) + 3;
                if (after + 11 <= panelX + CONTROL_LEFT) {
                    qx = after;
                } else {
                    qx = labelX;          // 空间不足 → 标记前置,标签右移让位
                    labelX += 12;
                }
            }
            g.drawString(font, lbl, labelX, y + (ROW_H - 8) / 2, 0xFFD0D8E0);
            if (tk != null) {
                boolean hover = mouseX >= qx - 1 && mouseX <= qx + 9 && mouseY >= y && mouseY <= y + ROW_H;
                boolean active = tk.equals(helpKey);
                g.drawString(font, "?", qx, y + (ROW_H - 8) / 2,
                        active ? 0xFF7FD4FF : hover ? 0xFFA8D8FF : 0x7090A8C0);
                helpHits.add(new HelpHit(qx - 1, y, 10, ROW_H, tk));
            }
            if (e.value != null && e.valueRight > 0) {
                String v = e.value.get();
                g.drawString(font, v, panelX + e.valueRight - font.width(v),
                        y + (ROW_H - 8) / 2, 0xFFA8E0FF);
            } else if (e.value != null && e.widgets.isEmpty()) {
                // 无控件的值行（如"当前组件/分组"标题行）：值右对齐到面板右缘——
                // 此前只支持 valueRight>0 的行,这些行的值从不显示（只有静态标签）
                String v = e.value.get();
                g.drawString(font, v, panelX + PANEL_W - 8 - font.width(v),
                        y + (ROW_H - 8) / 2, 0xFFA8E0FF);
            }
        }
    }

    // ================= 交互 =================

    /** 面板命中（非控件区域：用于拖动整个面板） */
    private boolean panelHit(double mx, double my) {
        if (mx < panelX || mx > panelX + PANEL_W || my < panelY || my > panelY + panelH) return false;
        for (PEntry e : panelEntries) {
            for (PW pw : e.widgets) {
                if (pw.widget().isMouseOver(mx, my)) return false; // 控件优先
            }
        }
        return true;
    }

    /** 鼠标是否悬停在任一控件上（控件优先于元素命中，避免底栏/面板按钮被拖拽抢走） */
    private boolean overAnyWidget(double mx, double my) {
        for (var child : children()) {
            if (child instanceof AbstractWidget w && w.isActive() && w.isMouseOver(mx, my)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 0) (?) 说明标记：命中 → 切换显示对应说明;其它点击先关闭已有说明再正常处理
        for (HelpHit h : helpHits) {
            if (mouseX >= h.x() && mouseX <= h.x() + h.w()
                    && mouseY >= h.y() && mouseY <= h.y() + h.h()) {
                helpKey = h.key().equals(helpKey) ? null : h.key();
                return true;
            }
        }
        helpKey = null;
        // 0b) 面板滚动条拖拽（优先于面板拖动）
        if (panelScrollbarHit(mouseX, mouseY)) {
            panelScrollbarDragging = true;
            panelScrollbarDrag(mouseY);
            return true;
        }
        // 1) 悬浮面板拖动（标题栏或面板空白处）
        if (panelHit(mouseX, mouseY)) {
            panelDragging = true;
            panelGrabX = (int) mouseX - panelX;
            panelGrabY = (int) mouseY - panelY;
            return true;
        }
        // 2) 控件优先（面板行 / 底栏 / 重置按钮）
        if (overAnyWidget(mouseX, mouseY)) {
            boolean consumed = super.mouseClicked(mouseX, mouseY, button);
            if (consumed) markDirty(); // 控件交互即标脏（显式保存机制）
            return consumed;
        }
        // 3) 元素命中 → 开始拖拽（自定义 = 组件/文本/图标;长条 = 预览中的条/状态图标/数值文本）
        if (hudStyle() == HudStyle.ASTEORBAR) {
            // 图标/文本命中优先（更小;拖拽 = 拆分独立摆放）
            for (String key : BarLayouts.KEYS) {
                int[] ir = BarLayouts.lastIconRect(key);
                if (ir == null) continue;
                if (mouseX >= ir[0] - 1 && mouseX < ir[0] + ir[2] + 1
                        && mouseY >= ir[1] - 1 && mouseY < ir[1] + ir[3] + 1) {
                    selectedAsteorBar = key;
                    draggingAsteorBar = key; // 拖拽闸门是 draggingAsteorBar——此前只置 Icon 标志,
                    draggingAsteorIcon = true; // mouseDragged 永远不进入该分支（实测"拖不动"）
                    iconGrabX = (int) mouseX;
                    iconGrabY = (int) mouseY;
                    var p = BarLayouts.get(key);
                    iconStartOffX = p.iconOffX;
                    iconStartOffY = p.iconOffY;
                    panelPage = PanelPage.EDIT;
                    rebuildWidgets();
                    return true;
                }
                int[] tr = BarLayouts.lastTextRect(key);
                if (tr == null) continue;
                if (mouseX >= tr[0] - 1 && mouseX < tr[0] + tr[2] + 1
                        && mouseY >= tr[1] - 1 && mouseY < tr[1] + tr[3] + 1) {
                    selectedAsteorBar = key;
                    draggingAsteorBar = key;
                    draggingAsteorText = true;
                    textGrabX = (int) mouseX;
                    textGrabY = (int) mouseY;
                    var p = BarLayouts.get(key);
                    textStartOffX = p.textOffX;
                    textStartOffY = p.textOffY;
                    panelPage = PanelPage.EDIT;
                    rebuildWidgets();
                    return true;
                }
            }
            for (String key : BarLayouts.KEYS) {
                int[] r = BarLayouts.lastRect(key);
                if (r == null) continue;
                if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    selectedAsteorBar = key;
                    draggingAsteorBar = key;
                    int[] f = BarLayouts.resolve(key, width, height, r[2], r[3]);
                    int bx = f != null ? f[0] : r[0];
                    int by = f != null ? f[1] : r[1];
                    barGrabDX = (int) mouseX - bx;
                    barGrabDY = (int) mouseY - by;
                    panelPage = PanelPage.EDIT; // 点击条直达其编辑页
                    rebuildWidgets();
                    return true;
                }
            }
            if (selectedAsteorBar != null) {
                selectedAsteorBar = null;
                rebuildWidgets();
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!editingCustom()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        Map<String, int[]> sizes = CustomHudRenderer.measureAll(layout(), minecraft == null ? null : minecraft.player);
        Map<String, HudLayoutSolver.Box> boxes = HudLayoutSolver.solve(layout(), sizes, width, height);
        for (Map.Entry<String, HudLayoutSolver.Box> e : boxes.entrySet()) {
            HudLayoutSolver.Box box = e.getValue();
            if (mouseX >= box.x() && mouseX <= box.x() + box.width()
                    && mouseY >= box.y() && mouseY <= box.y() + box.height()) {
                boolean changed = !e.getKey().equals(selected);
                selected = e.getKey();
                dragging = true;
                draggingX = (int) mouseX;
                draggingY = (int) mouseY;
                dragStartOffX = dragStartX(selected);
                dragStartOffY = dragStartY(selected);
                // 点击组件直达其编辑页;拆分子元素直达对应子组件页
                panelPage = selected.endsWith(".text") ? PanelPage.SUB_TEXT
                        : selected.endsWith(".icon") ? PanelPage.SUB_ICON
                        : PanelPage.EDIT;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panelScrollbarDragging) {
            panelScrollbarDrag(mouseY);
            return true;
        }
        if (panelDragging) {
            panelX = (int) mouseX - panelGrabX;
            panelY = (int) mouseY - panelGrabY;
            clampPanel();
            layoutPanel();
            layout().panelX = panelX; // 记录位置（随配置持久化）
            layout().panelY = panelY;
            return true;
        }
        if (draggingAsteorBar != null) {
            markDirty(); // 长条侧的自由摆放/子件拆分同样是内容改动,须进入未保存确认流程
            if (draggingAsteorIcon) {
                // 状态图标拆分拖拽：偏移 = 拖拽起点偏移 + 鼠标位移（钳制与面板步进同域）
                var p = BarLayouts.get(draggingAsteorBar);
                p.iconOffX = Math.max(-500, Math.min(500, iconStartOffX + (int) mouseX - iconGrabX));
                p.iconOffY = Math.max(-500, Math.min(500, iconStartOffY + (int) mouseY - iconGrabY));
                return true;
            }
            if (draggingAsteorText) {
                // 数值文本拆分拖拽：同图标语义
                var p = BarLayouts.get(draggingAsteorBar);
                p.textOffX = Math.max(-500, Math.min(500, textStartOffX + (int) mouseX - textGrabX));
                p.textOffY = Math.max(-500, Math.min(500, textStartOffY + (int) mouseY - textGrabY));
                return true;
            }
            // 长条自由摆放：首次拖拽即脱离预设布局（free=true）,位置钳制在屏幕内
            int[] r = BarLayouts.lastRect(draggingAsteorBar);
            if (r != null) {
                var p = BarLayouts.get(draggingAsteorBar);
                p.free = true;
                p.x = Math.max(0, Math.min((int) mouseX - barGrabDX, Math.max(0, width - r[2])));
                p.y = Math.max(0, Math.min((int) mouseY - barGrabDY, Math.max(0, height - r[3])));
            }
            return true;
        }
        if (dragging && editingCustom()) {
            markDirty();
                applyDrag(selected, dragStartOffX + (int) mouseX - draggingX,
                    dragStartOffY + (int) mouseY - draggingY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        draggingAsteorBar = null;
        draggingAsteorIcon = false;
        draggingAsteorText = false;
        panelDragging = false;
        panelScrollbarDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 悬停面板 → 滚动属性面板（全部样式：长条面板内容远超面板高,此前非自定义直接 return 滚不动）
        if (mouseX >= panelX && mouseX <= panelX + PANEL_W
                && mouseY >= panelY && mouseY <= panelY + panelH) {
            panelScrollTarget -= (float) delta * 24f;
            layoutPanel();
            return true;
        }
        if (!editingCustom()) return super.mouseScrolled(mouseX, mouseY, delta);
        nudgeScale(delta > 0 ? 0.05 : -0.05);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Esc 先关闭设置说明浮框（若有）
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && helpKey != null) {
            helpKey = null;
            return true;
        }
        // Esc = 层级回退：编辑页/类型页/组页/子组件页 → 列表页;列表页再按才退出编辑器
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && panelPage != PanelPage.LIST) {
            panelPage = PanelPage.LIST;
            rebuildWidgets();
            return true;
        }
        // 文本输入框聚焦时方向键/快捷键归输入框（否则会被组件微调逻辑抢走）
        if (getFocused() instanceof EditBox eb && eb.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!editingCustom()) return super.keyPressed(keyCode, scanCode, modifiers);
        int step = Screen.hasShiftDown() ? 10 : 1;
        // 标脏只在真正微调时——此前置于 switch 之前,任何按键（含 Esc/Tab/字母）都标脏,
        // 导致"没改任何东西按 Esc 也弹未保存确认页"
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> {
                markDirty();
                applyDrag(selected, dragStartX(selected) - step, dragStartY(selected));
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> {
                markDirty();
                applyDrag(selected, dragStartX(selected) + step, dragStartY(selected));
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> {
                markDirty();
                applyDrag(selected, dragStartX(selected), dragStartY(selected) - step);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> {
                markDirty();
                applyDrag(selected, dragStartX(selected), dragStartY(selected) + step);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void nudgeScale(double delta) {
        sel().scale = Math.max(0.5, Math.min(2.0, sel().scale + delta));
        markDirty();
    }

    @Override
    public void removed() {
        // 显式保存机制:removed 不再无条件落盘（保存/放弃经确认页决定）
    }

    @Override
    public void onClose() {
        // 确认页上再按 Esc = 取消退出,回到编辑——此前会直接走"保存并退出",
        // 想要"放弃"的用户反而把未保存改动写进了文件
        if (pendingUnsaved) {
            pendingUnsaved = false;
            rebuildWidgets();
            return;
        }
        if (dirty) {
            pendingUnsaved = true; // 下一次渲染为确认页
            rebuildWidgets();
            return;
        }
        ConfigManager.saveConfig();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
