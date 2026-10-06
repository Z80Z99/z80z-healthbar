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
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
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

    private static final List<String> COMPONENT_ORDER = List.of(
            HudLayoutConfig.HEALTH, HudLayoutConfig.FOOD, HudLayoutConfig.AIR,
            HudLayoutConfig.EXPERIENCE, HudLayoutConfig.ARMOR, HudLayoutConfig.MOUNT,
            HudLayoutConfig.COMPAT);

    // ---- 悬浮面板布局常量（GUI 坐标） ----
    private static final int PANEL_W = 178;
    private static final int TITLE_H = 14;
    private static final int HEADER_H = 11;
    private static final int ROW_H = 16;
    private static final int ROW_GAP = 2;
    private static final int BTN_H = 14;
    private static final int STEP_W = 22;
    private static final int CYCLE_W = 84;

    private final Screen parent;
    private String selected = HudLayoutConfig.HEALTH;
    private int draggingX, draggingY;
    private int dragStartOffX, dragStartOffY;
    private boolean dragging;

    /** 长条样式：预览中选中的条 + 拖拽状态（BarLayouts.KEYS 之一） */
    private String selectedAsteorBar;
    private String draggingAsteorBar;
    private int barGrabDX, barGrabDY;

    // ---- 悬浮面板状态 ----
    private final List<PEntry> panelEntries = new ArrayList<>();
    private int panelX, panelY, panelH;
    private boolean panelDragging;
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

    /** 面板行：节标题 / 参数行（label + 值 + 控件组） */
    private static final class PEntry {
        final String headerKey;
        final String labelKey;
        final Supplier<String> value;
        final List<PW> widgets = new ArrayList<>();
        int relY;
        int valueRight = -1;

        PEntry(String headerKey, String labelKey, Supplier<String> value) {
            this.headerKey = headerKey;
            this.labelKey = labelKey;
            this.value = value;
        }

        boolean isHeader() { return headerKey != null; }
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
        if (editingCustom()) buildCustomControls();
        buildPanelEntries();
        // 面板默认右上（曾拖动过则用记录值）
        panelX = layout().panelX < 0 ? width - PANEL_W - 6 : layout().panelX;
        panelY = layout().panelY < 0 ? 84 : layout().panelY;
        layoutPanel();
        clampPanel();
        layoutPanel();
        // 右上功能键全部样式通用——此前"完成/重置"只在自定义样式构建,长条/原版样式无完成按钮
        int fx = width - 150;
        if (editingCustom()) {
            addRenderableWidget(flat(140, 16, () -> Component.translatable("z80zhealthbar.editor.reset_component"),
                    () -> layout().resetComponent(baseKey(selected)), fx, 28));
            addRenderableWidget(flat(140, 16, () -> Component.translatable("z80zhealthbar.editor.reset_all"),
                    () -> layout().resetToDefaults(), fx, 46));
            addRenderableWidget(flat(140, 16, () -> CommonComponents.GUI_DONE, this::onClose, fx, 64));
        } else {
            addRenderableWidget(flat(140, 16, () -> CommonComponents.GUI_DONE, this::onClose, fx, 28));
        }
    }

    /** 自定义样式的专属控件：底栏（仅组件选择）+ 预设（一步应用）;右上功能键全部样式通用（init） */
    private void buildCustomControls() {
        int y = height - 24;
        int x = 8;
        addRenderableWidget(CycleButton.<String>builder(v -> Component.translatable("z80zhealthbar.hud.component." + v))
                                .withValues(COMPONENT_ORDER)
                                .withInitialValue(baseKey(selected))
                .create(x, y, Math.min(240, width - 16), 18, Component.empty(), (b, v) -> {
                    selected = v;
                    rebuildWidgets();
                }));

        // 预设布局（左上,一步应用：点击 = 立即应用当前显示的整套设计并轮换到下一个）
        addRenderableWidget(flat(150, 16, () -> Component.translatable("z80zhealthbar.editor.preset.apply")
                        .copy().append(": ")
                        .append(Component.translatable("z80zhealthbar.editor.preset." + presetSel)),
                () -> {
                    layout().applyPreset(presetSel);
                    presetSel = nextPreset(presetSel);
                    rebuildWidgets();
                }, 8, 28));
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

    /** 组织面板行：首行样式选择，随后按当前样式展示对应参数组 */
    private void buildPanelEntries() {
        panelEntries.clear();
        panelEntries.add(styleRow());

        HudStyle st = hudStyle();
        if (st == HudStyle.VANILLA) {
            panelEntries.add(new PEntry(null, "z80zhealthbar.editor.vanilla_note", null));
            return;
        }
        if (st == HudStyle.ASTEORBAR) {
            buildAsteorEntries();
            return;
        }
        buildCustomEntries();
    }

    /** 样式选择行：原版 / 长条 / 自定义（写回配置；预览同步切换） */
    private PEntry styleRow() {
        var o = ConfigManager.getConfig().overlay;
        return cycler("z80zhealthbar.editor.edit_style",
                () -> Component.translatable("z80zhealthbar.style.hudstyle."
                        + hudStyle().name().toLowerCase(Locale.ROOT)).getString(),
                () -> {
                    HudStyle[] vals = HudStyle.values();
                    o.hudStyle = vals[(hudStyle().ordinal() + 1) % vals.length].name();
                    selectedAsteorBar = null; // 换样式后长条选择失效
                    rebuildWidgets();
                });
    }

    /** 长条样式的全部参数（设置页玩家 HUD 的散项全部整合至此）：布局 / 显示开关 / 动态与闪烁 / 数值上限 */
    private void buildAsteorEntries() {
        var o = ConfigManager.getConfig().overlay;
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
                v -> Math.round(v * 100) + "%"));
        panelEntries.add(toggle("z80zhealthbar.option.overlay.mountHealthOnLeftSide.label",
                () -> o.mountHealthOnLeftSide, v -> o.mountHealthOnLeftSide = v));
        // 自由摆放：预览中直接拖拽任意条即可脱离布局预设;选中后在此精确微调
        panelEntries.add(new PEntry(null, "z80zhealthbar.editor.asteor.note", null));
        panelEntries.add(cycler("z80zhealthbar.editor.asteor.select",
                () -> selectedAsteorBar == null
                        ? Component.translatable("z80zhealthbar.editor.asteor.none").getString()
                        : Component.translatable("z80zhealthbar.hud.component." + selectedAsteorBar).getString(),
                () -> {
                    List<String> avail = new java.util.ArrayList<>();
                    for (String k : BarLayouts.KEYS) {
                        if (BarLayouts.lastRect(k) != null) avail.add(k);
                    }
                    if (avail.isEmpty()) return;
                    int i = selectedAsteorBar == null ? -1 : avail.indexOf(selectedAsteorBar);
                    selectedAsteorBar = avail.get((i + 1) % avail.size());
                    rebuildWidgets();
                }));
        if (selectedAsteorBar != null) {
            var p = BarLayouts.get(selectedAsteorBar);
            panelEntries.add(toggle("z80zhealthbar.editor.asteor.free",
                    () -> p.free, v -> p.free = v));
            panelEntries.add(stepper("z80zhealthbar.editor.asteor.x",
                    () -> p.x, v -> p.x = (int) Math.round(v), 0, 2000, 1));
            panelEntries.add(stepper("z80zhealthbar.editor.asteor.y",
                    () -> p.y, v -> p.y = (int) Math.round(v), 0, 2000, 1));
            panelEntries.add(cycler("z80zhealthbar.editor.asteor.bar_one",
                    () -> Component.translatable("z80zhealthbar.editor.asteor.reset").getString(),
                    () -> {
                        BarLayouts.reset(selectedAsteorBar);
                        selectedAsteorBar = null;
                        rebuildWidgets();
                    }));
        }
        panelEntries.add(cycler("z80zhealthbar.editor.asteor.bar_all",
                () -> Component.translatable("z80zhealthbar.editor.asteor.reset").getString(),
                () -> {
                    BarLayouts.resetAll();
                    selectedAsteorBar = null;
                    rebuildWidgets();
                }));
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
                v -> Math.round(v * 100) + "%"));
        panelEntries.add(cycler("z80zhealthbar.option.overlay.absorptionMode.label",
                () -> Component.translatable("z80zhealthbar.overlay.absorptionMode."
                        + Math.min(1, Math.max(0, o.absorptionMode))).getString(),
                () -> o.absorptionMode = (o.absorptionMode + 1) % 2));
        panelEntries.add(stepper("z80zhealthbar.option.overlay.hideUnchangingBarAfterSeconds.label",
                () -> o.hideUnchangingBarAfterSeconds, v -> o.hideUnchangingBarAfterSeconds = (int) Math.round(v),
                0, 600, 5));
        // ---- 数值上限（0 = 跟随实际值;长条/自定义两样式共用） ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.asteor_caps", null, null));
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.overlay.fullHealthValue",
                () -> o.fullHealthValue, v -> o.fullHealthValue = (int) Math.round(v), 0, 100000, 1,
                v -> v <= 0 ? "—" : String.valueOf((int) Math.round(v))));
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.overlay.fullFoodLevelValue",
                () -> o.fullFoodLevelValue, v -> o.fullFoodLevelValue = (int) Math.round(v), 0, 40, 1,
                v -> v <= 0 ? "—" : String.valueOf((int) Math.round(v))));
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.overlay.fullArmorValue",
                () -> o.fullArmorValue, v -> o.fullArmorValue = (int) Math.round(v), 0, 100, 1,
                v -> v <= 0 ? "—" : String.valueOf((int) Math.round(v))));
        panelEntries.add(stepper1Fmt("z80zhealthbar.option.overlay.fullSaturationValue",
                () -> o.fullSaturationValue, v -> o.fullSaturationValue = v, 0, 40, 0.5,
                v -> v <= 0 ? "—" : String.format(java.util.Locale.ROOT, "%.1f", v)));
    }

    /** 自定义样式的参数分组（模式 / 组件 / 条形 / 文本 / 图标） */
    private void buildCustomEntries() {
        ComponentLayout c = sel();
        // 组件显示形式:长条/图标/关闭(自设置页迁入,作用于当前选中组件)
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

        if (c.modeParsed() == HudLayoutConfig.ComponentMode.OFF) {
            panelEntries.add(new PEntry(null, "z80zhealthbar.editor.off_mode_note", null));
            return;
        }
        if (c.modeParsed() != HudLayoutConfig.ComponentMode.BAR) {
            panelEntries.add(new PEntry(null, "z80zhealthbar.editor.icon_mode_note", null));
            return;
        }

        // ---- 条形 ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.bar", null, null));
        panelEntries.add(stepper("z80zhealthbar.editor.bar_width",
                () -> c.barWidth, v -> c.barWidth = (int) Math.round(v), 40, 400, 4));
        panelEntries.add(stepper("z80zhealthbar.editor.bar_height",
                () -> c.barHeight, v -> c.barHeight = (int) Math.round(v), 5, 16, 1));

        // ---- 文本（先给显示开关,再给参数——此前开关只在底栏,面板无法重新打开） ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.text", null, null));
        panelEntries.add(toggle("z80zhealthbar.editor.show_text",
                () -> c.showText, v -> {
                    c.showText = v;
                    rebuildWidgets();
                }));
        if (c.showText) {
            panelEntries.add(cycler("z80zhealthbar.editor.text_align",
                    () -> Component.translatable("z80zhealthbar.editor.align."
                            + c.textAlignParsed().name().toLowerCase(Locale.ROOT)).getString(),
                    () -> {
                        var vals = HudLayoutConfig.TextAlign.values();
                        c.textAlign = vals[(c.textAlignParsed().ordinal() + 1) % vals.length].name();
                    }));
            panelEntries.add(stepper("z80zhealthbar.editor.text_x",
                    () -> c.textOffsetX, v -> c.textOffsetX = (int) Math.round(v), -50, 50, 1));
            panelEntries.add(stepper("z80zhealthbar.editor.text_y",
                    () -> c.textOffsetY, v -> c.textOffsetY = (int) Math.round(v), -50, 50, 1));
            panelEntries.add(cycler("z80zhealthbar.editor.text_anchor",
                    () -> anchorLabel(c.textAnchor), () -> {
                        c.textAnchor = nextAnchor(c.textAnchor);
                        c.textOffsetX = 0;
                        c.textOffsetY = 0;
                        rebuildWidgets();
                    }));
            panelEntries.add(stepper1("z80zhealthbar.editor.text_scale",
                    () -> c.textScale, v -> c.textScale = v, 0.25, 3.0, 0.1));
        }

        // ---- 图标（新增 X/Y 微调——此前该字段未暴露于任何界面,只能靠拖拽图标元素） ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.icon", null, null));
        if (c.iconAnchorParsed() == null) {
            panelEntries.add(cycler("z80zhealthbar.editor.icon_side",
                    () -> Component.translatable("z80zhealthbar.editor.icon."
                            + c.iconSideParsed().name().toLowerCase(Locale.ROOT)).getString(),
                    () -> {
                        var vals = HudLayoutConfig.IconSide.values();
                        c.iconSide = vals[(c.iconSideParsed().ordinal() + 1) % vals.length].name();
                    }));
        }
        panelEntries.add(cycler("z80zhealthbar.editor.icon_anchor",
                () -> anchorLabel(c.iconAnchor), () -> {
                    c.iconAnchor = nextAnchor(c.iconAnchor);
                    c.iconOffsetX = 0;
                    c.iconOffsetY = 0;
                    rebuildWidgets();
                }));
        panelEntries.add(stepper("z80zhealthbar.editor.icon_x",
                () -> c.iconOffsetX, v -> c.iconOffsetX = (int) Math.round(v), -500, 500, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.icon_y",
                () -> c.iconOffsetY, v -> c.iconOffsetY = (int) Math.round(v), -500, 500, 1));
        panelEntries.add(stepper1("z80zhealthbar.editor.icon_scale",
                () -> c.iconScale, v -> c.iconScale = v, 0.25, 3.0, 0.1));
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

    /** 数值参数行（自定义显示格式，如 OFF / 百分比） */
    private PEntry stepper1Fmt(String labelKey, DoubleSupplier get, DoubleConsumer set,
                               double min, double max, double step, DoubleFunction<String> fmt) {
        PEntry e = new PEntry(null, labelKey, () -> fmt.apply(get.getAsDouble()));
        int minusX = PANEL_W - 4 - STEP_W * 2 - 2;
        int plusX = PANEL_W - 4 - STEP_W;
        e.valueRight = minusX - 4;
        FlatButton minus = new FlatButton(STEP_W, BTN_H, () -> Component.literal("\u2212"), () ->
                set.accept(Math.max(min, roundStep(get.getAsDouble() - step, step))));
        FlatButton plus = new FlatButton(STEP_W, BTN_H, () -> Component.literal("+"), () ->
                set.accept(Math.min(max, roundStep(get.getAsDouble() + step, step))));
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

    /** 按面板原点更新全部控件位置，并计算面板高度（内容超高时启用滚动,行按滚动物画出裁剪） */
    private void layoutPanel() {
        // 1) 内容坐标（不含滚动）——同时得出内容总高
        int cursor = TITLE_H + 4;
        for (PEntry e : panelEntries) {
            if (e.isHeader()) {
                e.relY = cursor + 2;
                cursor += HEADER_H;
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

        // 1) 预览 = 实际渲染路径
        if (st == HudStyle.CUSTOM) {
            CustomHudRenderer.render(graphics, partialTick);
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
                            Component.translatable("z80zhealthbar.hud.component." + baseKey(e.getKey())).getString() + suffix,
                            box.x() + box.width() / 2, box.y() - 10, 0xFF40FF40);
                }
            }
        } else if (st == HudStyle.ASTEORBAR) {
            // 长条样式：预览渲染时各条记录了矩形,直接框选（未渲染的条如坐骑无框）
            for (String key : BarLayouts.KEYS) {
                int[] r = BarLayouts.lastRect(key);
                if (r == null) continue;
                boolean isSel = key.equals(selectedAsteorBar);
                int color = isSel ? 0x8040FF40 : 0x50FFFFFF;
                graphics.renderOutline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, color);
                if (isSel) {
                    graphics.drawCenteredString(font,
                            Component.translatable("z80zhealthbar.hud.component." + key).getString(),
                            r[0] + r[2] / 2, r[1] - 10, 0xFF40FF40);
                }
            }
        }

        // 4) 悬浮面板底板（深色 + 青色强调 + 投影）
        renderPanelChrome(graphics);

        // 5) 控件（含面板行 / 底栏 / 重置按钮）
        super.render(graphics, mouseX, mouseY, partialTick);

        // 6) 面板文本（标签/值/节标题；控件之上补文字）
        renderPanelTexts(graphics);

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

    /** 面板文本：节标题 / 行标签 / 行值（右对齐到控件左侧；随滚动位移,视口外不画） */
    private void renderPanelTexts(GuiGraphics g) {
        int scroll = Math.round(panelScroll);
        for (PEntry e : panelEntries) {
            int y = panelY + e.relY - scroll;
            if (e.isHeader()) {
                if (y >= panelY + TITLE_H + 2 && y + HEADER_H <= panelY + panelH) {
                    g.drawString(font, Component.translatable(e.headerKey), panelX + 6, y + 1, 0xFF7FD4FF);
                }
                continue;
            }
            if (y < panelY + TITLE_H + 2 || y + ROW_H > panelY + panelH - 1) continue;
            g.drawString(font, Component.translatable(e.labelKey), panelX + 6,
                    y + (ROW_H - 8) / 2, 0xFFD0D8E0);
            if (e.value != null && e.valueRight > 0) {
                String v = e.value.get();
                g.drawString(font, v, panelX + e.valueRight - font.width(v),
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
        // 1) 悬浮面板拖动（标题栏或面板空白处）
        if (panelHit(mouseX, mouseY)) {
            panelDragging = true;
            panelGrabX = (int) mouseX - panelX;
            panelGrabY = (int) mouseY - panelY;
            return true;
        }
        // 2) 控件优先（面板行 / 底栏 / 重置按钮）
        if (overAnyWidget(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // 3) 元素命中 → 开始拖拽（自定义 = 组件/文本/图标;长条 = 预览中的条）
        if (hudStyle() == HudStyle.ASTEORBAR) {
            for (String key : BarLayouts.KEYS) {
                int[] r = BarLayouts.lastRect(key);
                if (r == null) continue;
                if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    boolean changed = !key.equals(selectedAsteorBar);
                    selectedAsteorBar = key;
                    draggingAsteorBar = key;
                    int[] f = BarLayouts.resolve(key, width, height, r[2], r[3]);
                    int bx = f != null ? f[0] : r[0];
                    int by = f != null ? f[1] : r[1];
                    barGrabDX = (int) mouseX - bx;
                    barGrabDY = (int) mouseY - by;
                    if (changed) rebuildWidgets(); // 面板刷新为选中条
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
                if (changed) rebuildWidgets(); // 面板刷新为新选中元素
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
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
        panelDragging = false;
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
        if (!editingCustom()) return super.keyPressed(keyCode, scanCode, modifiers);
        int step = Screen.hasShiftDown() ? 10 : 1;
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> {
                applyDrag(selected, dragStartX(selected) - step, dragStartY(selected));
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> {
                applyDrag(selected, dragStartX(selected) + step, dragStartY(selected));
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> {
                applyDrag(selected, dragStartX(selected), dragStartY(selected) - step);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> {
                applyDrag(selected, dragStartX(selected), dragStartY(selected) + step);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void nudgeScale(double delta) {
        sel().scale = Math.max(0.5, Math.min(2.0, sel().scale + delta));
    }

    @Override
    public void removed() {
        ConfigManager.saveConfig();
    }

    @Override
    public void onClose() {
        ConfigManager.saveConfig();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
