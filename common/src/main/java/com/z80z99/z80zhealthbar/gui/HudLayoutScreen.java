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

import java.util.ArrayDeque;
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
    /** 面板宽（仿 Windows 拖右缘/右下角调整;持久化 layout().panelW） */
    private int panelW = PANEL_W;
    /** 面板高覆盖值（拖下缘/右下角设定;-1 = 按内容自适应;持久化 layout().panelH） */
    private int panelHOv = -1;
    private static final int PANEL_MIN_W = 156, PANEL_MAX_W = 440;
    /** 正在调整大小:0=无 1=右缘 2=下缘 3=右下角 */
    private int panelResizing;
    private boolean panelDragging;
    /** 面板滚动条拖拽中 */
    private boolean panelScrollbarDragging;
    private int panelGrabX, panelGrabY;
    /** 面板滚动（px,平滑逼近目标;滚轮悬停面板时滚动,面板外滚轮仍是组件缩放） */
    private float panelScroll, panelScrollTarget;
    private int panelContentH;
    /** 当前选择的预设布局（点"应用预设"按钮生效） */
    private String presetSel = "MODERN";
    /** hotbar 组件当前编辑的槽位（0..8） */
    private int hotbarEditSlot = 0;
    /** hotbar 槽位拖拽状态（预览中直接拖单个槽） */
    private int hotbarDragSlot = -1;
    private int hbGrabX, hbGrabY, hbStartOffX, hbStartOffY;
    private float hbDragScale = 1f;
    /** 本次槽拖拽是否已产生真实位移（区分"点击进页"与"拖动"——纯点击松手后进槽编辑页） */
    private boolean hbDragMoved = false;

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

    /** 选中键（主组件或 .text/.icon 子件）的解析后屏幕位置（盒体左上）;求解失败返回 null。
     *  位置行按此显示/换算——X/Y 曾显示"相对锚点偏移"（不同锚点下全为 0 位置却各异,用户困惑） */
    private int[] resolvedPos(String key) {
        try {
            Map<String, int[]> sizes = CustomHudRenderer.measureAll(layout(), minecraft == null ? null : minecraft.player);
            Map<String, HudLayoutSolver.Box> boxes = HudLayoutSolver.solve(layout(), sizes, width, height);
            var b = boxes.get(key);
            return b == null ? null : new int[]{b.x(), b.y()};
        } catch (Exception ex) {
            return null;
        }
    }

    /** hotbar 第 idx 槽的解析后屏幕位置（与渲染/命中同式:盒体原点 + 1px 边距 + 槽格累计宽 + 偏移,乘组件缩放） */
    private int[] hotbarSlotResolved(ComponentLayout c, int idx) {
        try {
            Map<String, int[]> sizes = CustomHudRenderer.measureAll(layout(), minecraft == null ? null : minecraft.player);
            Map<String, HudLayoutSolver.Box> boxes = HudLayoutSolver.solve(layout(), sizes, width, height);
            var box = boxes.get(baseKey(selected));
            if (box == null) return null;
            float k = (float) c.scale;
            int px = 1;
            for (int i = 0; i < idx; i++) px += Math.max(8, Math.min(64, c.hotbarSlot(i).slotW));
            var s = c.hotbarSlot(idx);
            return new int[]{box.x() + Math.round((px + s.offsetX) * k), box.y() + Math.round((1 + s.offsetY) * k)};
        } catch (Exception ex) {
            return null;
        }
    }

    // ================= 面板模型 =================

    /** 面板行内控件（记录相对面板左缘的 x） */
    private record PW(AbstractWidget widget, int relX) {}

    /** 面板页：LIST = 清单+全局操作;EDIT = 对象参数页;ADD = 类型选择;GROUP = 分组成员;SUB_TEXT/SUB_ICON = 子组件页 */
    private enum PanelPage { LIST, EDIT, ADD, GROUP, SUB_TEXT, SUB_ICON, HOTBAR_SLOT, DATASOURCE_LIST, DATASOURCE_TYPE }

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
    /** 设置说明浮框（模态式,彻底规避遮挡）:
     *  全屏轻压暗 → 弹框弹在面板**外侧**（面板偏右→左侧,偏左→右侧,与 (?) 行对齐）——
     *  与面板行文字零重叠;两侧都放不下才回退 (?) 下方。绘制于 render 末尾（最后一层） */
    private void renderHelpBox(GuiGraphics g) {
        var lines = font.split(Component.translatable(helpKey), Math.min(330, width - 60));
        int lineH = 10, pad = 6;
        int textW = 0;
        for (var l : lines) textW = Math.max(textW, font.width(l));
        int boxW = textW + pad * 2;
        int boxH = lines.size() * lineH + pad * 2;
        // (?) 命中位置（无命中记录时回退屏幕中上）
        int hx = width / 2, hy = height / 2;
        for (var h : helpHits) {
            if (h.key().equals(helpKey)) {
                hx = h.x();
                hy = h.y();
                break;
            }
        }
        // 横向:优先面板外侧;外侧放不下换另一侧;再不行回退贴 (?) 右下
        boolean panelOnRight = panelX + panelW / 2 > width / 2;
        int bx = panelOnRight ? panelX - boxW - 10 : panelX + panelW + 10;
        if (bx < 4 || bx + boxW > width - 4) {
            bx = panelOnRight ? panelX + panelW + 10 : panelX - boxW - 10;
        }
        if (bx < 4 || bx + boxW > width - 4) {
            bx = Math.max(4, Math.min(hx + 12, width - boxW - 4));
        }
        int by = Math.max(4, Math.min(hy - 2, height - boxH - 4));
        // 全屏轻压暗:弹框与界面文字对比度彻底分离;点击任意处即关闭
        g.fill(0, 0, width, height, 0x28000000);
        // 连接线:(?) 标记 → 弹框边缘,保持指向关系
        int lineY = hy + 4;
        int lineX0 = Math.min(hx + 10, bx > hx ? bx : bx + boxW);
        int lineX1 = Math.max(hx + 10, bx > hx ? bx : bx + boxW);
        g.fill(lineX0, lineY, lineX1, lineY + 1, 0x607FD4FF);
        g.fill(bx + 2, by + 2, bx + boxW + 2, by + boxH + 2, 0x50000000);
        g.fill(bx, by, bx + boxW, by + boxH, 0xFF0E1218);
        g.renderOutline(bx, by, boxW, boxH, 0x907FD4FF);
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

    // ===== 撤销/重做（快照式）：undoWatch 每 150ms 轮询配置 JSON,变化突发静默 350ms 后
    // 把"变化前状态"压入撤销栈——拖拽/连点/连续输入自动并作一步,无需在各改动点埋钩子 =====
    private static final int UNDO_LIMIT = 50;
    private static final long UNDO_POLL_MS = 150, UNDO_QUIET_MS = 350;
    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private String lastSeenJson;   // 上次轮询到的配置状态（提交态或突发中的最新态）
    private String burstPrev;      // 进行中突发的起点状态（null = 无突发）
    private long burstEndAt, lastUndoPoll;

    /** 撤销监听（render 每帧调用,内部按 UNDO_POLL_MS 限频） */
    private void undoWatch() {
        long now = System.currentTimeMillis();
        if (now - lastUndoPoll < UNDO_POLL_MS) return;
        lastUndoPoll = now;
        String cur = ConfigManager.snapshotJson();
        if (!cur.equals(lastSeenJson)) {
            if (burstPrev == null) burstPrev = lastSeenJson; // 记住突发起点
            lastSeenJson = cur;
            burstEndAt = now;
            return;
        }
        // 无新变化且静默期满 → 提交一步撤销
        if (burstPrev != null && now - burstEndAt >= UNDO_QUIET_MS) {
            undoStack.push(burstPrev);
            while (undoStack.size() > UNDO_LIMIT) undoStack.removeLast();
            redoStack.clear(); // 新改动使重做失效
            burstPrev = null;
        }
    }

    boolean canUndo() { return burstPrev != null || !undoStack.isEmpty(); }

    boolean canRedo() { return !redoStack.isEmpty(); }

    /** 撤销：进行中的突发先提交（拖拽中 Ctrl+Z = 撤销整个拖拽）,再回退一步 */
    private void undo() {
        if (burstPrev != null) {
            undoStack.push(burstPrev);
            while (undoStack.size() > UNDO_LIMIT) undoStack.removeLast();
            redoStack.clear();
            burstPrev = null;
        }
        if (undoStack.isEmpty()) return;
        redoStack.push(ConfigManager.snapshotJson());
        applyUndoSnapshot(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(ConfigManager.snapshotJson());
        while (undoStack.size() > UNDO_LIMIT) undoStack.removeLast();
        applyUndoSnapshot(redoStack.pop());
    }

    /** 还原快照并同步监听基准（否则还原态会被当成新改动再入栈,撤销变往返切换） */
    private void applyUndoSnapshot(String json) {
        ConfigManager.restoreJson(json);
        lastSeenJson = json;
        burstPrev = null;
        dirty = true;
        clampPanel();
        layoutPanel();
        rebuildWidgets();
    }

    private void markDirty() {
        dirty = true;
    }

    private void saveChanges() {
        ConfigManager.saveConfig();
        dirty = false;
        pendingUnsaved = false;
    }

    /** 放弃改动：全量还原到进入编辑器时的快照（实例被替换 → 面板位置需按新配置复位）。
     *  同时清空撤销/重做——放弃即"回到开工前",旧步进不应再可撤 */
    private void discardChanges() {
        ConfigManager.restoreJson(savedSnapshot);
        undoStack.clear();
        redoStack.clear();
        burstPrev = null;
        lastSeenJson = savedSnapshot;
        HudLayoutConfig l = layout();
        panelX = l.panelX < 0 ? width - panelW - 6 : l.panelX;
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
        /** 颜色行小色块（ARGB;0=未设置→画空框;null=非颜色行不画）——色值不再拼进标签 */
        Supplier<Integer> swatchArgb;
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

        /** 动态 note 行（每帧取 labelDyn 文本,如数据源实时读数诊断） */
        static PEntry dynNote(Supplier<String> text) {
            PEntry e = new PEntry(null, null, null);
            e.note = true;
            e.labelDyn = text;
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
            lastSeenJson = savedSnapshot; // 撤销监听基准 = 进入时状态
        }
        buildPanelEntries();
        // 面板默认右上（曾拖动过则用记录值）
        // 面板尺寸（仿 Windows 拖缘调整,持久化;非法值回退默认）
        panelW = layout().panelW >= PANEL_MIN_W && layout().panelW <= PANEL_MAX_W ? layout().panelW : PANEL_W;
        panelHOv = layout().panelH > 0 ? layout().panelH : -1;
        panelX = layout().panelX < 0 ? width - panelW - 6 : layout().panelX;
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
                    () -> Component.translatable("z80zhealthbar.editor.save_exit").getString(),
                    () -> {
                        saveChanges();
                        ConfigManager.saveConfig();
                        if (minecraft != null) minecraft.setScreen(parent);
                    }));
            panelEntries.add(cycler("z80zhealthbar.editor.discard",
                    () -> Component.translatable("z80zhealthbar.editor.discard_exit").getString(),
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
            case HOTBAR_SLOT -> buildHotbarSlotPage();
            case DATASOURCE_LIST -> buildDatasourceList();
            case DATASOURCE_TYPE -> buildDatasourceType();
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
        e.widgets.add(new PW(b, panelW - 4 - CYCLE_W));
        return e;
    }

    /** 颜色行：标签 + 当前色小色块（空 = 继承默认→空框）,点击"编辑"打开颜色轮盘;写回即持久化。
     *  色值不再拼进标签——"标签: 8位hex"串起来曾溢出到右侧按钮下（用户反馈"文字显示不全"） */
    private PEntry colorRow(String labelKey, Supplier<String> get, java.util.function.Consumer<String> set) {
        PEntry e = listRow(() -> Component.translatable(labelKey).getString(),
                "z80zhealthbar.editor.color.edit", () -> minecraft.setScreen(new ColorWheelScreen(this,
                        Component.translatable(labelKey).getString(), get.get(), v -> {
                            set.accept(v);
                            markDirty();
                        })));
        e.swatchArgb = () -> parseColorSafe(get.get());
        return e;
    }

    /** "#RRGGBB"/"#AARRGGBB"（容忍缺 #）→ ARGB;空/非法 → 0（=继承,色块画空框） */
    private static int parseColorSafe(String s) {
        if (s == null) return 0;
        String h = s.replace("#", "").trim();
        try {
            if (h.length() == 6) return 0xFF000000 | (int) Long.parseLong(h, 16);
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        } catch (NumberFormatException ignored) {
        }
        return 0;
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
        e.widgets.add(new PW(b, panelW - 4 - 120));
        return e;
    }

    /** 子组件页返回行：回**父组件编辑页**——此前用 backRow 直跳最开始的组件列表,层级断裂（实测反馈） */
    private PEntry backToEditRow() {
        PEntry e = new PEntry(null, null, null);
        e.labelDyn = () -> "";
        FlatButton b = new FlatButton(120, BTN_H,
                () -> Component.translatable("z80zhealthbar.editor.back_component"),
                () -> {
                    panelPage = PanelPage.EDIT;
                    rebuildWidgets();
                });
        addRenderableWidget(b);
        e.widgets.add(new PW(b, panelW - 4 - 120));
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
                    markDirty();
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
                    () -> Component.translatable("z80zhealthbar.editor.save").getString(),
                    this::saveChanges));
            panelEntries.add(cycler("z80zhealthbar.editor.discard",
                    () -> Component.translatable("z80zhealthbar.editor.discard").getString(),
                    () -> {
                        discardChanges();
                        panelPage = PanelPage.LIST;
                        rebuildWidgets();
                    }));
            // 撤销/重做（快照式;按钮显示快捷键提示;进行中的拖拽/输入并作一步）
            if (canUndo()) {
                panelEntries.add(cycler("z80zhealthbar.editor.undo", () -> "Ctrl+Z", this::undo));
            }
            if (canRedo()) {
                panelEntries.add(cycler("z80zhealthbar.editor.redo", () -> "Ctrl+Y", this::redo));
            }
        }
        panelEntries.add(doneRow());
    }

    /** 二段确认行（防误触）：首次点击变为"再次点击确认",3 秒内再点执行,超时还原 */
    private long confirmUntil;

    private PEntry confirmRow(String labelKey, Runnable action) {
        return cycler(labelKey, () -> System.currentTimeMillis() < confirmUntil
                        ? Component.translatable("z80zhealthbar.editor.confirm_reset").getString()
                        : Component.translatable("z80zhealthbar.editor.reset").getString(),
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
                        "compat", "saturation_bar", "hotbar"},
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
                    // hotbar 是单组件（内含 9 槽）:已存在则直接选中,不重复添加
                    if (HudLayoutConfig.HOTBAR.equals(type) && layout().components.containsKey(HudLayoutConfig.HOTBAR)) {
                        selected = HudLayoutConfig.HOTBAR;
                        panelPage = PanelPage.EDIT;
                        rebuildWidgets();
                        return;
                    }
                    String k = layout().addInstance(type);
                    if (k != null) {
                        selected = k;
                        panelPage = PanelPage.EDIT;
                    }
                    rebuildWidgets();
                }));
            }
            // 自定义数据源:「数据源…」入口行（专页承接:选择已有源 / 新建 → 数据类型）
            if ("z80zhealthbar.editor.cat_compat".equals(cat[0])) {
                panelEntries.add(listRow(
                        () -> Component.translatable("z80zhealthbar.editor.cat_datasource").getString(),
                        "z80zhealthbar.editor.add", () -> {
                            panelPage = PanelPage.DATASOURCE_LIST;
                            rebuildWidgets();
                        }));
            }
        }
    }

    /** 数据源 · 选择页:已建数据源列表（点击 = 添加对应条组件）+ 新建入口 */
    private void buildDatasourceList() {
        panelEntries.add(backRow());
        panelEntries.add(new PEntry("z80zhealthbar.editor.datasource.title", null, null));
        var cs = ConfigManager.getConfig().customSources;
        var list = cs == null ? null : cs.customSources;
        if (list != null && !list.isEmpty()) {
            for (var src : list) {
                if (src == null) continue;
                final String type = "compat_custom_" + src.id;
                final String dn = (src.enabled ? "" : "§8") + src.name()
                        + " §7[" + datasourceChannelName(src.channel) + "]";
                panelEntries.add(listRow(() -> dn, "z80zhealthbar.editor.add", () -> {
                    String k = layout().addInstance(type);
                    if (k != null) {
                        selected = k;
                        panelPage = PanelPage.EDIT;
                    }
                    rebuildWidgets();
                }));
            }
        } else {
            panelEntries.add(PEntry.note("z80zhealthbar.editor.datasource.empty"));
        }
        panelEntries.add(listRow(
                () -> Component.translatable("z80zhealthbar.editor.datasource.new").getString(),
                "z80zhealthbar.editor.add", () -> {
                    panelPage = PanelPage.DATASOURCE_TYPE;
                    rebuildWidgets();
                }));
    }

    /** 数据源 · 数据类型页:5 种读取通道,点选即创建数据源 + 条组件并进入编辑页 */
    private void buildDatasourceType() {
        panelEntries.add(backRow());
        panelEntries.add(new PEntry("z80zhealthbar.editor.datasource.type_title", null, null));
        for (int ch = 0; ch <= 4; ch++) {
            final int channel = ch;
            String dn = Component.translatable("z80zhealthbar.editor.datasource.type." + ch).getString();
            panelEntries.add(listRow(() -> dn, "z80zhealthbar.editor.add", () -> {
                var cs = ConfigManager.getConfig().customSources;
                if (cs == null) return;
                if (cs.customSources == null) cs.customSources = new java.util.ArrayList<>();
                if (cs.customSources.size() >= 32) return; // 上限保护(validator 同步)
                var s = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource();
                int n = 1;
                while (true) {
                    String candidate = "source_" + n;
                    boolean exists = false;
                    for (var o : cs.customSources) {
                        if (o != null && o.id.equals(candidate)) { exists = true; break; }
                    }
                    if (!exists) { s.id = candidate; break; }
                    n++;
                }
                s.displayName = "";
                s.channel = channel;
                cs.customSources.add(s);
                String k = layout().addInstance("compat_custom_" + s.id);
                if (k != null) {
                    selected = k;
                    panelPage = PanelPage.EDIT;
                }
                ConfigManager.saveConfig();
                rebuildWidgets();
            }));
        }
    }

    private static String datasourceChannelName(int ch) {
        return switch (ch) {
            case 0 -> "Capability";
            case 2 -> "Factory";
            case 3 -> "Player";
            case 4 -> "Item";
            default -> "Static";
        };
    }

    /** 组件类型 compat_custom_<id> → 绑定的数据源配置（找不到返回 null） */
    private static com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource datasourceOf(String selType) {
        String raw = selType.substring("compat_custom_".length());
        var cs = ConfigManager.getConfig().customSources;
        if (cs == null || cs.customSources == null) return null;
        for (var s : cs.customSources) {
            if (s != null && s.id.equals(raw)) return s;
        }
        return null;
    }

    /** 数据源参数行（compat_custom_* 编辑页内;全部复用现有行工厂,改即存） */
    private void buildDatasourceParams(com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource src) {
        panelEntries.add(new PEntry("z80zhealthbar.editor.datasource.params_section", null, null));
        panelEntries.add(textInput("z80zhealthbar.custom_sources.field.name",
                () -> src.displayName == null ? "" : src.displayName, v -> src.displayName = v));
        panelEntries.add(textInput("z80zhealthbar.custom_sources.field.mod",
                () -> src.modId == null ? "" : src.modId, v -> src.modId = v.trim()));
        panelEntries.add(cycler("z80zhealthbar.custom_sources.field.channel",
                () -> Component.translatable("z80zhealthbar.editor.datasource.type." + src.channel).getString(),
                () -> {
                    src.channel = (src.channel + 1) % 5;
                    markDirty();
                    rebuildWidgets();
                }));
        // 按通道动态参数行
        switch (src.channel) {
            case 0 -> {
                panelEntries.add(textInput("z80zhealthbar.custom_sources.field.holder",
                        () -> src.holderClass, v -> src.holderClass = v.trim()));
                panelEntries.add(textInput("z80zhealthbar.custom_sources.field.token_field",
                        () -> src.holderField, v -> src.holderField = v.trim()));
                panelEntries.add(textInput("z80zhealthbar.custom_sources.field.iface",
                        () -> src.ifaceClass, v -> src.ifaceClass = v.trim()));
                panelEntries.add(textInput("z80zhealthbar.custom_sources.field.read",
                        () -> src.readMethod, v -> src.readMethod = v.trim()));
            }
            case 1 -> {
                panelEntries.add(textInput("z80zhealthbar.custom_sources.field.holder",
                        () -> src.holderClass, v -> src.holderClass = v.trim()));
                panelEntries.add(textInput("z80zhealthbar.custom_sources.field.read",
                        () -> src.readMethod, v -> src.readMethod = v.trim()));
            }
            case 2 -> {
                panelEntries.add(textInput("z80zhealthbar.custom_sources.field.holder",
                        () -> src.holderClass, v -> src.holderClass = v.trim()));
                panelEntries.add(textInput("z80zhealthbar.custom_sources.field.factory",
                        () -> src.readMethod, v -> src.readMethod = v.trim()));
                panelEntries.add(textInput("z80zhealthbar.custom_sources.field.value_method",
                        () -> src.valueMethod, v -> src.valueMethod = v.trim()));
            }
            case 3 -> panelEntries.add(textInput("z80zhealthbar.custom_sources.field.player_method",
                    () -> src.readMethod, v -> src.readMethod = v.trim()));
            default -> {
                panelEntries.add(cycler("z80zhealthbar.custom_sources.field.item_mode",
                        () -> Component.translatable("z80zhealthbar.editor.datasource.item_mode." + src.itemMode).getString(),
                        () -> {
                            src.itemMode = (src.itemMode + 1) % 3;
                            markDirty();
                            rebuildWidgets();
                        }));
                if (src.itemMode != com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.ITEM_VANILLA_DURABILITY) {
                    panelEntries.add(textInput(src.itemMode == com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.ITEM_NBT_INT
                                    ? "z80zhealthbar.custom_sources.field.nbt_key"
                                    : "z80zhealthbar.custom_sources.field.item_method",
                            () -> src.readMethod, v -> src.readMethod = v.trim()));
                }
            }
        }
        panelEntries.add(textInput("z80zhealthbar.custom_sources.field.max_method",
                () -> src.maxMethod == null ? "" : src.maxMethod, v -> src.maxMethod = v.trim()));
        panelEntries.add(stepper1("z80zhealthbar.custom_sources.static_max_short",
                () -> src.staticMax, v -> src.staticMax = (float) v, 0, 1000, 5));
        panelEntries.add(colorRow("z80zhealthbar.custom_sources.color_short",
                () -> src.color == null ? "" : src.color, v -> src.color = v));
        panelEntries.add(toggle("z80zhealthbar.custom_sources.takeover_short",
                () -> src.takeover, v -> src.takeover = v));
        panelEntries.add(textInput("z80zhealthbar.custom_sources.field.overlay_id",
                () -> src.overlayId == null ? "" : src.overlayId, v -> src.overlayId = v.trim()));
        // 实时读数诊断（每帧刷新;配置拼写错误的主要发现途径）
        panelEntries.add(PEntry.dynNote(() -> Component.translatable("z80zhealthbar.custom_sources.test").getString()
                + ": " + datasourceDiagnose(src)));
    }

    /** 数据源实时读数诊断（与管理页同逻辑;成功 → 值/上限,失败 → 具体原因） */
    private String datasourceDiagnose(com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource src) {
        if (!src.enabled) return "§8" + Component.translatable("z80zhealthbar.custom_sources.diag.disabled").getString();
        if (!src.paramsComplete()) {
            return "§e" + Component.translatable("z80zhealthbar.custom_sources.diag.incomplete").getString();
        }
        if (minecraft == null || minecraft.player == null) return "§8—";
        var adapter = new com.z80z99.z80zhealthbar.compat.CustomSourceAdapter(src);
        if (!adapter.isAvailable()) {
            return "§c" + Component.translatable("z80zhealthbar.custom_sources.diag.mod_missing",
                    src.modId).getString();
        }
        Float v = adapter.readStat(src.id);
        if (v == null) {
            return "§c" + Component.translatable("z80zhealthbar.custom_sources.diag.read_failed").getString();
        }
        Float m = adapter.resolveMax(minecraft.player);
        return "§a" + String.format(Locale.ROOT, "%.1f", v)
                + (m == null ? "" : " / " + String.format(Locale.ROOT, "%.1f", m));
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
    /** hotbar 槽位编辑页（独立子组件页:参数只作用于该槽;**无删除/复制动作**——槽是固定子件） */
    private void buildHotbarSlotPage() {
        ComponentLayout c = layout().get(baseKey(selected));
        panelEntries.add(backToEditRow()); // 子组件页:返回父组件编辑页(不跳组件列表)
        panelEntries.add(new PEntry(null, "z80zhealthbar.editor.hotbar.slot_title",
                () -> Component.translatable("z80zhealthbar.editor.hotbar.slot.fmt",
                        hotbarEditSlot + 1).getString()));
        java.util.function.Supplier<com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig.ComponentLayout.HotbarSlot> hsLive =
                () -> c.hotbarSlot(hotbarEditSlot);
        panelEntries.add(toggle("z80zhealthbar.editor.hotbar.hidden",
                () -> hsLive.get().hidden, v -> hsLive.get().hidden = v));
        // 位置 = 解析后屏幕坐标（拖拽/步进同域换算;求解不可用时回退显示原始偏移）
        panelEntries.add(stepper("z80zhealthbar.editor.pos_x",
                () -> {
                    int[] p = hotbarSlotResolved(c, hotbarEditSlot);
                    return p != null ? p[0] : hsLive.get().offsetX;
                },
                v -> {
                    int[] p = hotbarSlotResolved(c, hotbarEditSlot);
                    hsLive.get().offsetX = p != null
                            ? hsLive.get().offsetX + (int) Math.round(v) - p[0]
                            : (int) Math.round(v);
                }, -4096, 4096, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.pos_y",
                () -> {
                    int[] p = hotbarSlotResolved(c, hotbarEditSlot);
                    return p != null ? p[1] : hsLive.get().offsetY;
                },
                v -> {
                    int[] p = hotbarSlotResolved(c, hotbarEditSlot);
                    hsLive.get().offsetY = p != null
                            ? hsLive.get().offsetY + (int) Math.round(v) - p[1]
                            : (int) Math.round(v);
                }, -4096, 4096, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.hotbar.slot_w",
                () -> hsLive.get().slotW, v -> hsLive.get().slotW = (int) Math.round(v), 8, 64, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.hotbar.slot_h",
                () -> hsLive.get().slotH, v -> hsLive.get().slotH = (int) Math.round(v), 8, 64, 1));
        panelEntries.add(stepper1("z80zhealthbar.editor.component_scale",
                () -> hsLive.get().scale, v -> hsLive.get().scale = v, 0.25, 4.0, 0.05));
        panelEntries.add(stepper("z80zhealthbar.editor.opacity",
                () -> hsLive.get().opacity, v -> hsLive.get().opacity = (int) Math.round(v), 0, 100, 5));
        panelEntries.add(stepper("z80zhealthbar.editor.idle_fade",
                () -> hsLive.get().idleFadeSecs, v -> hsLive.get().idleFadeSecs = (int) Math.round(v), 0, 60, 1));
        // 背景与渐变
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.color", null, null));
        panelEntries.add(colorRow("z80zhealthbar.editor.hotbar.bg", () -> hsLive.get().bgColor, v -> hsLive.get().bgColor = v));
        panelEntries.add(colorRow("z80zhealthbar.editor.hotbar.bg2", () -> hsLive.get().bgColor2, v -> hsLive.get().bgColor2 = v));
        // 渐变方向 → 角度制（0=左→右,90=上→下,顺时针;步进 5°）
        panelEntries.add(stepper("z80zhealthbar.editor.hotbar.grad_angle",
                () -> hsLive.get().gradientAngle,
                v -> hsLive.get().gradientAngle = (int) Math.round(v), 0, 359, 5));
        // 边框
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.border", null, null));
        panelEntries.add(cycler("z80zhealthbar.editor.hotbar.border",
                () -> Component.translatable("z80zhealthbar.editor.hotbar.border." + hsLive.get().borderStyle).getString(),
                () -> {
                    hsLive.get().borderStyle = (hsLive.get().borderStyle + 1) % 4;
                    markDirty();
                }));
        panelEntries.add(colorRow("z80zhealthbar.editor.hotbar.border_color",
                () -> hsLive.get().borderColor, v -> hsLive.get().borderColor = v));
        panelEntries.add(listRow(
                () -> Component.translatable("z80zhealthbar.editor.hotbar.reset_slot").getString(),
                "z80zhealthbar.editor.hotbar.reset", () -> {
                    c.hotbarSlot(hotbarEditSlot);
                    c.hotbarSlots.set(hotbarEditSlot, new com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig.ComponentLayout.HotbarSlot());
                    markDirty();
                }));
    }

    private void buildSubText() {
        ComponentLayout c = layout().get(baseKey(selected));
        panelEntries.add(backToEditRow()); // 子组件页:返回父组件编辑页(不跳组件列表)
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
        panelEntries.add(backToEditRow()); // 子组件页:返回父组件编辑页(不跳组件列表)
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

        // 对该组件类型**无效果**的设置不显示（实测反馈:快捷栏没有条填充色等条形专属参数）
        boolean isHotbar = HudLayoutConfig.typeOf(selected, c).equals(HudLayoutConfig.HOTBAR);

        // ---- 颜色（填充/文本/卡片底,轮盘编辑;空 = 继承全局/状态自动变色）----
        // hotbar 用槽级背景色,条/文本/卡片色全部无效 → 整区隐藏
        if (!isHotbar) {
            panelEntries.add(new PEntry("z80zhealthbar.editor.section.color", null, null));
            panelEntries.add(colorRow("z80zhealthbar.editor.color.fill", () -> c.colorFill, v -> c.colorFill = v));
            panelEntries.add(colorRow("z80zhealthbar.editor.color.fill2", () -> c.colorFill2, v -> c.colorFill2 = v));
            panelEntries.add(colorRow("z80zhealthbar.editor.color.text", () -> c.colorText, v -> c.colorText = v));
            panelEntries.add(colorRow("z80zhealthbar.editor.color.card", () -> c.colorCard, v -> c.colorCard = v));
        }

        // ---- 组件（位置/缩放/间距:所有显示形式通用;原先散在底栏,现集中入面板） ----
        panelEntries.add(new PEntry("z80zhealthbar.editor.section.component", null, null));
        if (c.modeParsed() == HudLayoutConfig.ComponentMode.BAR && !isHotbar) {
            // 三部件独立开关（自由组合:只留图标/只留文本/条+图标+文本）;hotbar 无条形三部件概念
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
                    markDirty();
                    rebuildWidgets();
                }));
        // 位置 = 解析后屏幕坐标（与拖拽同域;不同锚点/堆叠下不再是"全是 0 却位置不同"的相对偏移）。
        // 换算:新偏移 = 旧偏移 + (目标绝对值 − 当前解析值);求解不可用回退直接写偏移
        panelEntries.add(stepper("z80zhealthbar.editor.pos_x",
                () -> {
                    int[] p = resolvedPos(selected);
                    return p != null ? p[0] : dragStartX(selected);
                },
                v -> {
                    int[] p = resolvedPos(selected);
                    applyDrag(selected,
                            p != null ? dragStartX(selected) + (int) Math.round(v) - p[0] : (int) Math.round(v),
                            dragStartY(selected));
                }, -4096, 4096, 1));
        panelEntries.add(stepper("z80zhealthbar.editor.pos_y",
                () -> {
                    int[] p = resolvedPos(selected);
                    return p != null ? p[1] : dragStartY(selected);
                },
                v -> {
                    int[] p = resolvedPos(selected);
                    applyDrag(selected, dragStartX(selected),
                            p != null ? dragStartY(selected) + (int) Math.round(v) - p[1] : (int) Math.round(v));
                }, -4096, 4096, 1));
        panelEntries.add(stepper1("z80zhealthbar.editor.component_scale",
                () -> c.scale, v -> c.scale = v, 0.5, 2.0, 0.1));
        if (!isHotbar) {
            // 间距仅影响同锚点堆叠排布,hotbar 单组件 9 槽自排布,无堆叠语义
            panelEntries.add(stepper("z80zhealthbar.editor.spacing",
                    () -> c.spacing, v -> c.spacing = (int) Math.round(v), 0, 12, 1));
        }
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

        // ---- 条形（hotbar 无条宽/条高概念——尺寸在槽位级,主页面隐藏此区） ----
        if (!isHotbar) {
            panelEntries.add(new PEntry("z80zhealthbar.editor.section.bar", null, null));
            panelEntries.add(stepper("z80zhealthbar.editor.bar_width",
                    () -> c.barWidth, v -> c.barWidth = (int) Math.round(v), 40, 400, 4));
            panelEntries.add(stepper("z80zhealthbar.editor.bar_height",
                    () -> c.barHeight, v -> c.barHeight = (int) Math.round(v), 5, 16, 1));
            // 吸收段显示方式（仅生命;与饱和度同 5 档,默认右侧追加 = 历史行为）
            if (HudLayoutConfig.typeOf(selected, c).equals(HudLayoutConfig.HEALTH)) {
                panelEntries.add(cycler("z80zhealthbar.editor.absorption_mode",
                        () -> Component.translatable("z80zhealthbar.editor.sat_mode."
                                + Math.max(0, Math.min(4, c.absorptionMode))).getString(),
                        () -> {
                            c.absorptionMode = (c.absorptionMode + 1) % 5;
                            markDirty();
                        }));
            }
        }

        // ---- 子组件入口（文本/图标各一个独立参数页,与主组件互不干扰） ----
        // 类型过滤：无该部件的类型不显示入口（如经验无状态图标,compat 无文本无图标）
        String selType = HudLayoutConfig.typeOf(selected, c);
        if (selType.equals(HudLayoutConfig.HOTBAR)) {
            // 选中槽样式（作用于全部 9 槽的"玩家当前选中格";方向用于抬升/呼吸灯辉光锚定边）
            panelEntries.add(new PEntry("z80zhealthbar.editor.hotbar.sel_section", null, null));
            panelEntries.add(cycler("z80zhealthbar.editor.hotbar.sel_style",
                    () -> Component.translatable("z80zhealthbar.editor.hotbar.sel_style." + c.selStyle).getString(),
                    () -> {
                        c.selStyle = (c.selStyle + 1) % 5;
                        markDirty();
                        rebuildWidgets();
                    }));
            // 选中槽边框位置:外/内/双/无(作用于样式 1/2/4 描边与样式 3 脉冲环位)
            panelEntries.add(cycler("z80zhealthbar.editor.hotbar.sel_border",
                    () -> Component.translatable("z80zhealthbar.editor.hotbar.sel_border." + c.selBorder).getString(),
                    () -> {
                        c.selBorder = (c.selBorder + 1) % 4;
                        markDirty();
                    }));
            panelEntries.add(colorRow("z80zhealthbar.editor.hotbar.sel_color", () -> c.selColor, v -> c.selColor = v));
            // 呼吸/脉冲周期（呼吸灯背景、脉冲边框共用）;抬升幅度;高亮填充强度
            panelEntries.add(stepper("z80zhealthbar.editor.hotbar.sel_speed",
                    () -> c.selSpeedMs, v -> c.selSpeedMs = (int) Math.round(v), 100, 6000, 100));
            panelEntries.add(stepper("z80zhealthbar.editor.hotbar.sel_lift",
                    () -> c.selLiftPx, v -> c.selLiftPx = (int) Math.round(v), 0, 8, 1));
            panelEntries.add(stepper("z80zhealthbar.editor.hotbar.sel_fill",
                    () -> c.selFillPct, v -> c.selFillPct = (int) Math.round(v), 0, 100, 5));
            // 选中格切换动效:高亮自原格滑向新格（瞬切/顺滑/回弹）+ 时长
            panelEntries.add(cycler("z80zhealthbar.editor.hotbar.sel_anim",
                    () -> Component.translatable("z80zhealthbar.editor.hotbar.sel_anim." + c.selAnim).getString(),
                    () -> {
                        c.selAnim = (c.selAnim + 1) % 3;
                        markDirty();
                    }));
            panelEntries.add(stepper("z80zhealthbar.editor.hotbar.sel_anim_ms",
                    () -> c.selAnimMs, v -> c.selAnimMs = (int) Math.round(v), 80, 600, 20));
            panelEntries.add(cycler("z80zhealthbar.editor.hotbar.sel_dir",
                    () -> Component.translatable("z80zhealthbar.editor.hotbar.sel_dir." + c.selDir).getString(),
                    () -> {
                        c.selDir = (c.selDir + 1) % 2;
                        markDirty();
                    }));
            // 槽位 = 可逐格添加/移除的独立子组件（与文本/图标子件同形式）:
            // 未添加的槽位显示"添加"行（一键启用并直达该槽编辑页）;已添加的显示"第 N 格"行
            panelEntries.add(PEntry.note("z80zhealthbar.editor.hotbar.note"));
            panelEntries.add(new PEntry("z80zhealthbar.editor.hotbar.slots_section", null, null));
            for (int i = 0; i < 9; i++) {
                final int slotIdx = i;
                var sl = c.hotbarSlot(i);
                if (sl.hidden) {
                    panelEntries.add(listRow(
                            () -> Component.translatable("z80zhealthbar.editor.hotbar.add_slot",
                                    slotIdx + 1).getString(),
                            "z80zhealthbar.editor.add", () -> {
                                c.hotbarSlot(slotIdx).hidden = false;
                                markDirty();
                                hotbarEditSlot = slotIdx;
                                panelPage = PanelPage.HOTBAR_SLOT;
                                rebuildWidgets();
                            }));
                } else {
                    panelEntries.add(listRow(
                            () -> Component.translatable("z80zhealthbar.editor.hotbar.slot.fmt",
                                    slotIdx + 1).getString(),
                            "z80zhealthbar.editor.adjust", () -> {
                                hotbarEditSlot = slotIdx;
                                panelPage = PanelPage.HOTBAR_SLOT;
                                rebuildWidgets();
                            }));
                }
            }
            addComponentActions(selected);
            panelEntries.add(backRow());
            return;
        }
        // selType 已声明（上方插入块内）
        if (selType.equals(HudLayoutConfig.COMPAT) || selType.startsWith("compat_")) {
            // 自定义数据源条:数据源参数区（读写绑定的 CustomSource,边填边看实时读数）
            if (selType.startsWith("compat_custom_")) {
                var src = datasourceOf(selType);
                if (src == null) {
                    panelEntries.add(PEntry.note("z80zhealthbar.editor.datasource.missing"));
                } else {
                    buildDatasourceParams(src);
                }
            }
            // compat 特判：多行动态行的文本部件参数（开关/模板/对齐/偏移/缩放;
            // 行间距由上方"间距"行控制,渲染端已接入）
            panelEntries.add(toggle("z80zhealthbar.editor.show_text",
                    () -> c.showText, v -> c.showText = v));
            panelEntries.add(textInput("z80zhealthbar.editor.text_format",
                    () -> c.textFormat == null ? "" : c.textFormat, v -> c.textFormat = v));
            panelEntries.add(PEntry.note("z80zhealthbar.editor.compat_fmt_note"));
            panelEntries.add(colorRow("z80zhealthbar.editor.color.fill", () -> c.colorFill, v -> c.colorFill = v));
        panelEntries.add(colorRow("z80zhealthbar.editor.color.fill2", () -> c.colorFill2, v -> c.colorFill2 = v));
            panelEntries.add(colorRow("z80zhealthbar.editor.color.text", () -> c.colorText, v -> c.colorText = v));
            panelEntries.add(colorRow("z80zhealthbar.editor.color.card", () -> c.colorCard, v -> c.colorCard = v));
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
            // 润泽度显示方式（仅口渴单行组件;TWT 的"饱和度",与饱食度饱和度同 5 档）
            if (selType.equals("compat_thirst")) {
                panelEntries.add(cycler("z80zhealthbar.editor.quenched_mode",
                        () -> Component.translatable("z80zhealthbar.editor.sat_mode."
                                + Math.max(0, Math.min(4, c.quenchedMode))).getString(),
                        () -> {
                            c.quenchedMode = (c.quenchedMode + 1) % 5;
                            markDirty();
                        }));
            }
            // 动作（复制/删除/重置/分组）：此前 compat 分支提前 return,添加的 compat_* 组件
            // 在编辑器内无法删除/复制（实测"加上去就撤不掉",只能手改 JSON）
            addComponentActions(selected);
            panelEntries.add(groupToggleRow(c));
            panelEntries.add(backRow());
            return;
        }
        if (!selType.equals(HudLayoutConfig.HOTBAR) && !selType.equals(HudLayoutConfig.COMPAT)) {
            panelEntries.add(listRow(() -> Component.translatable("z80zhealthbar.editor.sub_text").getString()
                            + (c.textAnchorParsed() != null
                            ? " · " + Component.translatable("z80zhealthbar.editor.sub_detached").getString() : ""),
                    "z80zhealthbar.editor.adjust", () -> {
                        selected = baseKey(selected) + ".text";
                        panelPage = PanelPage.SUB_TEXT;
                        rebuildWidgets();
                    }));
            // 一键拆分:主组件页直接"添加"文本子组件（此前必须进子页把锚点从'跟随'切走）
            if (c.textAnchorParsed() == null) {
                panelEntries.add(listRow(
                        () -> Component.translatable("z80zhealthbar.editor.sub_text_add").getString(),
                        "z80zhealthbar.editor.add", () -> {
                            c.textAnchor = c.anchorParsed().name(); // 以主组件同锚点起步,随后独立微调
                            c.textOffsetX = 0;
                            c.textOffsetY = 0;
                            markDirty();
                            autoGroupOnDetach(c);
                            rebuildWidgets();
                        }));
            }
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
            // 一键拆分:主组件页直接"添加"图标子组件
            if (c.iconAnchorParsed() == null) {
                panelEntries.add(listRow(
                        () -> Component.translatable("z80zhealthbar.editor.sub_icon_add").getString(),
                        "z80zhealthbar.editor.add", () -> {
                            c.iconAnchor = c.anchorParsed().name();
                            c.iconOffsetX = 0;
                            c.iconOffsetY = 0;
                            markDirty();
                            autoGroupOnDetach(c);
                            rebuildWidgets();
                        }));
            }
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
        int minusX = panelW - 4 - STEP_W * 2 - 2;
        int plusX = panelW - 4 - STEP_W;
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
            markDirty(); // ± 步进同输入框路径标脏（此前仅键入标脏,± 改动曾不进未保存流程）
        });
        FlatButton plus = new FlatButton(STEP_W, BTN_H, () -> Component.literal("+"), () -> {
            set.accept(Math.min(max, roundStep(get.getAsDouble() + step, step)));
            displayNow[0] = fmt.apply(get.getAsDouble());
            box.setValue(displayNow[0]);
            markDirty();
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
        int x = panelW - 4 - CYCLE_W;
        e.valueRight = -1; // 值显示在按钮内
        FlatButton cycle = new FlatButton(CYCLE_W, BTN_H, () -> Component.literal(valueText.get()), next);
        addRenderableWidget(cycle);
        e.widgets.add(new PW(cycle, x));
        return e;
    }

    /** 布尔参数行：标签 + [开/关] 按钮（全部为值编辑,统一标脏） */
    private PEntry toggle(String labelKey, Supplier<Boolean> get, Consumer<Boolean> set) {
        return cycler(labelKey, () -> Component.translatable(
                        get.get() ? "z80zhealthbar.msg.on" : "z80zhealthbar.msg.off").getString(),
                () -> {
                    set.accept(!get.get());
                    markDirty();
                });
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
        e.widgets.add(new PW(box, panelW - 4 - w));
        return e;
    }

    /** 按面板原点更新全部控件位置，并计算面板高度（内容超高时启用滚动,行按滚动物画出裁剪） */
    private void layoutPanel() {
        // 折叠态：面板只留标题条——内容不定位、控件全隐藏并挪出屏幕（isMouseOver 只查坐标,
        // 仅 visible=false 仍会命中 overAnyWidget）
        if (layout().panelCollapsed) {
            panelH = TITLE_H;
            panelScroll = 0;
            panelScrollTarget = 0;
            for (PEntry e : panelEntries) {
                for (PW pw : e.widgets) {
                    pw.widget().visible = false;
                    pw.widget().setY(-9999);
                }
            }
            return;
        }
        // 1) 内容坐标（不含滚动）——同时得出内容总高。
        //    note 行（无值无节标题）按面板内宽换行,行数决定占高——此前固定单行高,长文案溢出面板
        int cursor = TITLE_H + 4;
        for (PEntry e : panelEntries) {
            if (e.isHeader()) {
                e.relY = cursor + 2;
                cursor += HEADER_H;
            } else if (e.note) {
                var src = e.labelDyn != null ? Component.literal(e.labelDyn.get())
                        : Component.translatable(e.labelKey);
                e.noteLines = Math.max(1, font.split(src, panelW - 12).size());
                e.relY = cursor;
                cursor += e.noteLines * 10 + 6;
            } else {
                e.relY = cursor;
                cursor += ROW_H + ROW_GAP;
            }
        }
        panelContentH = cursor + 2;

        // 2) 可视高度：面板不超过底栏上缘；内容不足时面板收缩贴合。
        //    手动高度（拖下缘/右下角设定）优先——内容超高走滚动,内容不足留白（仿窗口语义）
        int screenMaxBody = Math.max(TITLE_H, height - 28 - panelY - TITLE_H - 4);
        int bodyH = Math.min(panelContentH - TITLE_H - 2, screenMaxBody);
        bodyH = Math.max(bodyH, TITLE_H);
        if (panelHOv > 0) {
            bodyH = Math.max(TITLE_H, Math.min(panelHOv - TITLE_H - 2, screenMaxBody));
        }
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
        panelX = Math.max(2, Math.min(panelX, Math.max(2, width - panelW - 2)));
        panelY = Math.max(2, Math.min(panelY, Math.max(2, height - 28 - panelH)));
    }

    // ================= 渲染 =================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HudStyle st = hudStyle();
        undoWatch(); // 撤销监听（限频轮询配置快照）
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
                // hotbar:槽格指示（仅选中该组件时画）——中性暗框,当前编辑槽青色高亮;
                // 坐标乘组件 scale 保证与放大/缩小后的渲染对齐（旧版绿色 + 未乘 scale = "显示错误"观感）
                String boxKey = e.getKey();
                if (isSel && !boxKey.contains(".") && HudLayoutConfig.HOTBAR.equals(
                        HudLayoutConfig.typeOf(boxKey, layout().peek(boxKey)))) {
                    var hbC = layout().peek(boxKey);
                    float k = hbC == null ? 1f : (float) hbC.scale;
                    int pitch = 1;
                    for (int si = 0; si < 9; si++) {
                        var sl = hbC == null ? null : hbC.hotbarSlot(si);
                        if (sl == null) continue;
                        int sw = Math.max(8, Math.min(64, sl.slotW));
                        int sh = Math.max(8, Math.min(64, sl.slotH));
                        int gx = box.x() + (int) ((pitch + sl.offsetX) * k);
                        int gy = box.y() + (int) ((1 + sl.offsetY) * k);
                        pitch += sw; // 隐藏槽保留占位（与渲染排布一致）
                        if (sl.hidden) continue; // 未添加的槽位无指示框
                        boolean editing = si == hotbarEditSlot;
                        graphics.renderOutline(gx, gy, Math.max(1, (int) (sw * k)), Math.max(1, (int) (sh * k)),
                                editing ? 0xC07FD4FF : 0x30FFFFFF);
                    }
                }
                if (isSel) {
                    String suffix = e.getKey().endsWith(".text")
                            ? Component.translatable("z80zhealthbar.editor.suffix_text").getString()
                            : e.getKey().endsWith(".icon")
                            ? Component.translatable("z80zhealthbar.editor.suffix_icon").getString() : "";
                    String title = Component.translatable("z80zhealthbar.hud.component."
                            + HudLayoutConfig.baseKeyOf(baseKey(e.getKey()))).getString() + suffix;
                    // 悬停标题变白提示可抓取拖动（命中区同式,见 mouseClicked 标题分支）
                    int tw = font.width(title);
                    boolean hoverTitle = mouseX >= box.x() + box.width() / 2 - tw / 2 - 2
                            && mouseX < box.x() + box.width() / 2 + tw / 2 + 2
                            && mouseY >= box.y() - 12 && mouseY < box.y() - 1;
                    graphics.drawCenteredString(font, title,
                            box.x() + box.width() / 2, box.y() - 10,
                            hoverTitle ? 0xFFFFFFFF : 0xFF40FF40);
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

        graphics.drawCenteredString(font, getTitle(), width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("z80zhealthbar.editor.hint"),
                width / 2, height - 34, 0xFFCFCFCF);
        // 设置说明浮框**最后绘制**:此前先于底部提示/行控件,文字互相遮挡混叠（实测反馈）
        if (helpKey != null) renderHelpBox(graphics);
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
        boolean collapsed = layout().panelCollapsed;
        g.fill(panelX + 3, panelY + 3, panelX + panelW + 3, panelY + panelH + 3, 0x50000000);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE60E1218);
        g.fill(panelX, panelY, panelX + panelW, panelY + TITLE_H, 0xFF1B2836);
        g.fill(panelX, panelY + TITLE_H - 1, panelX + panelW, panelY + TITLE_H, 0x907FD4FF);
        g.renderOutline(panelX, panelY, panelW, panelH, 0x507FD4FF);

        // 右下角缩放握把（仿 Windows 斜纹角标,提示可拖拽调大小）
        if (!collapsed) {
            for (int i = 1; i <= 3; i++) {
                g.fill(panelX + panelW - 2 - i * 4, panelY + panelH - 3,
                        panelX + panelW + 1 - i * 4, panelY + panelH - 2, 0x607FD4FF);
                g.fill(panelX + panelW - 3, panelY + panelH - 2 - i * 4,
                        panelX + panelW - 2, panelY + panelH + 1 - i * 4, 0x607FD4FF);
            }
        }

        g.drawString(font, "\u2261 " + Component.translatable("z80zhealthbar.editor.panel.title").getString(),
                panelX + 5, panelY + 3, 0xFFCFE6FF);
        // 折叠按钮（标题栏右缘）："—"/"+"——折叠时只留标题条（实测反馈）
        String fold = collapsed ? "+" : "\u2212";
        g.drawString(font, fold, panelX + panelW - 10, panelY + 3, 0xFFCFE6FF);
        if (!collapsed) {
            String drag = Component.translatable("z80zhealthbar.editor.panel.drag").getString();
            g.drawString(font, drag, panelX + panelW - 16 - font.width(drag), panelY + 3, 0x8088A0B8);
        }
        if (collapsed) return; // 折叠：无内容区

        int scroll = Math.round(panelScroll);
        for (PEntry e : panelEntries) {
            if (!e.isHeader()) continue;
            int y = panelY + e.relY - scroll;
            if (y < panelY + TITLE_H + 2 || y + HEADER_H > panelY + panelH) continue; // 视口外
            g.fill(panelX + 4, y + HEADER_H - 3, panelX + panelW - 4,
                    y + HEADER_H - 2, 0x20FFFFFF);
        }

        // 内容超高时画滚动条（右缘 4px 轨道 + 青色滑块——原 2px 太细,实测反馈加粗）
        int maxScroll = Math.max(0, panelContentH - panelH);
        if (maxScroll > 0) {
            int trackX = panelX + panelW - 6;
            int trackY0 = panelY + TITLE_H + 2, trackY1 = panelY + panelH - 3;
            int bodyView = Math.max(1, trackY1 - trackY0);
            int thumbH = Math.max(10, bodyView * panelH / panelContentH);
            thumbH = Math.min(thumbH, bodyView);
            int thumbY = trackY0 + (bodyView - thumbH) * (int) panelScroll / maxScroll;
            g.fill(trackX, trackY0, trackX + 4, trackY1, 0x30FFFFFF);
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0x907FD4FF);
        }
    }

    /** 文本按可用宽截断,超出补省略号（行标签/实例名过长不再压到右侧控件下） */
    private String fitText(String s, int maxW) {
        if (s == null || s.isEmpty() || maxW <= 0 || font.width(s) <= maxW) return s;
        int ellW = font.width("…");
        int i = s.length();
        while (i > 0 && font.width(s.substring(0, i)) + ellW > maxW) i--;
        return (i > 0 ? s.substring(0, i) : "") + "…";
    }

    /** 面板滚动条命中测试（拖拽用;false = 无滚动条或不在轨道上） */
    private boolean panelScrollbarHit(double mx, double my) {
        int maxScroll = Math.max(0, panelContentH - panelH);
        if (maxScroll <= 0) return false;
        return mx >= panelX + panelW - 8 && mx <= panelX + panelW
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
                // note 行：按面板内宽换行绘制（layoutPanel 已按行数计高）;labelDyn 非空 = 动态文本(诊断行)
                if (y < panelY + TITLE_H + 2 || y + e.noteLines * 10 > panelY + panelH - 1) continue;
                var src = e.labelDyn != null ? Component.literal(e.labelDyn.get())
                        : Component.translatable(e.labelKey);
                var lines = font.split(src, panelW - 12);
                for (int i = 0; i < lines.size(); i++) {
                    g.drawString(font, lines.get(i), panelX + 6, y + i * 10, 0xFFB8C4D0);
                }
                continue;
            }
            if (y < panelY + TITLE_H + 2 || y + ROW_H > panelY + panelH - 1) continue;
            String lbl = e.labelDyn != null ? e.labelDyn.get()
                    : (e.labelKey != null ? Component.translatable(e.labelKey).getString() : "");
            // (?) 说明标记：有译文的参数行才画;点击切换显示说明浮框。
            // 位置统一**固定前置列**（panelX+6,标签统一右移 12px 起点）——此前"后置优先、
            // 空间不足才前置"导致长标签行与短标签行的问号一左一右参差（实测反馈"统一设置项问号位置"）
            String tk = e.labelDyn == null ? tooltipKeyFor(e.labelKey) : null;
            int labelX = panelX + 18;
            int qx = tk != null ? panelX + 6 : -1;
            // 行标签按可用宽截断（…）:可用 = 行内最左控件左缘（无控件行 = 面板右缘-8）− 边距
            int minRel = -1;
            for (PW pw : e.widgets) if (minRel < 0 || pw.relX() < minRel) minRel = pw.relX();
            int textRight = minRel >= 0 ? minRel : panelW - 8;
            if (e.swatchArgb != null) {
                int argb = e.swatchArgb.get();
                int swX = panelX + textRight - 13;
                int swY = y + (ROW_H - 8) / 2;
                if (argb == 0) {
                    g.renderOutline(swX, swY, 8, 8, 0x70FFFFFF); // 继承默认:空框
                } else {
                    g.fill(swX - 1, swY - 1, swX + 9, swY + 9, 0xFF000000);
                    g.fill(swX, swY, swX + 8, swY + 8, argb);
                    g.renderOutline(swX - 1, swY - 1, 10, 10, 0x80FFFFFF);
                }
                textRight -= 16;
            }
            lbl = fitText(lbl, textRight - 18 - 4);
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
                g.drawString(font, v, panelX + panelW - 8 - font.width(v),
                        y + (ROW_H - 8) / 2, 0xFFA8E0FF);
            }
        }
    }

    // ================= 交互 =================

    /** 面板命中（非控件区域：用于拖动整个面板） */
    private boolean panelHit(double mx, double my) {
        if (mx < panelX || mx > panelX + panelW || my < panelY || my > panelY + panelH) return false;
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

    /** 面板缩放命中:右缘/下缘/右下角 4px 感应带（返回 1=右缘 2=下缘 3=角,0=未命中） */
    private int panelResizeHit(double mx, double my) {
        boolean right = mx >= panelX + panelW - 4 && mx <= panelX + panelW + 2
                && my >= panelY && my <= panelY + panelH;
        boolean bottom = my >= panelY + panelH - 4 && my <= panelY + panelH + 2
                && mx >= panelX && mx <= panelX + panelW;
        if (right && bottom) return 3;
        if (right) return 1;
        if (bottom) return 2;
        return 0;
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
        // 0a) 面板折叠按钮（标题栏右缘）：切换折叠态并持久化
        if (mouseX >= panelX + panelW - 12 && mouseX <= panelX + panelW - 2
                && mouseY >= panelY && mouseY <= panelY + TITLE_H) {
            layout().panelCollapsed = !layout().panelCollapsed;
            layoutPanel();
            return true;
        }
        // 0a-2) 面板缩放命中（仿 Windows:右缘/下缘/右下角拖拽调大小;折叠态只有标题条不缩放）
        if (!layout().panelCollapsed) {
            int mode = panelResizeHit(mouseX, mouseY);
            if (mode > 0) {
                panelResizing = mode;
                return true;
            }
        }
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
        // hotbar 槽位优先命中（**不看组件框**——槽位可被拖出框外,必须能重新抓回。
        // 旧实现把槽位命中放在"点击须在组件框内"的内层判定,槽位拖出框后永远点不到 = "无法拖动"根因）
        for (Map.Entry<String, ComponentLayout> ce : layout().components.entrySet()) {
            var hbComp = ce.getValue();
            if (hbComp == null || !HudLayoutConfig.HOTBAR.equals(
                    HudLayoutConfig.typeOf(ce.getKey(), hbComp))) continue;
            if (hbComp.modeParsed() == HudLayoutConfig.ComponentMode.OFF) continue;
            var hbBox = boxes.get(ce.getKey());
            if (hbBox == null) continue;
            float k = (float) hbComp.scale;
            int pitch = 1;
            for (int si = 0; si < 9; si++) {
                var sl = hbComp.hotbarSlot(si);
                double gx = hbBox.x() + (pitch + sl.offsetX) * k;
                double gy = hbBox.y() + (1 + sl.offsetY) * k;
                int sw = Math.max(8, Math.min(64, sl.slotW));
                int sh = Math.max(8, Math.min(64, sl.slotH));
                pitch += sw; // 隐藏槽保留占位（与渲染排布一致）
                if (sl.hidden) continue; // 未添加的槽位不可点选
                if (mouseX >= gx && mouseX < gx + sw * k && mouseY >= gy && mouseY < gy + sh * k) {
                    selected = ce.getKey();
                    hotbarEditSlot = si;
                    hotbarDragSlot = si;
                    hbDragMoved = false;
                    hbDragScale = k;
                    hbGrabX = (int) mouseX;
                    hbGrabY = (int) mouseY;
                    hbStartOffX = sl.offsetX;
                    hbStartOffY = sl.offsetY;
                    // 页面切换延后到松手且未拖动时（拖拽优先,拖动中面板不跳动）
                    rebuildWidgets();
                    return true;
                }
            }
        }
        // 选中组件的标题文字（框上方绿字）同作拖拽把手——命中区与渲染同式（居中于框顶上方 1..12px）
        if (selected != null && boxes.containsKey(selected)) {
            var sb = boxes.get(selected);
            if (mouseY >= sb.y() - 12 && mouseY < sb.y() - 1) {
                String suffix = selected.endsWith(".text")
                        ? Component.translatable("z80zhealthbar.editor.suffix_text").getString()
                        : selected.endsWith(".icon")
                        ? Component.translatable("z80zhealthbar.editor.suffix_icon").getString() : "";
                String title = Component.translatable("z80zhealthbar.hud.component."
                        + HudLayoutConfig.baseKeyOf(baseKey(selected))).getString() + suffix;
                int tw = font.width(title);
                int tx0 = sb.x() + sb.width() / 2 - tw / 2 - 2;
                if (mouseX >= tx0 && mouseX < tx0 + tw + 4) {
                    dragging = true;
                    draggingX = (int) mouseX;
                    draggingY = (int) mouseY;
                    dragStartOffX = dragStartX(selected);
                    dragStartOffY = dragStartY(selected);
                    panelPage = selected.endsWith(".text") ? PanelPage.SUB_TEXT
                            : selected.endsWith(".icon") ? PanelPage.SUB_ICON : PanelPage.EDIT;
                    rebuildWidgets();
                    return true;
                }
            }
        }
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
        if (panelResizing > 0) {
            // 仿 Windows 调整窗口大小:右缘调宽、下缘调高、角两者;即时持久化,松手重排行内容
            if (panelResizing == 1 || panelResizing == 3) {
                panelW = Math.max(PANEL_MIN_W, Math.min(PANEL_MAX_W, (int) mouseX - panelX));
            }
            if (panelResizing == 2 || panelResizing == 3) {
                panelHOv = Math.max(TITLE_H + 30, Math.min(height - 8 - panelY, (int) mouseY - panelY));
            }
            layout().panelW = panelW;
            layout().panelH = panelHOv;
            clampPanel();
            layoutPanel();
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
        if (hotbarDragSlot >= 0 && editingCustom()) {
            markDirty();
            var hbC = layout().peek(selected);
            if (hbC != null && HudLayoutConfig.HOTBAR.equals(HudLayoutConfig.typeOf(selected, hbC))) {
                var sl = hbC.hotbarSlot(hotbarDragSlot);
                if (Math.abs((int) mouseX - hbGrabX) > 2 || Math.abs((int) mouseY - hbGrabY) > 2) {
                    hbDragMoved = true; // 超过阈值才算拖动（点击进页 vs 拖动移位的分界）
                }
                float k = hbDragScale <= 0.01f ? 1f : hbDragScale;
                sl.offsetX = Math.max(-500, Math.min(500,
                        hbStartOffX + Math.round(((int) mouseX - hbGrabX) / k)));
                sl.offsetY = Math.max(-500, Math.min(500,
                        hbStartOffY + Math.round(((int) mouseY - hbGrabY) / k)));
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
        if (panelResizing > 0) {
            panelResizing = 0;
            rebuildWidgets(); // 松手重排:行控件按新宽度重新布局
            return true;
        }
        if (hotbarDragSlot >= 0 && !hbDragMoved && editingCustom()) {
            // 纯点击（未拖动）→ 进入该槽的独立编辑页;拖动过则只完成移位,页面不变
            panelPage = PanelPage.HOTBAR_SLOT;
            rebuildWidgets();
        }
        dragging = false;
        hotbarDragSlot = -1;
        hbDragMoved = false;
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
        if (mouseX >= panelX && mouseX <= panelX + panelW
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
        // Ctrl+Z 撤销 / Ctrl+Y 或 Ctrl+Shift+Z 重做——置于文本框聚焦判断之前,
        // 输入框打字中的撤销 = 整体回到本次输入前的值（原版 EditBox 无自带撤销,无冲突）
        if (Screen.hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z && !Screen.hasShiftDown()) {
            undo();
            return true;
        }
        if (Screen.hasControlDown() && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y
                || (Screen.hasShiftDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z))) {
            redo();
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
