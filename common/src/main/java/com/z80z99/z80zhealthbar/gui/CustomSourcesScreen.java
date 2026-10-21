package com.z80z99.z80zhealthbar.gui;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig;
import com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig.CustomSource;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 自定义数据源管理页（通用兼容框架）。
 * <p>列表 + 编辑表单（按通道动态显隐字段）+ **实时读数诊断** + 官方源预置一键填入。
 * 配置极易写错（类名/方法名拼写）,没有即时反馈等于不可用,所以每帧刷新测试结果。
 * <p>打开时对当前配置快照,每次修改即写回 + saveConfig（与设置页即时保存语义一致）。
 */
public final class CustomSourcesScreen extends Screen {

    private static final int W = 360;
    private static final int H = 236;
    private static final int ROW_H = 20;
    /** 响应式实际尺寸（init 里按屏幕计算;w/h 是参考默认值） */
    private int w = W, h = H;

    private final Screen parent;

    private int panelX, panelY;
    private int selected = -1;      // 当前编辑的源索引
    private boolean creating;
    /** 目标模组选择视图（内嵌于编辑表单下方） */
    private boolean modPicking;
    private String modSearch = "";
    private int modScroll;
    private List<com.z80z99.z80zhealthbar.platform.ModEntry> modCache;
    /** 保存防抖:打字时每键落盘曾导致渲染线程磁盘 IO(审计 P4)。dirty=true 才算待保存。 */
    private boolean saveDirty;
    private long saveDueAt;

    /** 活引用:热重载会替换 config 实例,缓存的列表引用会写到旧对象(审计 S3) */
    private List<CustomSource> sources() {
        var cs = ConfigManager.getConfig().customSources;
        if (cs == null) {
            cs = new com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig();
            ConfigManager.getConfig().customSources = cs;
        }
        if (cs.customSources == null) cs.customSources = new ArrayList<>();
        return cs.customSources;
    }

    /** 标记待保存(500ms 防抖;关屏/返回时立即落盘) */
    private void markSave() {
        saveDirty = true;
        saveDueAt = System.currentTimeMillis() + 500L;
    }

    private void flushSave() {
        if (!saveDirty) return;
        saveDirty = false;
        ConfigManager.saveConfig();
    }

    @Override
    public void removed() {
        flushSave();
        super.removed();
    }

    public CustomSourcesScreen(Screen parent) {
        super(Component.translatable("z80zhealthbar.custom_sources.title"));
        this.parent = parent;
    }

    // 编辑表单控件（仅编辑态可见）
    private EditBox idBox, modBox, nameBox, holderBox, fieldBox, ifaceBox, readBox, valueBox, maxBox, overlayBox;
    private Button channelBtn, itemModeBtn, maxFixedBtn, colorBtn, takeoverBtn;

    @Override
    protected void init() {
        w = GuiLayout.fitW(W, width, 10);
        h = GuiLayout.fitH(H, height, 10);
        panelX = GuiLayout.centerX(w, width, 10);
        panelY = GuiLayout.centerY(h, height, 10);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> back())
                .bounds(panelX + w - 70, panelY + h - 26, 60, 18).build());

        if (selected < 0) {
            buildList();
        } else {
            buildEditor();
        }
    }

    private void back() {
        flushSave();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // ================= 列表 =================

    /** 列表滚动偏移（行）；第 8 个以后的数据源旧版完全不可达 */
    private int listScroll;
    /** 待确认删除的下标（-1 = 无;点删除先变确认态,再点才真删） */
    private int pendingDelete = -1;

    private void buildList() {
        int listTop = panelY + 30;
        int listBottom = panelY + h - 60;
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);
        int total = sources().size();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, total - visibleRows)));
        for (int i = listScroll; i < Math.min(total, listScroll + visibleRows); i++) {
            final int idx = i;
            var s = sources().get(i);
            int rowY = listTop + (i - listScroll) * ROW_H;
            // 参数不完整 → 黄色警示（配置写错的源一眼可辨）
            String warn = s.paramsComplete() ? "" : "§e\u25B2 ";
            addRenderableWidget(Button.builder(Component.literal(warn + (s.enabled ? "§a● " : "§8○ ")
                            + s.name() + " §7[" + channelName(s.channel) + "]"),
                    b -> { selected = idx; creating = false; rebuild(); })
                    .bounds(panelX + 12, rowY, w - 24 - 118, 18).build());
            boolean confirming = pendingDelete == idx;
            addRenderableWidget(Button.builder(Component.translatable(confirming
                            ? "z80zhealthbar.custom_sources.delete_confirm"
                            : "z80zhealthbar.custom_sources.delete"),
                            b -> {
                                if (pendingDelete == idx) {
                                    sources().remove(idx);
                                    pendingDelete = -1;
                                    markSave();
                                } else {
                                    pendingDelete = idx;
                                }
                                rebuild();
                            })
                    .bounds(panelX + w - 12 - 106, rowY, 50, 18).build());
            addRenderableWidget(Button.builder(Component.translatable(
                            s.enabled ? "z80zhealthbar.custom_sources.disable" : "z80zhealthbar.custom_sources.enable"),
                            b -> { s.enabled = !s.enabled; markSave(); rebuild(); })
                    .bounds(panelX + w - 12 - 52, rowY, 52, 18).build());
        }
        if (total > visibleRows) {
            // 滚动条几何（绘制在 render,滚轮/拖动见 mouseScrolled）
            int trackX = panelX + w - 6;
            int trackH = listBottom - listTop;
            int thumbH = Math.max(10, trackH * visibleRows / total);
            int thumbY = listTop + (trackH - thumbH) * listScroll / Math.max(1, total - visibleRows);
            listScrollTrack = new int[]{trackX, listTop, listBottom, thumbY, thumbH, total, visibleRows};
        } else {
            listScrollTrack = null;
        }
        if (sources().size() < 32) {
            addRenderableWidget(Button.builder(Component.translatable("z80zhealthbar.custom_sources.new"),
                            b -> {
                                var s = new CustomSource();
                                s.id = "source_" + (sources().size() + 1);
                                s.displayName = "";
                                s.channel = 4; // 默认最常用的「手持物品」通道
                                sources().add(s);
                                selected = sources().size() - 1;
                                creating = true;
                                rebuild();
                            })
                    .bounds(panelX + 12, panelY + h - 52, 120, 18).build());
        }
        // （预置入口已移除——创建流程改在 HUD 编辑器:添加组件 → 数据源 → 数据类型）
    }

    /** 列表滚动条几何（null = 无需滚动）:{trackX, top, bottom, thumbY, thumbH, total, visibleRows} */
    private int[] listScrollTrack;

    private static int trackY(int thumbY, int thumbH, int top, int bottom) {
        return Math.max(top, Math.min(thumbY, bottom - thumbH));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (selected < 0 && listScrollTrack != null
                && mouseX >= panelX && mouseX <= panelX + w
                && mouseY >= panelY + 30 && mouseY <= panelY + h - 60) {
            listScroll -= (int) Math.signum(delta);
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }


    // ================= 编辑表单 =================

    private void buildEditor() {
        var s = sources().get(selected);
        int x0 = panelX + 12, x1 = panelX + 150;
        int y = panelY + 30;

        idBox = textField(x0, y, 128, s.id, v -> s.id = v.trim().toLowerCase(java.util.Locale.ROOT));
        nameBox = textField(x1, y, 196, s.name(), v -> s.displayName = v);
        y += ROW_H;
        modBox = textField(x0, y, 96, s.modId, v -> s.modId = v.trim());
        addRenderableWidget(Button.builder(Component.translatable("z80zhealthbar.custom_sources.pick.from_list"),
                        b -> { modPicking = true; modSearch = ""; modScroll = 0; rebuild(); })
                .bounds(x0 + 100, y, 44, 18).build());
        channelBtn = Button.builder(Component.translatable("z80zhealthbar.custom_sources.channel_short",
                        channelName(s.channel)),
                b -> { s.channel = (s.channel + 1) % 5; rebuild(); })
                .bounds(x1, y, 196, 18).build();
        addRenderableWidget(channelBtn);
        y += ROW_H;
        if (modPicking) {
            buildModPicker(x0, y);
            return;
        }
        switch (s.channel) {
            case 0 -> {
                holderBox = textField(x0, y, 330, s.holderClass, v -> s.holderClass = v.trim());
                y += ROW_H;
                fieldBox = textField(x0, y, 160, s.holderField, v -> s.holderField = v.trim());
                ifaceBox = textField(x0 + 168, y, 162, s.ifaceClass, v -> s.ifaceClass = v.trim());
                y += ROW_H;
                readBox = textField(x0, y, 160, s.readMethod, v -> s.readMethod = v.trim());
                maxBox = textField(x0 + 168, y, 162, s.maxMethod, v -> s.maxMethod = v.trim());
                y += ROW_H;
            }
            case 1 -> {
                holderBox = textField(x0, y, 330, s.holderClass, v -> s.holderClass = v.trim());
                y += ROW_H;
                readBox = textField(x0, y, 160, s.readMethod, v -> s.readMethod = v.trim());
                maxBox = textField(x0 + 168, y, 162, s.maxMethod, v -> s.maxMethod = v.trim());
                y += ROW_H;
            }
            case 2 -> {
                holderBox = textField(x0, y, 330, s.holderClass, v -> s.holderClass = v.trim());
                y += ROW_H;
                readBox = textField(x0, y, 160, s.readMethod, v -> s.readMethod = v.trim());
                valueBox = textField(x0 + 168, y, 162, s.valueMethod, v -> s.valueMethod = v.trim());
                y += ROW_H;
                maxBox = textField(x0, y, 160, s.maxMethod, v -> s.maxMethod = v.trim());
                y += ROW_H;
            }
            case 3 -> {
                readBox = textField(x0, y, 160, s.readMethod, v -> s.readMethod = v.trim());
                maxBox = textField(x0 + 168, y, 162, s.maxMethod, v -> s.maxMethod = v.trim());
                y += ROW_H;
            }
            default -> {
                itemModeBtn = Button.builder(Component.translatable(
                                "z80zhealthbar.custom_sources.item_mode_short", itemModeName(s.itemMode)),
                        b -> { s.itemMode = (s.itemMode + 1) % 3; rebuild(); })
                        .bounds(x0, y, 160, 18).build();
                addRenderableWidget(itemModeBtn);
                y += ROW_H;
                if (s.itemMode != CustomSourceConfig.ITEM_VANILLA_DURABILITY) {
                    readBox = textField(x0, y, 160,
                            s.readMethod, v -> s.readMethod = v.trim());
                    maxBox = textField(x0 + 168, y, 162, s.maxMethod, v -> s.maxMethod = v.trim());
                    y += ROW_H;
                }
            }
        }

        // 上限（固定值）/ 颜色 / 接管
        maxFixedBtn = Button.builder(Component.translatable("z80zhealthbar.custom_sources.static_max_short",
                        s.staticMax > 0 ? String.valueOf(Math.round(s.staticMax)) : "—"),
                b -> {
                    s.staticMax = switch ((int) s.staticMax) {
                        case 0 -> 20f;
                        case 20 -> 100f;
                        case 100 -> 0f;
                        default -> 0f;
                    };
                    rebuild();
                }).bounds(x0, y, 110, 18).build();
        addRenderableWidget(maxFixedBtn);
        colorBtn = Button.builder(Component.translatable("z80zhealthbar.custom_sources.color_short",
                        s.color == null || s.color.isBlank() ? "—" : s.color),
                b -> minecraft.setScreen(new ColorWheelScreen(this,
                        Component.translatable("z80zhealthbar.custom_sources.color_label").getString(),
                        s.color, v -> { s.color = v; markSave(); rebuild(); })))
                .bounds(x0 + 118, y, 110, 18).build();
        addRenderableWidget(colorBtn);
        overlayBox = textField(x0 + 236, y, 94, s.overlayId, v -> s.overlayId = v.trim());
        y += ROW_H;
        takeoverBtn = Button.builder(Component.translatable("z80zhealthbar.custom_sources.takeover_short",
                        Component.translatable(s.takeover
                                ? "z80zhealthbar.msg.on" : "z80zhealthbar.msg.off").getString()),
                b -> { s.takeover = !s.takeover; markSave(); rebuild(); })
                .bounds(x0, y, 110, 18).build();
        addRenderableWidget(takeoverBtn);

        // 返回列表
        addRenderableWidget(Button.builder(Component.translatable("z80zhealthbar.custom_sources.back"),
                        b -> { markSave(); selected = -1; rebuild(); })
                .bounds(panelX + w - 70, panelY + h - 26, 60, 18).build());
    }

    /** 内嵌模组选择：搜索框 + 可滚动列表（每行 显示名 + id,点击填入 modId） */
    private void buildModPicker(int x0, int y) {
        var s = sources().get(selected);
        if (modCache == null) {
            modCache = com.z80z99.z80zhealthbar.platform.PlatformService.get() == null
                    ? List.of()
                    : com.z80z99.z80zhealthbar.platform.PlatformService.get().installedMods();
        }
        var search = new EditBox(font, x0, y, 240, 18,
                Component.translatable("z80zhealthbar.custom_sources.pick.search"));
        search.setMaxLength(64);
        search.setValue(modSearch);
        search.setResponder(v -> { modSearch = v; modScroll = 0; rebuild(); });
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("z80zhealthbar.custom_sources.pick.back"),
                        b -> { modPicking = false; rebuild(); })
                .bounds(x0 + 248, y, 82, 18).build());
        y += ROW_H;

        String f = modSearch == null ? "" : modSearch.trim().toLowerCase(java.util.Locale.ROOT);
        List<com.z80z99.z80zhealthbar.platform.ModEntry> hits = new ArrayList<>();
        for (var m : modCache) {
            String id = m.id() == null ? "" : m.id();
            String dn = m.displayName() == null ? "" : m.displayName();
            if (!f.isEmpty()
                    && !id.toLowerCase(java.util.Locale.ROOT).contains(f)
                    && !dn.toLowerCase(java.util.Locale.ROOT).contains(f)) continue;
            hits.add(m);
        }
        int visible = 6; // 一次显示 6 行（滚动切换）
        int maxScroll = Math.max(0, hits.size() - visible);
        modScroll = Math.max(0, Math.min(modScroll, maxScroll));
        for (int i = modScroll; i < Math.min(hits.size(), modScroll + visible); i++) {
            var m = hits.get(i);
            String label = (m.displayName() == null || m.displayName().isBlank()
                    || m.displayName().equalsIgnoreCase(m.id()) ? m.id()
                    : m.displayName() + " §7(" + m.id() + ")");
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                        s.modId = m.id();
                        modPicking = false;
                        modCache = null;
                        markSave();
                        rebuild();
                    }).bounds(x0, y, 330, 18).build());
            y += ROW_H;
        }
        if (hits.isEmpty()) {
            // 无结果提示（绘制在 render 的标签区,这里只留空）
        }
        // 滚动按钮
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> { modScroll = Math.max(0, modScroll - visible); rebuild(); })
                .bounds(x0 + 336, panelY + 30, 12, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> { modScroll = modScroll + visible; rebuild(); })
                .bounds(x0 + 336, panelY + 50, 12, 18).build());
    }

    private EditBox textField(int x, int y, int w, String initial, Consumer<String> setter) {        var box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setMaxLength(160);
        box.setValue(initial == null ? "" : initial);
        box.setResponder(s -> {
            setter.accept(s);
            markSave();
        });
        addRenderableWidget(box);
        return box;
    }

    private static String channelName(int ch) {
        return net.minecraft.network.chat.Component.translatable(
                "z80zhealthbar.editor.datasource.type." + Math.max(0, Math.min(4, ch))).getString();
    }

    private static String itemModeName(int m) {
        return net.minecraft.network.chat.Component.translatable(
                "z80zhealthbar.editor.datasource.item_mode." + Math.max(0, Math.min(2, m))).getString();
    }

    // ================= 渲染 =================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (saveDirty && System.currentTimeMillis() >= saveDueAt) flushSave();
        renderBackground(g);
        g.fill(panelX, panelY, panelX + w, panelY + h, 0xF010151C);
        g.renderOutline(panelX, panelY, w, h, 0x907FD4FF);
        g.drawCenteredString(font, getTitle(), panelX + w / 2, panelY + 8, 0xFFCFE6FF);
        // 列表滚动条
        if (selected < 0 && listScrollTrack != null) {
            int tx = listScrollTrack[0], top = listScrollTrack[1], bot = listScrollTrack[2];
            int thumbY = listScrollTrack[3], thumbH = listScrollTrack[4];
            g.fill(tx, top, tx + 4, bot, 0x28FFFFFF);
            g.fill(tx, thumbY, tx + 4, thumbY + thumbH, 0xA07FD4FF);
        }
        super.render(g, mouseX, mouseY, partialTick);

        if (selected < 0) {
            if (sources().isEmpty()) {
                g.drawWordWrap(font, Component.translatable("z80zhealthbar.custom_sources.empty"),
                        panelX + 14, panelY + 40, w - 28, 0xFFA8B8C8);
            }
            g.drawWordWrap(font, Component.translatable("z80zhealthbar.custom_sources.list_note"),
                    panelX + 235, panelY + h - 76, w - 240, 0xFF8898A8);
        } else {
            renderEditorLabels(g);
            if (modPicking && modCache != null) {
                String f = modSearch == null ? "" : modSearch.trim().toLowerCase(java.util.Locale.ROOT);
                boolean any = false;
                for (var m : modCache) {
                    String id = m.id() == null ? "" : m.id();
                    String dn = m.displayName() == null ? "" : m.displayName();
                    if (f.isEmpty() || id.toLowerCase(java.util.Locale.ROOT).contains(f)
                            || dn.toLowerCase(java.util.Locale.ROOT).contains(f)) { any = true; break; }
                }
                if (!any) {
                    g.drawString(font, Component.translatable("z80zhealthbar.custom_sources.pick.empty"),
                            panelX + 12, panelY + h - 132, 0xFFE0B060);
                }
            }
        }
    }

    private void renderEditorLabels(GuiGraphics g) {
        var s = sources().get(selected);
        int x0 = panelX + 12, x1 = panelX + 150;
        int y = panelY + 30;
        label(g, "z80zhealthbar.custom_sources.field.id", x0, y - 9);
        label(g, "z80zhealthbar.custom_sources.field.name", x1, y - 9);
        y += ROW_H;
        label(g, "z80zhealthbar.custom_sources.field.mod", x0, y - 9);
        label(g, "z80zhealthbar.custom_sources.field.channel", x1, y - 9);
        y += ROW_H;
        if (s.channel == 0) {
            label(g, "z80zhealthbar.custom_sources.field.holder", x0, y - 9);
            y += ROW_H;
            label(g, "z80zhealthbar.custom_sources.field.token_field", x0, y - 9);
            label(g, "z80zhealthbar.custom_sources.field.iface", x0 + 168, y - 9);
            y += ROW_H;
            label(g, "z80zhealthbar.custom_sources.field.read", x0, y - 9);
            label(g, "z80zhealthbar.custom_sources.field.max_method", x0 + 168, y - 9);
        } else if (s.channel == 1) {
            label(g, "z80zhealthbar.custom_sources.field.holder", x0, y - 9);
            y += ROW_H;
            label(g, "z80zhealthbar.custom_sources.field.read", x0, y - 9);
            label(g, "z80zhealthbar.custom_sources.field.max_method", x0 + 168, y - 9);
        } else if (s.channel == 2) {
            label(g, "z80zhealthbar.custom_sources.field.holder", x0, y - 9);
            y += ROW_H;
            label(g, "z80zhealthbar.custom_sources.field.factory", x0, y - 9);
            label(g, "z80zhealthbar.custom_sources.field.value_method", x0 + 168, y - 9);
        } else if (s.channel == 3) {
            label(g, "z80zhealthbar.custom_sources.field.player_method", x0, y - 9);
            label(g, "z80zhealthbar.custom_sources.field.max_method", x0 + 168, y - 9);
        } else {
            label(g, "z80zhealthbar.custom_sources.field.item_mode", x0, y - 9);
            y += ROW_H;
            if (s.itemMode != CustomSourceConfig.ITEM_VANILLA_DURABILITY) {
                label(g, s.itemMode == CustomSourceConfig.ITEM_NBT_INT
                                ? "z80zhealthbar.custom_sources.field.nbt_key"
                                : "z80zhealthbar.custom_sources.field.item_method",
                        x0, y - 9);
                label(g, "z80zhealthbar.custom_sources.field.max_method", x0 + 168, y - 9);
            }
        }
        // 实时读数诊断（配置拼写错误的主要发现途径）
        String diag = diagnose(s);
        int diagY = panelY + h - 68;
        g.drawString(font, Component.translatable("z80zhealthbar.custom_sources.test").getString()
                + ": " + diag, panelX + 12, diagY, 0xFFB8C8D8);
        g.drawString(font, "§7" + Component.translatable(
                        "z80zhealthbar.custom_sources.hint").getString(),
                panelX + 12, diagY + 12, 0xFF8898A8);
    }

    private void label(GuiGraphics g, String key, int x, int y) {
        g.drawString(font, Component.translatable(key), x, y, 0xFF98A8B8);
    }

    /** 诊断文本（每帧调用:成功 → 值/上限;失败 → 具体原因） */
    private String diagnose(CustomSource s) {
        if (!s.enabled) return "§8" + Component.translatable("z80zhealthbar.custom_sources.diag.disabled").getString();
        if (!s.paramsComplete()) {
            return "§e" + Component.translatable("z80zhealthbar.custom_sources.diag.incomplete").getString();
        }
        if (minecraft == null || minecraft.player == null) {
            return "§8—";
        }
        var adapter = new com.z80z99.z80zhealthbar.compat.CustomSourceAdapter(s);
        if (!adapter.isAvailable()) {
            return "§c" + Component.translatable("z80zhealthbar.custom_sources.diag.mod_missing",
                    s.modId).getString();
        }
        Float v = adapter.readStat(s.id);
        if (v == null) {
            return "§c" + Component.translatable("z80zhealthbar.custom_sources.diag.read_failed").getString();
        }
        Float m = adapter.resolveMax(minecraft.player);
        return "§a" + String.format(java.util.Locale.ROOT, "%.1f", v)
                + (m == null ? "" : " / " + String.format(java.util.Locale.ROOT, "%.1f", m));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
