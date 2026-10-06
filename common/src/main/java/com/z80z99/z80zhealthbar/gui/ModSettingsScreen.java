package com.z80z99.z80zhealthbar.gui;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.Z80ZHealthBarConfig;
import com.z80z99.z80zhealthbar.mobdisplay.DamagePopupRenderer;
import com.z80z99.z80zhealthbar.mobdisplay.EntityHealthStyle;
import com.z80z99.z80zhealthbar.mobdisplay.HealthDisplayRenderer;
import com.z80z99.z80zhealthbar.mobdisplay.MobDisplayRenderer;
import com.z80z99.z80zhealthbar.mobdisplay.MobHealthBarStyle;
import com.z80z99.z80zhealthbar.status.EntityStatusSnapshot;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import com.z80z99.z80zhealthbar.overlay.HudStyle;
import com.z80z99.z80zhealthbar.platform.PlatformService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * 游戏内五页设置界面（任务书 6）—— V2 完全重写。
 *
 * <p>布局架构：顶部标签页条 + 中央滚动内容面板 + 底部完成按钮。
 * 控件行统一"左标签 + 右控件"对齐，分组节标题带分隔线，行悬停高亮，
 * 滚轮滚动 + 右侧滚动条。所有控件由本类手动布局/命中（不经过 super.render），
 * 彻底解决旧版两列硬编码坐标互相覆盖、状态行错位的问题。
 */
public final class ModSettingsScreen extends Screen {

    // ---- 布局常量（GUI 缩放坐标） ----
    private static final int HEADER_H = 56;
    private static final int FOOTER_H = 30;
    private static final int ROW_H = 22;
    private static final int SECTION_H = 26;
    private static final int TEXT_H = 14;
    private static final int PANEL_PAD = 6;
    private static final int WIDGET_W = 150;

    private enum Page { MAIN, STYLE, POPUP, VISIBILITY, COMPAT, ADVANCED }

    private static final String[] PAGE_KEYS = {
            "z80zhealthbar.settings.page.main",
            "z80zhealthbar.settings.page.style",
            "z80zhealthbar.settings.page.popup",
            "z80zhealthbar.settings.page.visibility",
            "z80zhealthbar.settings.page.compat",
            "z80zhealthbar.settings.page.advanced",
    };

    /** 页眉副标题(每页一句功能说明) */
    private static final String[] PAGE_DESC_KEYS = {
            "z80zhealthbar.settings.pagedesc.main",
            "z80zhealthbar.settings.pagedesc.style",
            "z80zhealthbar.settings.pagedesc.popup",
            "z80zhealthbar.settings.pagedesc.visibility",
            "z80zhealthbar.settings.pagedesc.compat",
            "z80zhealthbar.settings.pagedesc.advanced",
    };

    private final Screen parent;
    private Page page = Page.MAIN;

    // ---- 行模型 ----
    private abstract static class Row {
        abstract int height();

        /** 布局控件到 (x, contentY) —— 每帧滚动后调用 */
        abstract void layout(int x, int y, int w, ModSettingsScreen s);

        /** 绘制非控件部分（标签/文本/节标题） */
        abstract void render(GuiGraphics g, int x, int y, int w, ModSettingsScreen s);

        /** 悬停说明（未配置翻译的行返回空列表） */
        List<Component> tooltip() { return List.of(); }

        void collectWidgets(java.util.List<net.minecraft.client.gui.components.AbstractWidget> out) {}

    /** 命中转发（滚轮外的鼠标事件） */
        boolean mouseClicked(double mx, double my, int btn) { return false; }

        boolean mouseDragged(double mx, double my, int btn, double dx, double dy) { return false; }

        boolean mouseReleased(double mx, double my, int btn) { return false; }
    }

    private final class SectionRow extends Row {
        final String key;

        SectionRow(String key) { this.key = key; }

        @Override int height() { return SECTION_H; }

        @Override
        void render(GuiGraphics g, int x, int y, int w, ModSettingsScreen s) {
            g.fill(x, y + SECTION_H - 2, x + w, y + SECTION_H - 1, fade(0x30FFFFFF));
            g.drawString(font, Component.translatable(key), x, y + 8, fade(0xFF7FD4FF));
        }

        @Override void layout(int x, int y, int w, ModSettingsScreen s) {}
    }

    /** 左标签 + 右控件行 */
    private final class ControlRow extends Row {
        final String labelKey;
        final AbstractWidget widget;
        final String tooltipKey;

        ControlRow(String labelKey, AbstractWidget widget, String tooltipKey) {
            this.labelKey = labelKey;
            this.widget = widget;
            this.tooltipKey = tooltipKey;
        }

        @Override int height() { return ROW_H; }

        @Override
        void layout(int x, int y, int w, ModSettingsScreen s) {
            widget.setX(x + w - WIDGET_W);
            widget.setY(y + (ROW_H - widget.getHeight()) / 2);
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, ModSettingsScreen s) {
            String label = truncate(Component.translatable(labelKey).getString(), w - WIDGET_W - 12);
            g.drawString(font, label, x + 2, y + (ROW_H - 8) / 2, fade(0xFFE0E0E0));
        }

        @Override
        List<Component> tooltip() { return tooltipFor(tooltipKey); }

        @Override
        boolean mouseClicked(double mx, double my, int btn) {
            return widget.mouseClicked(mx, my, btn);
        }

        @Override
        boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            return widget.mouseDragged(mx, my, btn, dx, dy);
        }

        @Override
        boolean mouseReleased(double mx, double my, int btn) {
            return widget.mouseReleased(mx, my, btn);
        }

        @Override
        void collectWidgets(java.util.List<net.minecraft.client.gui.components.AbstractWidget> out) {
            out.add(widget);
        }
    }

    /** 只读摘要行：左标签 + 右实时取值（主页配置概览用，不构成编辑入口） */
    private final class KeyValueRow extends Row {
        final String labelKey;
        final java.util.function.Supplier<String> value;

        KeyValueRow(String labelKey, java.util.function.Supplier<String> value) {
            this.labelKey = labelKey;
            this.value = value;
        }

        @Override int height() { return ROW_H; }

        @Override void layout(int x, int y, int w, ModSettingsScreen s) {}

        @Override
        void render(GuiGraphics g, int x, int y, int w, ModSettingsScreen s) {
            int ty = y + (ROW_H - 8) / 2;
            g.drawString(font, truncate(Component.translatable(labelKey).getString(), w / 2),
                    x + 2, ty, fade(0xFFE0E0E0));
            String v = truncate(value.get(), w / 2 - 6);
            g.drawString(font, v, x + w - 4 - font.width(v), ty, fade(0xFFA8E0FF));
        }
    }

    /** 整行按钮 */
    private final class ActionRow extends Row {
        final AbstractWidget button;
        final String tooltipKey;

        ActionRow(AbstractWidget button, String tooltipKey) {
            this.button = button;
            this.tooltipKey = tooltipKey;
        }

        @Override int height() { return ROW_H; }

        @Override
        void layout(int x, int y, int w, ModSettingsScreen s) {
            button.setX(x);
            button.setY(y + 1);
            button.setWidth(w);
        }

        @Override void render(GuiGraphics g, int x, int y, int w, ModSettingsScreen s) {}

        @Override
        List<Component> tooltip() { return tooltipFor(tooltipKey); }

        @Override
        boolean mouseClicked(double mx, double my, int btn) {
            return button.mouseClicked(mx, my, btn);
        }

        @Override
        boolean mouseReleased(double mx, double my, int btn) {
            return button.mouseReleased(mx, my, btn);
        }

        @Override
        void collectWidgets(java.util.List<net.minecraft.client.gui.components.AbstractWidget> out) {
            out.add(button);
        }
    }

    /** 灰色说明文本 */
    private final class TextRow extends Row {
        final String key;

        TextRow(String key) { this.key = key; }

        @Override int height() { return TEXT_H; }

        @Override
        void render(GuiGraphics g, int x, int y, int w, ModSettingsScreen s) {
            g.drawString(font, truncate(Component.translatable(key).getString(), w - 4),
                    x + 2, y + 3, fade(0xFF909090));
        }

        @Override void layout(int x, int y, int w, ModSettingsScreen s) {}
    }

    /** 兼容目标行：名称 + 检测状态 + 开关 */
    private final class CompatRow extends Row {
        final String labelKey;
        final String modId;
        final CycleButton<Boolean> toggle;

        CompatRow(String labelKey, String modId, CycleButton<Boolean> toggle) {
            this.labelKey = labelKey;
            this.modId = modId;
            this.toggle = toggle;
        }

        @Override int height() { return ROW_H; }

        @Override
        void layout(int x, int y, int w, ModSettingsScreen s) {
            toggle.setX(x + w - WIDGET_W);
            toggle.setY(y + (ROW_H - toggle.getHeight()) / 2);
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, ModSettingsScreen s) {
            g.drawString(font, Component.translatable(labelKey), x + 2, y + (ROW_H - 8) / 2, fade(0xFFE0E0E0));
            var platform = PlatformService.get();
            boolean loaded = platform.isModLoaded(modId);
            String status = loaded
                    ? Component.translatable("z80zhealthbar.compat.status.found",
                            Component.literal(platform.getModVersion(modId))).getString()
                    : Component.translatable("z80zhealthbar.compat.status.missing").getString();
            int color = fade(loaded ? 0xFF7FE07F : 0xFFA07070);
            int nameW = font.width(Component.translatable(labelKey));
            int statusW = font.width(status);
            int sx = Math.min(x + nameW + 10, x + w - WIDGET_W - statusW - 6);
            g.drawString(font, status, sx, y + (ROW_H - 8) / 2, color);
        }

        @Override
        List<Component> tooltip() { return tooltipFor(labelKey + ".tooltip"); }

        @Override
        boolean mouseClicked(double mx, double my, int btn) {
            return toggle.mouseClicked(mx, my, btn);
        }

        @Override
        boolean mouseReleased(double mx, double my, int btn) {
            return toggle.mouseReleased(mx, my, btn);
        }

        @Override
        void collectWidgets(java.util.List<net.minecraft.client.gui.components.AbstractWidget> out) {
            out.add(toggle);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    /** 平滑滚动目标（滚轮/拖动设定，渲染时指数逼近） */
    private double scrollTarget;
    /** 页切换动画起始时间（滑入 + 淡入，约 180ms） */
    private long pageAnimStart;
    private boolean draggingScrollbar;
    private double scrollbarGrab;
    /** 按下时命中的行：拖拽/释放事件只投给它（AbstractWidget.mouseDragged 对左键一律返回 true，顺序转发会被首行劫持） */
    private Row pressedRow;

    // ---- 实时预览（实体样式页右半面板）----
    private boolean previewActive;
    private boolean previewFriendly;
    private int previewX, previewY, previewW, previewH;
    private CycleButton<Boolean> previewToggle;
    private Button previewFullscreenBtn;
    private Button previewPauseBtn;
    /** 暂停预览：true 时演示时钟冻结在 previewFreezeAt */
    private boolean previewPaused;
    private long previewFreezeAt;

    private Component feedback;
    private long feedbackUntilMs;
    private int contentH;
    private int contentX;
    private int contentW;
    private int viewH;
    /** 命中测试用的"可见控件"快照（render 时刷新） */
    private final List<AbstractWidget> hitWidgets = new ArrayList<>();
    /** 页切换动画的行透明度（1=完全不透明） */
    private float rowAlpha = 1f;

    /** 按当前页动画缩放 ARGB 的 alpha（滑入淡入用） */
    private int fade(int argb) {
        if (rowAlpha >= 0.999f) return argb;
        int a = (int) (((argb >>> 24) & 0xFF) * rowAlpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public ModSettingsScreen(Screen parent) {
        super(Component.translatable("z80zhealthbar.settings.title"));
        this.parent = parent;
    }

    public static ModSettingsScreen create(Screen parent) {
        return new ModSettingsScreen(parent);
    }

    // ================= 构建 =================

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        hitWidgets.clear();
        rows.clear();
        switch (page) {
            case MAIN -> buildMain();
            case STYLE -> buildStyle();
            case POPUP -> buildPopup();
            case VISIBILITY -> buildVisibility();
            case COMPAT -> buildCompat();
            case ADVANCED -> buildAdvanced();
        }
        contentH = rows.stream().mapToInt(Row::height).sum();
        // 每一页都带右半实时预览面板（窗口宽度足够时）
        previewActive = width >= 460;
            previewToggle = null;
            previewFullscreenBtn = null;
            previewPauseBtn = null;
        if (previewActive) {
            previewW = Math.min(260, width * 2 / 5);
            int gap = 4; // 两栏面板外缘之间的呼吸缝
            // 设置栏与预览栏相邻成组、整组水平居中——此前设置栏在预览左侧的剩余宽度内
            // 居中，窗口越宽两栏之间的空档越大（用户反馈"预览应贴着设置栏"）
            contentW = Math.min(300, width - previewW - PANEL_PAD * 4 - gap - 12);
            int pairW = contentW + PANEL_PAD * 4 + gap + previewW;
            int startX = Math.max(6, (width - pairW) / 2);
            contentX = startX + PANEL_PAD;
            previewX = contentX + contentW + PANEL_PAD * 2 + gap;
            previewY = HEADER_H - 4;
            previewH = height - HEADER_H - FOOTER_H + 8;
            previewToggle = CycleButton.<Boolean>builder(v -> Component.translatable(
                            v ? "z80zhealthbar.settings.preview.friendly" : "z80zhealthbar.settings.preview.enemy"))
                    .withValues(List.of(false, true))
                    .withInitialValue(previewFriendly)
                    .displayOnlyValue()
                    .create(0, 0, previewW - 12, 18, Component.empty(), (b, v) -> previewFriendly = v);
            previewToggle.setX(previewX + 6);
            previewToggle.setY(previewY + 20);
            // 暂停 / 全屏 两等宽按钮（暂停 = 冻结演示时钟;玩家 HUD 页已删除,玩家 HUD 的编辑与
            // 预览统一在 HUD 布局编辑器内进行）。
            // 注意：本界面不经过 super.render，控件须在 drawPreviewPanel 手动渲染并在鼠标事件中转发
            int halfW = (previewW - 12 - 4) / 2;
            previewPauseBtn = Button.builder(
                            Component.translatable(previewPaused
                                    ? "z80zhealthbar.settings.preview.resume" : "z80zhealthbar.settings.preview.pause"),
                            b -> {
                                previewPaused = !previewPaused;
                                if (previewPaused) previewFreezeAt = System.currentTimeMillis();
                                b.setMessage(Component.translatable(previewPaused
                                        ? "z80zhealthbar.settings.preview.resume" : "z80zhealthbar.settings.preview.pause"));
                            })
                    .bounds(previewX + 6, previewY + 40, halfW, 18)
                    .build();
            previewFullscreenBtn = Button.builder(
                            Component.translatable("z80zhealthbar.settings.preview.fullscreen"),
                            b -> minecraft.setScreen(new FullscreenPreviewScreen(this)))
                    .bounds(previewX + 6 + halfW + 4, previewY + 40, halfW, 18)
                    .build();
        } else {
            contentW = Math.min(420, width - 24 - PANEL_PAD * 2);
            contentX = (width - contentW) / 2;
        }
        viewH = height - HEADER_H - FOOTER_H;
        scroll = Math.min(scroll, maxScroll());
        scrollTarget = Math.min(scrollTarget, maxScroll());
    }

    private void switchPage(Page p) {
        page = p;
        scroll = 0;
        scrollTarget = 0;
        pageAnimStart = System.currentTimeMillis();
        rebuild();
    }

    /** 页切换动画进度 0..1（easeOutQuad） */
    private float pageAnim() {
        float t = Math.min(1f, (System.currentTimeMillis() - pageAnimStart) / 180f);
        return 1f - (1f - t) * (1f - t);
    }

    private double maxScroll() {
        return Math.max(0, contentH - viewH + PANEL_PAD);
    }

    private Z80ZHealthBarConfig cfg() {
        return ConfigManager.getConfig();
    }

    private void buildMain() {
        var c = cfg();
        rows.add(new SectionRow("z80zhealthbar.settings.section.general"));
        rows.add(toggleRow("z80zhealthbar.hud.enable", c.overlay.enableOverlay,
                v -> c.overlay.enableOverlay = v));
        rows.add(toggleRow("z80zhealthbar.entity.enable", c.barStyle.enableHealthBar,
                v -> c.barStyle.enableHealthBar = v));
        // 样式选择分属各专页：实体样式在"实体样式"页（页面内容跟随所选样式），
        // HUD 样式在"玩家 HUD"页——避免同一设置跨页重复。

        // 当前配置概览（只读实时取值；各项的编辑入口仍在各自专页，不构成第二入口）
        rows.add(new SectionRow("z80zhealthbar.settings.section.overview"));
        rows.add(new KeyValueRow("z80zhealthbar.overview.entity_style",
                () -> Component.translatable("z80zhealthbar.style.entityhealthstyle."
                        + c.entityStyleParsed().name().toLowerCase(Locale.ROOT)).getString()));
        rows.add(new KeyValueRow("z80zhealthbar.overview.hud_style",
                () -> Component.translatable("z80zhealthbar.style.hudstyle."
                        + hudStyle().name().toLowerCase(Locale.ROOT)).getString()));
        rows.add(new KeyValueRow("z80zhealthbar.overview.damage_popup",
                () -> Component.translatable("z80zhealthbar.damagePopup.theme."
                        + c.damagePopup.themeParsed().toLowerCase(Locale.ROOT)).getString()
                        + " · " + Component.translatable("z80zhealthbar.damagePopup.motion."
                        + c.damagePopup.motionParsed().toLowerCase(Locale.ROOT)).getString()));
        rows.add(new KeyValueRow("z80zhealthbar.overview.visibility",
                () -> (int) c.visibility.maxDistance + " m"));

        // 快捷操作（编辑器入口自"玩家 HUD"页迁来，作为全局落地入口）
        rows.add(new SectionRow("z80zhealthbar.settings.section.quick"));
        // HUD 管线切换（自定义/长条）——编辑器面板不再提供（切换会整套更换 HUD,组件编辑中误触反人类）
        rows.add(cycleEnumRow("z80zhealthbar.hud.style", HudStyle.class,
                ModSettingsScreen::hudStyle, v -> c.overlay.hudStyle = v.name()));
        rows.add(actionRow("z80zhealthbar.settings.open_editor", () -> {
            c.overlay.hudStyle = HudStyle.CUSTOM.name();
            ConfigManager.saveConfig();
            minecraft.setScreen(new HudLayoutScreen(this));
        }));
        rows.add(actionRow("z80zhealthbar.advanced.reload", () -> {
            ConfigManager.loadConfig();
            rebuild();
            toast("z80zhealthbar.msg.config_reloaded");
        }));
    }

    /** 可见性规则页 */
    private void buildVisibility() {
        var c = cfg();
        var vis = c.visibility;

        rows.add(new SectionRow("z80zhealthbar.settings.section.entity_visibility"));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showOnSelf", vis.showOnSelf, v -> vis.showOnSelf = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showOnPlayers", vis.showOnPlayers, v -> vis.showOnPlayers = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showOnHostile", vis.showOnHostile, v -> vis.showOnHostile = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showOnPassive", vis.showOnPassive, v -> vis.showOnPassive = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showOnBoss", vis.showOnBoss, v -> vis.showOnBoss = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showOnArmorStands", vis.showOnArmorStands, v -> vis.showOnArmorStands = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showOnFullHealthWithoutAbsorption", vis.showOnFullHealthWithoutAbsorption, v -> vis.showOnFullHealthWithoutAbsorption = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showOnFullHealthWithAbsorption", vis.showOnFullHealthWithAbsorption, v -> vis.showOnFullHealthWithAbsorption = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showDamaged", vis.showDamaged, v -> vis.showDamaged = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showOnAggro", vis.showOnAggro, v -> vis.showOnAggro = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showHoveredMob", vis.showHoveredMob, v -> vis.showHoveredMob = v));
        rows.add(toggleRow("z80zhealthbar.option.visibility.showOnlyWhenVisible", vis.showOnlyWhenVisible, v -> vis.showOnlyWhenVisible = v));
        rows.add(sliderRow("z80zhealthbar.option.visibility.maxDistance", 4, 256, (int) vis.maxDistance,
                v -> vis.maxDistance = v, v -> v + " m"));
        rows.add(sliderRow("z80zhealthbar.option.visibility.maxConcurrentDisplays", 0, 100, vis.maxConcurrentDisplays,
                v -> vis.maxConcurrentDisplays = v, v -> v == 0 ? "∞" : String.valueOf(v)));
        rows.add(sliderRow("z80zhealthbar.option.visibility.fadeInTicks", 0, 20, vis.fadeInTicks,
                v -> vis.fadeInTicks = v, v -> v + " t"));
        rows.add(toggleRow("z80zhealthbar.option.visibility.fadeOnDeath", vis.fadeOnDeath, v -> vis.fadeOnDeath = v));
    }

    /** 实体样式页：顶部样式选择 + 仅显示当前样式的专属设置（切换即重排） */
    private void buildStyle() {
        var c = cfg();
        EntityHealthStyle style = c.entityStyleParsed();

        rows.add(new SectionRow("z80zhealthbar.settings.section.styles"));
        rows.add(cycleEnumRow("z80zhealthbar.entity.style", EntityHealthStyle.class,
                c::entityStyleParsed, v -> {
                    c.setEntityStyle(v);
                    scroll = 0;
                    rebuild();
                }));

        switch (style) {
            case OFF -> rows.add(new TextRow("z80zhealthbar.settings.style_off_hint"));
            case MOBHEALTHBAR -> {
                rows.add(new SectionRow("z80zhealthbar.settings.section.style_a"));
                // 形状与配色分设两行：形状决定形态，配色决定框/填充色（心形行不受配色影响）
                rows.add(cycleRow("z80zhealthbar.option.styleA.barType", List.of(0, 1, 2),
                        () -> c.styleA.barType, v -> c.styleA.barType = v,
                        v -> Component.translatable("z80zhealthbar.option.styleA.barType." + v)));
                rows.add(cycleRow("z80zhealthbar.option.styleA.colorVariant", List.of(0, 1, 2, 3),
                        () -> c.styleA.colorVariant, v -> c.styleA.colorVariant = v,
                        v -> Component.translatable("z80zhealthbar.option.styleA.colorVariant." + v)));
                rows.add(sliderRow("z80zhealthbar.option.styleA.heightOffset", -40, 80, (int) (c.styleA.heightOffset * 10),
                        v -> c.styleA.heightOffset = v / 10.0, v -> (v / 10.0f) + " m"));
                rows.add(sliderRow("z80zhealthbar.option.styleA.offsetX", -100, 100, c.styleA.offsetX,
                        v -> c.styleA.offsetX = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.styleA.offsetY", -100, 100, c.styleA.offsetY,
                        v -> c.styleA.offsetY = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.styleA.scaleBar", 25, 300, (int) (c.styleA.scaleBar * 100),
                        v -> c.styleA.scaleBar = v / 100.0, v -> v + "%"));
                rows.add(sliderRow("z80zhealthbar.option.styleA.scaleBarWidth", 25, 300, (int) (c.styleA.scaleBarWidth * 100),
                        v -> c.styleA.scaleBarWidth = v / 100.0, v -> v + "%"));
                rows.add(sliderRow("z80zhealthbar.option.styleA.scaleBarHeight", 25, 300, (int) (c.styleA.scaleBarHeight * 100),
                        v -> c.styleA.scaleBarHeight = v / 100.0, v -> v + "%"));
                rows.add(sliderRow("z80zhealthbar.option.styleA.scaleName", 25, 300, (int) (c.styleA.scaleName * 100),
                        v -> c.styleA.scaleName = v / 100.0, v -> v + "%"));
                rows.add(sliderRow("z80zhealthbar.option.styleA.scaleNums", 25, 300, (int) (c.styleA.scaleNums * 100),
                        v -> c.styleA.scaleNums = v / 100.0, v -> v + "%"));
                rows.add(sliderRow("z80zhealthbar.option.styleA.numOffsetX", -50, 50, c.styleA.numOffsetX,
                        v -> c.styleA.numOffsetX = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.styleA.numOffsetY", -50, 50, c.styleA.numOffsetY,
                        v -> c.styleA.numOffsetY = v, String::valueOf));
                // 数值取整（样式 1/3 通用开关；样式 3 的设置在本页另一分支中，同一字段）
                rows.add(toggleRow("z80zhealthbar.option.barStyle.integerHealthText", c.barStyle.integerHealthText,
                        v -> c.barStyle.integerHealthText = v));
                rows.add(sliderRow("z80zhealthbar.option.styleA.opacity", 0, 255, c.styleA.opacity,
                        v -> c.styleA.opacity = v, String::valueOf));
                rows.add(toggleRow("z80zhealthbar.option.styleA.showName", c.styleA.showName, v -> c.styleA.showName = v));
                rows.add(toggleRow("z80zhealthbar.option.styleA.showHp", c.styleA.showHp, v -> c.styleA.showHp = v));
                buildAddonRows();
                buildFxRows();
            }
            case MOBPLAQUES -> {
                rows.add(new SectionRow("z80zhealthbar.settings.section.plaques"));
                rows.add(sliderRow("z80zhealthbar.option.plaqueStyle.xOffset", -150, 150, c.plaqueStyle.xOffset,
                        v -> c.plaqueStyle.xOffset = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.plaqueStyle.yOffset", -60, 60, c.plaqueStyle.yOffset,
                        v -> c.plaqueStyle.yOffset = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.plaqueStyle.opacity", 0, 255, c.plaqueStyle.opacity,
                        v -> c.plaqueStyle.opacity = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.plaqueStyle.plaqueScale", 5, 200, (int) (c.plaqueStyle.plaqueScale * 100),
                        v -> c.plaqueStyle.plaqueScale = v / 100.0, v -> v + "%"));
                rows.add(sliderRow("z80zhealthbar.option.plaqueStyle.maxPlaqueRowWidth", 0, 300, c.plaqueStyle.maxPlaqueRowWidth,
                        v -> c.plaqueStyle.maxPlaqueRowWidth = v, v -> v + " px"));
                rows.add(sliderRow("z80zhealthbar.option.plaqueStyle.maxRenderDistance", 4, 128, c.plaqueStyle.maxRenderDistance,
                        v -> c.plaqueStyle.maxRenderDistance = v, v -> v + " m"));
                rows.add(toggleRow("z80zhealthbar.option.plaqueStyle.scaleWithDistance", c.plaqueStyle.scaleWithDistance, v -> c.plaqueStyle.scaleWithDistance = v));
                rows.add(toggleRow("z80zhealthbar.option.plaqueStyle.plaqueBackground", c.plaqueStyle.plaqueBackground, v -> c.plaqueStyle.plaqueBackground = v));
                rows.add(toggleRow("z80zhealthbar.option.plaqueStyle.behindWalls", c.plaqueStyle.behindWalls, v -> c.plaqueStyle.behindWalls = v));
                rows.add(toggleRow("z80zhealthbar.option.plaqueStyle.showArmorRow", c.plaqueStyle.showArmorRow, v -> c.plaqueStyle.showArmorRow = v));
                rows.add(toggleRow("z80zhealthbar.option.plaqueStyle.showToughnessRow", c.plaqueStyle.showToughnessRow, v -> c.plaqueStyle.showToughnessRow = v));
                rows.add(toggleRow("z80zhealthbar.option.plaqueStyle.showAirRow", c.plaqueStyle.showAirRow, v -> c.plaqueStyle.showAirRow = v));
                rows.add(sliderRow("z80zhealthbar.option.plaqueStyle.textScale", 25, 300, (int) (c.plaqueStyle.textScale * 100),
                        v -> c.plaqueStyle.textScale = v / 100.0, v -> v + "%"));
                rows.add(sliderRow("z80zhealthbar.option.plaqueStyle.textOffsetX", -50, 50, c.plaqueStyle.textOffsetX,
                        v -> c.plaqueStyle.textOffsetX = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.plaqueStyle.textOffsetY", -50, 50, c.plaqueStyle.textOffsetY,
                        v -> c.plaqueStyle.textOffsetY = v, String::valueOf));
                rows.add(toggleRow("z80zhealthbar.option.plaqueStyle.originalRender", c.plaqueStyle.originalRender,
                        v -> c.plaqueStyle.originalRender = v));
            }
            case ASTEORBAR -> {
                rows.add(new SectionRow("z80zhealthbar.settings.section.style_c"));
                rows.add(cycleRow("z80zhealthbar.option.barStyle.barVariant", List.of(0, 1, 2, 3),
                        () -> c.barStyle.barVariant, v -> c.barStyle.barVariant = v,
                        v -> Component.translatable("z80zhealthbar.option.barStyle.barVariant." + v)));
                // 分段刻度（长条变体 2）：每格血量为模式开关（"均分"=按格数均分,否则固定每格血量）,
                // 两种模式互斥显示——此前两行同时可调但静默互斥,误导（用户实测反馈）
                rows.add(cycleRow("z80zhealthbar.option.barStyle.segmentHp", List.of(0, 1, 2, 4, 5, 10, 20, 50, 100),
                        () -> c.barStyle.segmentHp,
                        v -> {
                            c.barStyle.segmentHp = v;
                            rebuild(); // 行集随模式变化（均分模式补充分段格数行）
                        },
                        v -> v == 0 ? Component.translatable("z80zhealthbar.option.barStyle.segmentHp.even")
                                : Component.literal(v + " HP")));
                if (c.barStyle.segmentHp == 0) {
                    rows.add(sliderRow("z80zhealthbar.option.barStyle.segmentCount", 1, 64, c.barStyle.segmentCount,
                            v -> c.barStyle.segmentCount = v, String::valueOf));
                }
                rows.add(toggleRow("z80zhealthbar.option.barStyle.segmentWholeOnly", c.barStyle.segmentWholeOnly,
                        v -> c.barStyle.segmentWholeOnly = v));
                rows.add(sliderRow("z80zhealthbar.option.barStyle.barScale", 10, 400, (int) (c.barStyle.barScale * 100),
                        v -> c.barStyle.barScale = v / 100.0, v -> v + "%"));
                rows.add(sliderRow("z80zhealthbar.option.barStyle.barAlpha", 0, 255, c.barStyle.barAlpha,
                        v -> c.barStyle.barAlpha = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.barStyle.barHalfWidth", 0, 80, c.barStyle.barHalfWidth,
                        v -> c.barStyle.barHalfWidth = v, v -> v == 0 ? "自动" : String.valueOf(v)));
                rows.add(sliderRow("z80zhealthbar.option.barStyle.barHalfHeight", 1, 12, c.barStyle.barHalfHeight,
                        v -> c.barStyle.barHalfHeight = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.barStyle.barPixelOffsetX", -100, 100, c.barStyle.barPixelOffsetX,
                        v -> c.barStyle.barPixelOffsetX = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.barStyle.barPixelOffsetY", -100, 100, c.barStyle.barPixelOffsetY,
                        v -> c.barStyle.barPixelOffsetY = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.barStyle.barTextScale", 10, 200, (int) (c.barStyle.barTextScale * 100),
                        v -> c.barStyle.barTextScale = v / 100.0, v -> v + "%"));
                rows.add(sliderRow("z80zhealthbar.option.barStyle.barTextOffsetX", -50, 50, (int) c.barStyle.barTextOffsetX,
                        v -> c.barStyle.barTextOffsetX = v, String::valueOf));
                rows.add(sliderRow("z80zhealthbar.option.barStyle.barTextOffsetY", -50, 50, (int) c.barStyle.barTextOffsetY,
                        v -> c.barStyle.barTextOffsetY = v, String::valueOf));
                rows.add(toggleRow("z80zhealthbar.option.barStyle.healthBarHealthColorDynamic", c.barStyle.healthBarHealthColorDynamic,
                        v -> c.barStyle.healthBarHealthColorDynamic = v));
                rows.add(toggleRow("z80zhealthbar.option.barStyle.originalRender", c.barStyle.originalRender,
                        v -> c.barStyle.originalRender = v));
                rows.add(toggleRow("z80zhealthbar.option.barStyle.integerHealthText", c.barStyle.integerHealthText,
                        v -> c.barStyle.integerHealthText = v));
                buildAddonRows();
                buildFxRows();
            }
        }
    }

    /** 附加行（条形样式 1/3 通用）：护甲/韧性/氧气图标行 */
    private void buildAddonRows() {
        var c = cfg();
        rows.add(new SectionRow("z80zhealthbar.settings.section.addons"));
        rows.add(toggleRow("z80zhealthbar.option.addons.armorRow", c.entityAddons.armorRow, v -> c.entityAddons.armorRow = v));
        rows.add(toggleRow("z80zhealthbar.option.addons.toughnessRow", c.entityAddons.toughnessRow, v -> c.entityAddons.toughnessRow = v));
        rows.add(toggleRow("z80zhealthbar.option.addons.airRow", c.entityAddons.airRow, v -> c.entityAddons.airRow = v));
    }

    /** 动态效果（条形样式 1/3 通用）：平滑过渡/伤害残影/受伤闪白 */
    private void buildFxRows() {
        var fx = cfg().dynamicFx;
        rows.add(new SectionRow("z80zhealthbar.settings.section.barfx"));
        rows.add(toggleRow("z80zhealthbar.option.barfx.enabled", fx.enabled, v -> fx.enabled = v));
        rows.add(toggleRow("z80zhealthbar.option.barfx.smooth", fx.smooth, v -> fx.smooth = v));
        rows.add(toggleRow("z80zhealthbar.option.barfx.ghost", fx.ghost, v -> fx.ghost = v));
        rows.add(toggleRow("z80zhealthbar.option.barfx.hurtFlash", fx.hurtFlash, v -> fx.hurtFlash = v));
        rows.add(toggleRow("z80zhealthbar.option.barfx.numRoll", fx.numRoll, v -> fx.numRoll = v));
        rows.add(toggleRow("z80zhealthbar.option.barfx.numPunch", fx.numPunch, v -> fx.numPunch = v));
        rows.add(toggleRow("z80zhealthbar.option.barfx.numTint", fx.numTint, v -> fx.numTint = v));
        rows.add(new TextRow("z80zhealthbar.option.barfx.note"));
    }

    /** 伤害跳字页 */
    private void buildPopup() {
        var c = cfg();

        rows.add(new SectionRow("z80zhealthbar.settings.section.damage_popup"));
        var dp = c.damagePopup;
        rows.add(toggleRow("z80zhealthbar.option.damagePopup.enabled", dp.enabled, v -> dp.enabled = v));
        rows.add(cycleRow("z80zhealthbar.option.damagePopup.theme",
                List.of("APEX", "TACTICAL", "WARFRAME", "CLASSIC", "MINIMAL"),
                dp::themeParsed, v -> dp.theme = v,
                v -> Component.translatable("z80zhealthbar.damagePopup.theme." + v.toLowerCase(Locale.ROOT))));
        rows.add(cycleRow("z80zhealthbar.option.damagePopup.motion",
                List.of("RISE", "ARC", "STACK", "CUMULATIVE"),
                dp::motionParsed, v -> dp.motion = v,
                v -> Component.translatable("z80zhealthbar.damagePopup.motion." + v.toLowerCase(Locale.ROOT))));
        // 随机跳出偏角上限（仅 RISE/ARC 生效,0 = 垂直向上）
        rows.add(sliderRow("z80zhealthbar.option.damagePopup.launchAngle", 0, 180, (int) dp.launchAngleDegrees,
                v -> dp.launchAngleDegrees = v, v -> v == 0 ? "—" : v + "°"));
        rows.add(toggleRow("z80zhealthbar.option.damagePopup.estimateWithoutServer",
                dp.estimateWithoutServer, v -> dp.estimateWithoutServer = v));
        rows.add(sliderRow("z80zhealthbar.option.damagePopup.scale", 50, 200, (int) (dp.scale * 100),
                v -> dp.scale = v / 100.0, v -> v + "%"));
        rows.add(sliderRow("z80zhealthbar.option.damagePopup.lifetimeTicks", 10, 60, dp.lifetimeTicks,
                v -> dp.lifetimeTicks = v, v -> v + " t"));
        rows.add(sliderRow("z80zhealthbar.option.damagePopup.maxPerEntity", 1, 8, dp.maxPerEntity,
                v -> dp.maxPerEntity = v, String::valueOf));
        rows.add(sliderRow("z80zhealthbar.option.damagePopup.mergeWindowTicks", 0, 20, dp.mergeWindowTicks,
                v -> dp.mergeWindowTicks = v, v -> v == 0 ? "—" : v + " t"));
        rows.add(toggleRow("z80zhealthbar.option.damagePopup.hitMarker", dp.hitMarker, v -> dp.hitMarker = v));
        rows.add(toggleRow("z80zhealthbar.option.damagePopup.hitMarkerSound", dp.hitMarkerSound,
                v -> dp.hitMarkerSound = v));
        rows.add(new TextRow("z80zhealthbar.damagePopup.note"));
    }

    private void buildCompat() {
        var c = cfg();
        rows.add(new SectionRow("z80zhealthbar.settings.section.compat_targets"));
        for (String[] t : new String[][]{
                {"appleskin", "z80zhealthbar.compat.appleskin"},
                {"thirst", "z80zhealthbar.compat.thirst"},
                {"parcool", "z80zhealthbar.compat.parcool"},
                {"toughasnails", "z80zhealthbar.compat.toughasnails"},
                {"botania", "z80zhealthbar.compat.botania"},
                {"mekanism", "z80zhealthbar.compat.mekanism"},
                {"vampirism", "z80zhealthbar.compat.vampirism"},
                {"feathers", "z80zhealthbar.compat.feathers"}}) {
            rows.add(compatRow(t[1], t[0]));
        }
        rows.add(new TextRow("z80zhealthbar.compat.server_note"));
    }

    private void buildAdvanced() {
        var c = cfg();
        rows.add(new SectionRow("z80zhealthbar.settings.section.maintenance"));
        rows.add(actionRow("z80zhealthbar.advanced.export", () -> {
            ConfigManager.saveConfig();
            try {
                minecraft.keyboardHandler.setClipboard(
                        java.nio.file.Files.readString(ConfigManager.getConfigFilePath()));
                toast("z80zhealthbar.advanced.exported");
            } catch (Exception ignored) {
            }
        }));
        rows.add(actionRow("z80zhealthbar.advanced.import", () -> {
            try {
                var loaded = new com.google.gson.Gson().fromJson(
                        minecraft.keyboardHandler.getClipboard(), Z80ZHealthBarConfig.class);
                if (loaded != null) {
                    ConfigManager.setConfig(loaded);
                    ConfigManager.saveConfig();
                    rebuild();
                    toast("z80zhealthbar.advanced.imported");
                }
            } catch (Exception e) {
                toast("z80zhealthbar.advanced.import_failed");
            }
        }));
        rows.add(actionRow("z80zhealthbar.advanced.reset_all", () -> {
            ConfigManager.resetToDefaults();
            rebuild();
            toast("z80zhealthbar.advanced.reset_done");
        }));
        rows.add(actionRow("z80zhealthbar.advanced.reset_layout", () -> {
            c.hudLayout.resetToDefaults();
            toast("z80zhealthbar.advanced.reset_done");
        }));
        rows.add(actionRow("z80zhealthbar.advanced.open_file", () -> {
            minecraft.keyboardHandler.setClipboard(ConfigManager.getConfigFilePath().toString());
            toast("z80zhealthbar.advanced.path_copied");
        }));

    }

    // ================= 行工厂 =================

    private ControlRow toggleRow(String key, boolean value, Consumer<Boolean> setter) {
        var btn = CycleButton.onOffBuilder(value)
                .displayOnlyValue()
                .create(0, 0, WIDGET_W, 18, Component.empty(), (b, v) -> {
                    setter.accept(v);
                    ConfigManager.saveConfig();
                });
        return new ControlRow(key, btn, key + ".tooltip");
    }

    private <E extends Enum<E>> ControlRow cycleEnumRow(String key, Class<E> type,
                                                        java.util.function.Supplier<E> current,
                                                        Consumer<E> setter) {
        return cycleRow(key, List.of(type.getEnumConstants()), current, setter,
                v -> Component.translatable("z80zhealthbar.style."
                        + type.getSimpleName().toLowerCase(Locale.ROOT) + "." + v.name().toLowerCase(Locale.ROOT)));
    }

    private <T> ControlRow cycleRow(String key, List<T> values, java.util.function.Supplier<T> current,
                                    Consumer<T> setter, java.util.function.Function<T, Component> display) {
        var btn = CycleButton.<T>builder(display)
                .withValues(values)
                .withInitialValue(current.get())
                .displayOnlyValue()
                .create(0, 0, WIDGET_W, 18, Component.empty(), (b, v) -> {
                    setter.accept(v);
                    ConfigManager.saveConfig();
                });
        return new ControlRow(key, btn, key + ".tooltip");
    }

    private ControlRow sliderRow(String key, int min, int max, int value,
                                 Consumer<Integer> setter, IntFunction<String> formatter) {
        var slider = new AbstractSliderButton(0, 0, WIDGET_W, 18,
                Component.literal(formatter.apply(value)), (value - min) / (double) (max - min)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(formatter.apply(min + (int) Math.round(this.value * (max - min)))));
            }

            @Override
            protected void applyValue() {
                setter.accept(min + (int) Math.round(this.value * (max - min)));
                // 拖动中不落盘：在 mouseReleased 统一保存（避免每帧写文件）
            }
        };
        return new ControlRow(key + ".label", slider, key + ".tooltip");
    }

    private ActionRow actionRow(String key, Runnable action) {
        return new ActionRow(net.minecraft.client.gui.components.Button.builder(
                        Component.translatable(key), b -> action.run())
                .bounds(0, 0, WIDGET_W * 2, 18)
                .build(), key + ".tooltip");
    }

    private CompatRow compatRow(String labelKey, String modId) {
        var c = cfg();
        var btn = CycleButton.onOffBuilder(c.compat.isHookEnabled(modId))
                .displayOnlyValue()
                .create(0, 0, WIDGET_W, 18, Component.empty(), (b, v) -> {
                    setHook(c, modId, v);
                    ConfigManager.saveConfig();
                });
        return new CompatRow(labelKey, modId, btn);
    }

    private static void setHook(Z80ZHealthBarConfig c, String modId, boolean value) {
        try {
            var field = c.compat.getClass().getField(
                    "hook" + Character.toUpperCase(modId.charAt(0)) + modId.substring(1));
            field.set(c.compat, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static HudStyle hudStyle() {
        try {
            return HudStyle.valueOf(ConfigManager.getConfig().overlay.hudStyle);
        } catch (IllegalArgumentException | NullPointerException e) {
            return HudStyle.ASTEORBAR;
        }
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        while (text.length() > 1 && font.width(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }

    /** 解析 <key>.tooltip 悬停说明；无该键翻译时返回空列表（不显示） */
    private List<Component> tooltipFor(String key) {
        String text = Component.translatable(key).getString();
        if (text.equals(key)) return List.of();
        List<Component> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.isBlank()) lines.add(Component.literal(line));
        }
        return lines;
    }

    /** 操作反馈：面板底部临时文本（主菜单无玩家也可用） */
    private void toast(String key) {
        feedback = Component.translatable(key);
        feedbackUntilMs = System.currentTimeMillis() + 2500;
    }

    // ================= 渲染与输入 =================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // 页眉横幅:大标题 + 灰色副标题(标签页下、内容面板上)
        g.drawString(font, Component.translatable(pageKey()), width / 2 - font.width(Component.translatable(pageKey())) / 2, 27, 0xFFEAF6FF);
        Component desc = Component.translatable(pageDescKey());
        g.drawString(font, desc, width / 2 - font.width(desc) / 2, 38, 0xFF8890A0);

        // 内容面板
        int panelTop = HEADER_H - 4;
        int panelBottom = height - FOOTER_H + 4;
        g.fill(contentX - PANEL_PAD, panelTop, contentX + contentW + PANEL_PAD, panelBottom, 0x66000000);
        g.fill(contentX - PANEL_PAD, panelTop, contentX + contentW + PANEL_PAD, panelTop + 1, 0x40FFFFFF);
        g.fill(contentX - PANEL_PAD, panelBottom - 1, contentX + contentW + PANEL_PAD, panelBottom, 0x40FFFFFF);

        // 标签页（手动）
        renderTabs(g, mouseX, mouseY);

        hitWidgets.clear();
        Row hoveredRow = null;

        // 平滑滚动：指数逼近目标（滚轮/滚动条拖动设定目标，避免瞬跳）
        scroll += (scrollTarget - scroll) * 0.35;
        if (Math.abs(scrollTarget - scroll) < 0.5) scroll = scrollTarget;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));

        // 页切换动画：内容滑入 + 淡入
        float anim = pageAnim();
        int slide = (int) ((1f - anim) * 8);
        rowAlpha = anim;

        // 行内容（scissor 裁剪 + 悬停高亮）
        g.enableScissor(contentX - PANEL_PAD, panelTop, contentX + contentW + PANEL_PAD, panelBottom);
        int y = HEADER_H - (int) scroll + slide;
        for (Row row : rows) {
            int h = row.height();
            if (y + h >= panelTop && y <= panelBottom) {
                boolean hovered = mouseX >= contentX - PANEL_PAD && mouseX <= contentX + contentW + PANEL_PAD
                        && mouseY >= y && mouseY < y + h
                        && mouseY >= panelTop && mouseY < panelBottom;
                if (hovered && !(row instanceof SectionRow)) {
                    g.fill(contentX, y, contentX + contentW, y + h, fade(0x20FFFFFF));
                    // 悬停强调条：左缘青色竖线，随悬停即时出现
                    g.fill(contentX, y + 1, contentX + 2, y + h - 1, fade(0xC07FD4FF));
                    hoveredRow = row;
                }
                row.render(g, contentX, y, contentW, this);
                row.layout(contentX, y, contentW, this);
                row.collectWidgets(hitWidgets);
            }
            y += h;
        }
        for (AbstractWidget widget : hitWidgets) {
            widget.render(g, mouseX, mouseY, partialTick);
        }
        g.disableScissor();

        // 悬停说明：必须在 disableScissor 之后绘制，否则被裁剪窗口裁掉
        if (hoveredRow != null) {
            List<Component> tip = hoveredRow.tooltip();
            if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }

        // 滚动条
        if (maxScroll() > 0) {
            int trackTop = panelTop + 2;
            int trackBottom = panelBottom - 2;
            int trackH = trackBottom - trackTop;
            double ratio = (double) viewH / contentH;
            int thumbH = Math.max(12, (int) (trackH * ratio));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (scroll / maxScroll()));
            int sx = contentX + contentW + 3;
            boolean sbHover = mouseX >= sx - 1 && mouseX <= sx + 4;
            g.fill(sx, trackTop, sx + 3, trackBottom, fade(0x28304050));
            g.fill(sx, thumbY, sx + 3, thumbY + thumbH, fade(sbHover ? 0xC07FD4FF : 0x807FD4FF));
        }

        // 右半实时预览面板（实体样式页）
        if (previewActive) {
            drawPreviewPanel(g, mouseX, mouseY, partialTick);
        }

        // 底部条：标题（左）+ 操作反馈（右）+ 完成按钮（中）。
        // 标题不放页眉：GUI 窄时会压住标签页（实测反馈）。
        g.drawString(font, getTitle(), 6, height - 8, 0xFF909090);
        if (feedback != null && System.currentTimeMillis() < feedbackUntilMs) {
            String ft = feedback.getString();
            g.drawString(font, ft, contentX + contentW - font.width(ft), height - 8, 0xFF7FE07F);
        }
        renderDone(g, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int n = Page.values().length;
        int tabW = Math.min(96, (width - 16) / n);
        int totalW = tabW * n;
        int x0 = (width - totalW) / 2;
        for (int i = 0; i < n; i++) {
            int tx = x0 + i * tabW;
            boolean active = i == page.ordinal();
            boolean hover = mouseX >= tx && mouseX < tx + tabW - 2 && mouseY >= 2 && mouseY < 22;
            int bg = active ? 0x80425368 : hover ? 0x60404048 : 0x50303038;
            g.fill(tx, 2, tx + tabW - 2, 22, bg);
            g.fill(tx, 2, tx + tabW - 2, 3, active ? 0xFF7FD4FF : 0x25FFFFFF); // 顶部强调线
            Component label = Component.translatable(PAGE_KEYS[i]);
            int textW = font.width(label);
            g.drawString(font, label, tx + (tabW - 2 - textW) / 2, 10,
                    active ? 0xFFEAF6FF : hover ? 0xFFD0D8E0 : 0xFFB0B8C0);
        }
    }

    private String pageKey() {
        return PAGE_KEYS[page.ordinal()];
    }

    private String pageDescKey() {
        return PAGE_DESC_KEYS[page.ordinal()];
    }

    private boolean doneHovered(double mx, double my) {
        int bw = 150;
        return mx >= width / 2 - bw / 2 && mx <= width / 2 + bw / 2
                && my >= height - 26 && my <= height - 8;
    }

    /** 右半实时预览面板：深色取景框内用真实渲染器绘制当前样式的模拟血条，随配置即时刷新 */
    private void drawPreviewPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(previewX - PANEL_PAD, previewY, previewX + previewW + PANEL_PAD, previewY + previewH, 0x66000000);
        g.fill(previewX - PANEL_PAD, previewY, previewX + previewW + PANEL_PAD, previewY + 1, 0x40FFFFFF);
        g.fill(previewX - PANEL_PAD, previewY + previewH - 1, previewX + previewW + PANEL_PAD, previewY + previewH, 0x40FFFFFF);

        Component title = Component.translatable("z80zhealthbar.settings.preview.title");
        g.drawString(font, title, previewX + (previewW - font.width(title)) / 2, previewY + 7, 0xFF7FD4FF);

        previewToggle.render(g, mouseX, mouseY, partialTick);
        if (previewPauseBtn != null) previewPauseBtn.render(g, mouseX, mouseY, partialTick);
        if (previewFullscreenBtn != null) previewFullscreenBtn.render(g, mouseX, mouseY, partialTick);

        int bx = previewX + 2, by = previewY + 64, bw = previewW - 4, bh = previewH - 70;
        g.fill(bx, by, bx + bw, by + bh, 0xFF0B0E14);
        g.renderOutline(bx, by, bw, bh, 0x40FFFFFF);

        g.enableScissor(bx, by, bx + bw, by + bh);
        drawMockBar(g, bx + bw / 2, by, bh);
        g.disableScissor();
    }

    /** 全屏预览界面：整窗实体 mock 预览（玩家 HUD 的编辑与预览统一在 HUD 布局编辑器内） */
    private class FullscreenPreviewScreen extends Screen {
        private final Screen backTo;

        FullscreenPreviewScreen(Screen backTo) {
            super(Component.translatable("z80zhealthbar.settings.preview.title"));
            this.backTo = backTo;
        }

        @Override
        protected void init() {
            // 敌方/友方切换（与设置页共用 previewFriendly 状态）
            addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.translatable(
                            v ? "z80zhealthbar.settings.preview.friendly" : "z80zhealthbar.settings.preview.enemy"))
                    .withValues(List.of(false, true))
                    .withInitialValue(previewFriendly)
                    .displayOnlyValue()
                    .create(width / 2 - 100, 22, 200, 18, Component.empty(), (b, v) -> previewFriendly = v));
            addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                    .bounds(width / 2 - 100, height - 26, 200, 20)
                    .build());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            drawMockBar(g, width / 2, 0, height - 32); // 整窗即取景框（底部留返回按钮位）
            g.drawCenteredString(font, getTitle(), width / 2, 8, 0xFFFFFF);
            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(backTo);
        }
    }

    private void drawMockBar(GuiGraphics g, int cx, int boxTop, int boxH) {
        var mc = Minecraft.getInstance();
        var cfg = cfg();
        var dispatcher = mc.getEntityRenderDispatcher();

        // 预览战斗循环（6 秒）：5 次随机掉血（伤害由周期序号确定性生成，每个循环不同、循环内稳定）+ 末段回血
        var dp = cfg.damagePopup;
        long now = previewPaused ? previewFreezeAt : System.currentTimeMillis(); // 暂停预览：冻结演示时钟
        long lifeMs = dp.lifetimeTicks * 50L;
        long cycle = 6000L;
        long tCycle = now % cycle;
        long cycleIdx = now / cycle;
        long[] births = {400, 1300, 2200, 3100, 4000};
        float maxHp = 20f;
        float mobHeight = 1.95f;
        int[] dmg = new int[5];
        for (int i = 0; i < 5; i++) {
            dmg[i] = 2 + (int) ((cycleIdx * 31L + i * 17L) % 7); // 2..8
        }
        // 吸收分摊（友方预设 4 点吸收）：命中先被吸收抵扣，剩余扣血
        float remainingAbs = previewFriendly ? 4f : 0f;
        float[] healthPart = new float[5];
        float[] absPart = new float[5];
        float hp = maxHp;
        for (int i = 0; i < 5; i++) {
            if (tCycle < births[i]) { healthPart[i] = -1; continue; } // 未命中
            absPart[i] = Math.min(remainingAbs, dmg[i]);
            healthPart[i] = dmg[i] - absPart[i];
            remainingAbs -= absPart[i];
            hp = Math.max(0.5f, hp - healthPart[i]);
        }
        // 末段回血：最后一次跳字淡出后线性回满
        float regenT = Math.min(1f, Math.max(0f,
                (tCycle - (births[4] + lifeMs)) / (float) (cycle - births[4] - lifeMs)));
        hp = hp + (maxHp - hp) * regenT;

        // 受伤硬直演示：命中后 300ms 内触发受伤闪白（与 hurtTime 语义一致）
        boolean hurtNow = false;
        for (long birth : births) {
            long age = tCycle - birth;
            if (age >= 0 && age <= 300) hurtNow = true;
        }

        var snap = previewFriendly
                ? EntityStatusSnapshot.preview(
                        Component.translatable("z80zhealthbar.settings.preview.mock.friendly").getString(),
                        mobHeight, hp, maxHp, Math.round(remainingAbs), 6, 2, hurtNow)
                : EntityStatusSnapshot.preview(
                        Component.translatable("z80zhealthbar.settings.preview.mock.enemy").getString(),
                        mobHeight, hp, maxHp, 0, 6, 2, hurtNow);

        var buffer = g.bufferSource();
        var pose = g.pose();
        // 每方块 GUI 像素：按取景框高度动态适配（僵尸 1.95 + 抬升 1.2 + 血条约 2 块 + 跳字余量 ≈ 5.2 块;
        // 此前按 4.2 估算,样式 3 的血条会顶出取景框被裁掉）
        float zb = Math.max(24f, Math.min(80f, (boxH - 16) / 5.2f));
        int feetY = boxTop + boxH - 6;

        // 虚拟相机：固定 180° 基准（无摇摆——摇摆只进僵尸 yBodyRot；进朝向会让牌子沿深度轴倾斜沉入面板背景）
        float sway = (float) Math.sin(now / 900.0) * 8f; // 仅僵尸本体摇摆（暂停时同步冻结）
        var virtualCam = new org.joml.Quaternionf().rotationYXZ(
                (float) Math.toRadians(180f), (float) Math.toRadians(-8f), 0f);

        // 1) 模拟单位（库存界面 renderEntityInInventory 同款变换：
        //    T(x,y,50) × scaling(s,s,-s) × rotateX(俯仰)∘rotateZ(180°)（竖立翻转）+ 灯光 + 朝向覆写 + fancy）
        if (mc.level != null) {
            var mob = previewFriendly
                    ? (net.minecraft.world.entity.LivingEntity) new net.minecraft.world.entity.npc.Villager(
                            net.minecraft.world.entity.EntityType.VILLAGER, mc.level)
                    : (net.minecraft.world.entity.LivingEntity) new net.minecraft.world.entity.monster.Zombie(
                            net.minecraft.world.entity.EntityType.ZOMBIE, mc.level);
            // 180° 基准 + 摇摆：z 镜像缩放下恰好面向视角
            float yaw = 180f + sway;
            mob.setYRot(yaw);
            mob.yBodyRot = yaw;
            mob.yBodyRotO = yaw;
            mob.setYHeadRot(yaw);
            mob.yHeadRotO = yaw;
            mob.setXRot(0);

            var savedOrient = new org.joml.Quaternionf(dispatcher.cameraOrientation());
            pose.pushPose();
            pose.translate(cx, feetY, 50f);
            pose.mulPoseMatrix(new org.joml.Matrix4f().scaling(zb, zb, -zb));
            pose.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(-8f))
                    .mul(new org.joml.Quaternionf().rotateZ((float) Math.PI)));
            com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
            dispatcher.overrideCameraOrientation(new org.joml.Quaternionf(virtualCam).conjugate());
            dispatcher.setRenderShadow(false);
            com.mojang.blaze3d.systems.RenderSystem.runAsFancy(() ->
                    dispatcher.render(mob, 0, 0, 0, 0f, 1f, pose, buffer, 0xF000F0));
            dispatcher.setRenderShadow(true);
            dispatcher.overrideCameraOrientation(savedOrient);
            pose.popPose();
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            g.flush();
        }

        // 2) 血条（复用真实渲染器；覆盖层渲染类型不测深度，画在僵尸之上）。
        //    主页"实体状态栏"总开关关闭时只显示僵尸，实时反映开关状态
        float popupBaseY = 0;
        if (!cfg.barStyle.enableHealthBar) {
            g.drawCenteredString(font,
                    Component.translatable("z80zhealthbar.settings.preview.barOff").getString(),
                    cx, boxTop + boxH / 2 - 4, 0xFF909090);
            popupBaseY = feetY - (mobHeight + 1.2f) * zb;
        } else switch (cfg.entityStyleParsed()) {
            case MOBHEALTHBAR -> {
                // 世界路径约定：内部自带 T(高度抬升)×名牌朝向×镜像。
                // 用"共轭对"抵消内部朝向旋转（预乘 Qf⁻¹ + 覆写 Qf）→ 净旋转=恒等，
                // 避免旋转把行沿深度轴倾斜、沉到面板背景后被深度测试裁掉（"只显示一半"的根因）
                var qf = new org.joml.Quaternionf().rotationYXZ(
                        (float) Math.toRadians(180f), 0f, 0f);
                var savedOrient = new org.joml.Quaternionf(dispatcher.cameraOrientation());
                dispatcher.overrideCameraOrientation(qf);
                pose.pushPose();
                pose.translate(cx, feetY, 0);
                pose.mulPose(new org.joml.Quaternionf(qf).conjugate());
                pose.scale(-zb, -zb, zb);
                MobHealthBarStyle.render(snap, pose, buffer, 0xF000F0, 1.0f);
                pose.popPose();
                dispatcher.overrideCameraOrientation(savedOrient);
                popupBaseY = feetY - (mobHeight + (float) cfg.styleA.heightOffset) * zb - 30;
            }
            case MOBPLAQUES -> {
                float ps = (float) cfg.plaqueStyle.plaqueScale;
                float gpx = 0.025f * ps * zb; // 牌匾像素 → GUI（1:1 世界比例——此前 1.5× 观察
                // 放大使三个样式的预览大小不可横向比较,用户据此误判牌匾"大一圈"）
                float anchorY = feetY - (mobHeight + 0.5f) * zb;
                // 轴对齐缩放；z 取负：渲染器内部的 -z 层间偏移（填充/描边层）翻回相机侧，避免沉入面板背景被深度裁掉
                pose.pushPose();
                pose.translate(cx, anchorY, 0);
                pose.scale(gpx, gpx, -gpx);
                MobDisplayRenderer.drawPlaqueRows(snap, pose, buffer, 0xF000F0, 1.0f, font, ps);
                pose.popPose();
                popupBaseY = anchorY - 30;
            }
            case ASTEORBAR -> {
                float p = 0.025f * (float) cfg.barStyle.barScale * zb; // 每条像素的 GUI 尺寸（1:1 世界比例）
                float barBottomY = feetY - (mobHeight + (float) cfg.barStyle.barOffsetY) * zb;
                pose.pushPose();
                pose.translate(cx, barBottomY, 0);
                // z 取负：内部 -z 层间偏移（填充/吸收环/边框）翻回相机侧——否则整条只剩空槽
                pose.scale(p, p, -p);
                var health = new HealthDisplayRenderer();
                int barW = health.getBarWidth(snap);
                int barH = cfg.barStyle.barHalfHeight * 2;
                health.renderBar(pose, buffer, font, snap, -barW / 2, -barH, barW, 1.0f,
                        0.025f * (float) cfg.barStyle.barScale);
                pose.popPose();
                popupBaseY = barBottomY - barH * p - 14;
            }
            case OFF -> {
                g.drawCenteredString(font,
                        Component.translatable("z80zhealthbar.settings.preview.off").getString(),
                        cx, boxTop + boxH / 2 - 4, 0xFF909090);
            }
        }

        // 3) 伤害跳字（忠实移植 DamagePopupRenderer：主题配色/运动轨迹/出生弹跳/淡出/描边/底板，
        //    5 次随机掉血演示，随配置实时变化）
        if (cfg.entityStyleParsed() != EntityHealthStyle.OFF && dp.enabled) {
            String theme = dp.themeParsed();
            String motion = dp.motionParsed();
            float risePx = switch (theme) {
                case "APEX" -> 16f;
                case "TACTICAL" -> 24f;
                case "WARFRAME" -> 34f;
                case "MINIMAL" -> 12f;
                default -> 20f;
            };
            boolean plate = theme.equals("APEX") || theme.equals("WARFRAME");
            boolean outline = theme.equals("TACTICAL") || theme.equals("CLASSIC");

            // 存活中的命中（时间正序）
            int alive = 0;
            for (int i = 0; i < 5; i++) {
                long age = tCycle - births[i];
                if (age >= 0 && age <= lifeMs) alive++;
            }
            int seen = 0;
            for (int i = 0; i < 5; i++) {
                long age = tCycle - births[i];
                if (age < 0 || age > lifeMs) continue;
                float tt = Math.min(1f, age / (float) lifeMs);
                float rise = 1f - (1f - tt) * (1f - tt); // easeOutQuad
                float punch = 1f + 0.45f * (float) Math.sin(
                        Math.min(1f, age / 180f) * (float) Math.PI); // 出生弹跳
                float alpha = tt < 0.7f ? 1f : Math.max(0f, 1f - (tt - 0.7f) / 0.3f);
                int a255 = (int) (255 * alpha);
                if (a255 <= 0) continue;
                boolean big = (float) dmg[i] / maxHp >= 0.2f;

                // 随机角度跳出:演示跳字以序号推导种子,与 DamagePopupRenderer.seedToUnit 同公式
                float launchSin = 0f, launchCos = 1f;
                float coneDeg = (float) dp.launchAngleDegrees;
                if (coneDeg > 0f && (motion.equals("RISE") || motion.equals("ARC"))) {
                    float launchRad = (float) Math.toRadians(
                            (DamagePopupRenderer.seedToUnit((int) (cycleIdx * 31L + i * 17L)) * 2f - 1f) * coneDeg);
                    launchSin = (float) Math.sin(launchRad);
                    launchCos = (float) Math.cos(launchRad);
                }

                // 运动轨迹（像素）——与 DamagePopupRenderer 逐式一致
                float yPx;
                float xPx = 0f;
                float worldDropPx = 0f;
                switch (motion) {
                    case "ARC" -> {
                        float riseUp = (float) Math.sin(Math.min(1f, tt * 1.8f) * (float) (Math.PI / 2));
                        float fall = Math.min(1f, Math.max(0f, (tt - 0.45f) / 0.55f));
                        yPx = -risePx * riseUp * launchCos;
                        xPx = launchSin * risePx * rise;
                        worldDropPx = (mobHeight + 0.1f) * zb * fall * fall; // 坠落至脚底
                    }
                    case "STACK" -> yPx = -12f * (alive - 1 - seen); // 顶旧底新
                    case "CUMULATIVE" -> yPx = 0;
                    default -> { // RISE:沿发射方向的直线漂浮
                        yPx = -risePx * rise * launchCos;
                        xPx = launchSin * risePx * rise;
                    }
                }
                float drift = theme.equals("TACTICAL")
                        ? (((i * 7) % 13) / 13f - 0.5f) * 12f * rise : 0f; // ±6px 确定性散布

                // 文本与配色（移植 drawPopup）
                String main;
                String sub = null;
                int mainColor;
                if (theme.equals("APEX")) {
                    mainColor = ColorHelper.parseColor(dp.colorHealth);
                    main = MobHealthBarStyle.formatNumber(healthPart[i]);
                    if (absPart[i] > 0.01f) sub = "+" + MobHealthBarStyle.formatNumber(absPart[i]);
                } else {
                    mainColor = switch (theme) {
                        case "TACTICAL" -> big ? ColorHelper.parseColor(dp.colorBigHit)
                                : ColorHelper.parseColor(dp.colorHealth);
                        case "MINIMAL" -> ColorHelper.parseColor(dp.colorHealth);
                        default -> ColorHelper.parseColor(dp.colorHealth); // 演示伤害无类别 → 生命色兜底
                    };
                    main = MobHealthBarStyle.formatNumber(dmg[i]);
                }
                float baseScaleF = switch (theme) {
                    case "WARFRAME" -> 1.15f;
                    case "MINIMAL" -> 0.85f;
                    case "TACTICAL" -> big ? 1.25f : 1.0f;
                    default -> 1.0f;
                };

                // 绘制（GUI 空间）：底板 → 描边 → 主字 → 吸收小字
                pose.pushPose();
                pose.translate(cx, popupBaseY + worldDropPx, 0);
                float sc = (float) dp.scale * baseScaleF * punch;
                pose.scale(sc, sc, 1f);
                pose.translate(drift + xPx, yPx, 0);
                var mat = pose.last().pose();
                if (plate) {
                    int w = Math.max(font.width(main), sub == null ? 0 : (int) (font.width(sub) * 0.8f));
                    g.fill(-w / 2 - 3, -2, w / 2 + 3, sub != null ? 21 : 11,
                            ColorHelper.modifyAlpha(0x8C10141C, a255));
                    g.fill(-w / 2 - 3, -2, w / 2 + 3, -1, ColorHelper.modifyAlpha(0x30FFFFFF, a255));
                }
                if (outline) {
                    int dark = ColorHelper.modifyAlpha(0xFF101014, a255);
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                        font.drawInBatch(main, -font.width(main) / 2f + d[0], d[1], dark, false,
                                mat, buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                    }
                }
                font.drawInBatch(main, -font.width(main) / 2f, 0,
                        ColorHelper.modifyAlpha(mainColor, a255), false, mat, buffer,
                        Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                if (sub != null) {
                    font.drawInBatch(sub, -font.width(sub) / 2f, 11,
                            ColorHelper.modifyAlpha(ColorHelper.parseColor(dp.colorAbsorbed), a255),
                            false, mat, buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                }
                pose.popPose();
                seen++;
            }
        }
        g.flush();
    }

    private void renderDone(GuiGraphics g, int mouseX, int mouseY) {
        int bw = 150;
        int bx = width / 2 - bw / 2;
        int by = height - 26;
        int bg = doneHovered(mouseX, mouseY) ? 0x80606A78 : 0x60303038;
        g.fill(bx, by, bx + bw, by + 18, bg);
        g.renderOutline(bx, by, bw, 18, 0x60FFFFFF);
        Component label = CommonComponentsGUI_DONE();
        g.drawCenteredString(font, label, width / 2, by + 5, 0xFFFFFFFF);
    }

    private static Component CommonComponentsGUI_DONE() {
        return net.minecraft.network.chat.CommonComponents.GUI_DONE;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 标签页
        int n = Page.values().length;
        int tabW = Math.min(96, (width - 16) / n);
        int x0 = (width - tabW * n) / 2;
        if (my >= 2 && my < 22) {
            for (int i = 0; i < n; i++) {
                if (mx >= x0 + i * tabW && mx < x0 + i * tabW + tabW - 2) {
                    switchPage(Page.values()[i]);
                    return true;
                }
            }
        }
        // 完成按钮
        if (doneHovered(mx, my)) {
            onClose();
            return true;
        }
        // 预览面板的敌方/友方切换 + 暂停/全屏按钮
        if (previewToggle != null && previewToggle.mouseClicked(mx, my, btn)) return true;
        if (previewPauseBtn != null && previewPauseBtn.mouseClicked(mx, my, btn)) return true;
        if (previewFullscreenBtn != null && previewFullscreenBtn.mouseClicked(mx, my, btn)) return true;
        // 滚动条（命中轨道 = 开始拖拽；点击空白轨道 = 拇指中心跳到该处）
        if (btn == 0 && maxScroll() > 0 && mx >= scrollbarX() - 2 && mx <= scrollbarX() + 5
                && my >= scrollbarTrackTop() && my <= scrollbarTrackBottom()) {
            int trackH = scrollbarTrackBottom() - scrollbarTrackTop();
            double ratio = (double) viewH / contentH;
            int thumbH = Math.max(12, (int) (trackH * ratio));
            int thumbY = scrollbarTrackTop() + (int) ((trackH - thumbH) * (scroll / maxScroll()));
            draggingScrollbar = true;
            if (my < thumbY || my > thumbY + thumbH) {
                scroll = Math.max(0, Math.min(maxScroll(),
                        (my - scrollbarTrackTop() - thumbH / 2.0) / (trackH - thumbH) * maxScroll()));
                scrollTarget = scroll; // 点击跳转即时，不经过平滑
            }
            scrollbarGrab = my - (scrollbarTrackTop() + (trackH - thumbH) * (scroll / maxScroll()));
            return true;
        }
        // 行控件
        for (Row row : rows) {
            if (row.mouseClicked(mx, my, btn)) {
                pressedRow = row;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingScrollbar && maxScroll() > 0) {
            int trackH = scrollbarTrackBottom() - scrollbarTrackTop();
            double ratio = (double) viewH / contentH;
            int thumbH = Math.max(12, (int) (trackH * ratio));
            scroll = Math.max(0, Math.min(maxScroll(),
                    (my - scrollbarGrab - scrollbarTrackTop()) / (double) (trackH - thumbH) * maxScroll()));
            scrollTarget = scroll; // 拖动跟手，不经过平滑
            return true;
        }
        // 拖拽只投给按下的那一行，滑条才能持续收到移动事件
        if (pressedRow != null) {
            return pressedRow.mouseDragged(mx, my, btn, dx, dy);
        }
        for (Row row : rows) {
            if (row.mouseDragged(mx, my, btn, dx, dy)) return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    /** 滚动条几何（与 render 中的绘制保持同一套公式） */
    private int scrollbarX() {
        return contentX + contentW + 2;
    }

    private int scrollbarTrackTop() {
        return HEADER_H - 2;
    }

    private int scrollbarTrackBottom() {
        return height - FOOTER_H + 2;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (previewToggle != null) previewToggle.mouseReleased(mx, my, btn);
        if (previewPauseBtn != null) previewPauseBtn.mouseReleased(mx, my, btn);
        if (previewFullscreenBtn != null) previewFullscreenBtn.mouseReleased(mx, my, btn);
        boolean handled = pressedRow != null;
        if (pressedRow != null) {
            pressedRow.mouseReleased(mx, my, btn);
        } else {
            for (Row row : rows) {
                handled |= row.mouseReleased(mx, my, btn);
            }
        }
        pressedRow = null;
        draggingScrollbar = false;
        if (handled) {
            ConfigManager.saveConfig(); // 滑杆拖动结束后统一落盘
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScroll() > 0) {
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - delta * 18));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            switchPage(Page.values()[(page.ordinal() + 1) % Page.values().length]);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
