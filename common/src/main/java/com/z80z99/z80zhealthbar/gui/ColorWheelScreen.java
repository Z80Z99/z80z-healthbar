package com.z80z99.z80zhealthbar.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * 颜色轮盘（HSV 选择器）：SV 方块（横=饱和度,纵=亮度反转）+ 色相竖条 + alpha 横条
 * + hex 直输入 + 原色/新色预览。确定后经 {@code onDone} 写回（AARRGGBB,无 #）;
 * "清除"回调 onDone 传空串（= 恢复继承默认/状态自动变色）。
 *
 * <p>2026-10-14 重排版（用户反馈:文字过小、拾取点被裁、条与条混淆、按钮重心偏移）：
 * (1) 整卡加深色半透明底板+描边,标题 2 倍放大,提升对游戏背景的可读性;
 * (2) alpha 从右侧竖条改为 SV 正下方的横向棋盘格条（棋盘=透明指示）,与色相竖条形态区分,
 *     "不透明度"标签直接置于条左侧;（3) SV/色相/alpha 三处游标统一加黑描边,
 *     SV 十字臂在方块边界内裁切不再溢出;（4) hex 输入带 # 前缀,与预览同行分区;
 *     (5) 确定/清除/取消按钮组对齐内容区几何中心;（6) hex 框内按 Enter=确定。
 * <p>历史：2026-10-09 修复按钮每帧重复 addRenderableWidget 的卡顿与元素重叠。
 */
public class ColorWheelScreen extends Screen {

    private static final int SV_W = 200, SV_H = 120, BAR_W = 14, GAP = 14;
    /** 响应式实际尺寸(小屏时缩小 SV 方块,保证面板完整入屏) */
    private int svW = SV_W, svH = SV_H;
    private static final int AB_H = 12;   // alpha 横条高
    private static final int CHECK = 6;   // 棋盘格单元格
    private static final int BTN_W = 64, BTN_GAP = 6, BTN_H = 18;

    private final Screen parent;
    private final String initial;
    private final String title;
    private final Consumer<String> onDone;

    private float hue = 0f;          // 0..1
    private float sat = 1f, val = 1f; // 0..1
    private float alpha = 1f;        // 0..1
    private EditBox hexBox;
    private boolean dragSv, dragHue, dragAlpha;

    private int svX, svY, hueX, abX, abY, abW, prevY, hexX, hexY, btnY, y0;
    private int panelX0, panelY0, panelX1, panelY1;

    public ColorWheelScreen(Screen parent, String title, String initialHexARGB, Consumer<String> onDone) {
        super(Component.literal(title == null ? "" : title));
        this.parent = parent;
        this.title = title;
        this.onDone = onDone;
        this.initial = initialHexARGB == null ? "" : initialHexARGB;
        applyHex(initial);
    }

    /** 解析 #RRGGBB / #AARRGGBB（容忍缺 #）;失败保持当前值 */
    private void applyHex(String s) {
        if (s == null) return;
        String h = s.replace("#", "").trim();
        if (h.isEmpty()) return;
        try {
            if (h.length() == 6) {
                setRGB((int) Long.parseLong(h, 16));
                alpha = 1f;
            } else if (h.length() == 8) {
                int v = (int) Long.parseLong(h, 16); // AARRGGBB（内部存储惯例）
                setRGB(v & 0xFFFFFF);
                alpha = ((v >>> 24) & 0xFF) / 255f;
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void setRGB(int rgb) { // rgb = RRGGBB
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        val = max;
        sat = max <= 0f ? 0f : (max - min) / max;
        if (max - min < 1e-6f) { hue = 0f; return; }
        float d = max - min;
        if (max == r) hue = ((g - b) / d) % 6f;
        else if (max == g) hue = (b - r) / d + 2f;
        else hue = (r - g) / d + 4f;
        hue /= 6f;
        if (hue < 0) hue += 1f;
    }

    private int hueRgb(float h) {
        return java.awt.Color.HSBtoRGB(h, 1f, 1f) & 0xFFFFFF;
    }

    private int currentRgb() {
        return java.awt.Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF;
    }

    private int currentArgb() {
        return ((int) (alpha * 255f) << 24) | currentRgb();
    }

    private String currentHex() {
        return String.format("%02X%06X", (int) (alpha * 255f), currentRgb());
    }

    @Override
    protected void init() {
        // 响应式:面板总高 ≈ 26+svH+8+AB_H+8+32+8+BTN_H+16,总宽 = svW+gap+BAR_W+28
        // 小屏时等比缩小 SV 方块（最小 120x72）,保证面板完整入屏、按钮不被裁（曾固定 256x248）
        int availW = Math.max(160, width - 20);
        int availH = Math.max(150, height - 20);
        svW = Math.max(120, Math.min(SV_W, availW - GAP - BAR_W - 28));
        int fixedH = 26 + 8 + AB_H + 8 + 32 + 8 + BTN_H + 16;
        svH = Math.max(72, Math.min(SV_H, availH - fixedH));
        int contentW = svW + GAP + BAR_W;
        int cx = Math.max(14, (width - contentW) / 2);
        svX = cx;
        hueX = svX + svW + GAP;
        int totalH = fixedH + svH;
        y0 = Math.max(10, (height - totalH) / 2 + 8);
        svY = y0 + 26;                       // 标题(2x,占~20px)下方
        abY = svY + svH + 8;                 // alpha 横条:SV 正下方
        abX = svX + 52;                      // 左侧留出"不透明度"标签
        abW = svW - 52;
        prevY = abY + AB_H + 8;              // 原色/新色预览行
        hexX = svX + 112;
        hexY = prevY + 7;                    // 输入框与 32 高的预览块垂直居中
        btnY = prevY + 32 + 8;

        panelX0 = svX - 14;
        panelY0 = y0 - 8;
        panelX1 = svX + contentW + 14;
        panelY1 = btnY + BTN_H + 8;

        hexBox = new EditBox(font, hexX, hexY, contentW - 112, 18,
                Component.translatable("z80zhealthbar.editor.color.hex"));
        hexBox.setMaxLength(9);
        hexBox.setValue("#" + currentHex());
        hexBox.setResponder(this::applyHex);
        addRenderableWidget(hexBox);

        // 按钮组对齐内容区几何中心（3×64 + 2×6 = 204,内容区宽 228）
        int bx = svX + (contentW - (BTN_W * 3 + BTN_GAP * 2)) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("z80zhealthbar.editor.color.ok"), b -> done())
                .bounds(bx, btnY, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(
                Component.translatable("z80zhealthbar.editor.color.clear"), b -> finish(""))
                .bounds(bx + BTN_W + BTN_GAP, btnY, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(
                Component.translatable("z80zhealthbar.editor.color.cancel"), b -> onClose())
                .bounds(bx + (BTN_W + BTN_GAP) * 2, btnY, BTN_W, BTN_H).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // 深色底板：所有文字/控件衬在半透明面板上,不与游戏画面直接混合
        g.fill(panelX0, panelY0, panelX1, panelY1, 0xC8141A21);
        g.renderOutline(panelX0, panelY0, panelX1 - panelX0, panelY1 - panelY0, 0xFF5A6678);
        g.renderOutline(panelX0 + 1, panelY0 + 1, panelX1 - panelX0 - 2, panelY1 - panelY0 - 2, 0xFF0B0E13);

        // 标题 2 倍放大（整数倍缩放保持像素锐利）
        var pose = g.pose();
        pose.pushPose();
        pose.scale(2f, 2f, 1f);
        g.drawCenteredString(font, title, width / 4, (y0 + 2) / 2, 0xFFEAF2FF);
        pose.popPose();

        // SV 方块：底层 横向 白→纯色 渐变,上层 纵向 透明→黑
        g.fillGradient(svX, svY, svX + svW, svY + svH, 0xFFFFFFFF, 0xFF000000 | hueRgb(hue));
        g.fillGradient(svX, svY, svX + svW, svY + svH, 0x00000000, 0xFF000000);
        g.renderOutline(svX - 1, svY - 1, svW + 2, svH + 2, 0x60FFFFFF);
        drawSvCursor(g);

        // 色相竖条（6 段渐变）+ 游标
        float[] stops = {0f, 1f / 6, 2f / 6, 3f / 6, 4f / 6, 5f / 6, 1f};
        int segH = svH / 6;
        for (int i = 0; i < 6; i++) {
            g.fillGradient(hueX, svY + i * segH, hueX + BAR_W, svY + (i + 1) * segH + (i == 5 ? svH % 6 : 0),
                    0xFF000000 | hueRgb(stops[i]), 0xFF000000 | hueRgb(stops[i + 1]));
        }
        g.renderOutline(hueX - 1, svY - 1, BAR_W + 2, svH + 2, 0x60FFFFFF);
        drawHueCursor(g);

        // alpha 横条:棋盘格示透明 + 当前色由透明(左)渐至不透明(右)
        g.drawString(font, Component.translatable("z80zhealthbar.editor.color.alpha").getString(),
                svX, abY + 2, 0xFFD5DEE9);
        drawChecker(g, abX, abY, abW, AB_H);
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            int a = Math.round(255f * i / (segs - 1));
            int x0 = abX + Math.round(abW * i / (float) segs);
            int x1 = abX + Math.round(abW * (i + 1) / (float) segs);
            g.fill(x0, abY, x1, abY + AB_H, (currentRgb() & 0xFFFFFF) | (a << 24));
        }
        g.renderOutline(abX - 1, abY - 1, abW + 2, AB_H + 2, 0x60FFFFFF);
        drawAlphaCursor(g);

        // 预览:左原色 → 右新色(棋盘格底,可示半透明);hex 输入同行
        g.fill(svX, prevY, svX + 32, prevY + 32, 0xFF000000);
        g.fill(svX + 1, prevY + 1, svX + 31, prevY + 31, parseSafe(initial));
        g.renderOutline(svX, prevY, 32, 32, 0x60FFFFFF);
        drawChecker(g, svX + 40, prevY, 32, 32);
        g.fill(svX + 40, prevY, svX + 72, prevY + 32, currentArgb());
        g.renderOutline(svX + 40, prevY, 32, 32, 0x80FFFFFF);
        g.drawString(font, Component.translatable("z80zhealthbar.editor.color.hex_label").getString(),
                svX + 84, hexY + 5, 0xFFD5DEE9);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** SV 十字游标：黑描边白十字,臂在方块边界内裁切（不再溢出被裁） */
    private void drawSvCursor(GuiGraphics g) {
        int px = svX + Math.round(sat * (svW - 1));
        int py = svY + Math.round((1f - val) * (svH - 1));
        int ax0 = Math.max(svX, px - 5), ax1 = Math.min(svX + svW, px + 6);
        int ay0 = Math.max(svY, py - 5), ay1 = Math.min(svY + svH, py + 6);
        g.fill(ax0, py - 1, ax1, py + 2, 0xFF000000);
        g.fill(ax0, py, ax1, py + 1, 0xFFFFFFFF);
        g.fill(px - 1, ay0, px + 2, ay1, 0xFF000000);
        g.fill(px, ay0, px + 1, ay1, 0xFFFFFFFF);
    }

    /** 色相游标:条两侧横向 tick,黑底白芯 */
    private void drawHueCursor(GuiGraphics g) {
        int hy = svY + Math.round(hue * (svH - 1));
        g.fill(hueX - 4, hy - 2, hueX, hy + 3, 0xFF000000);
        g.fill(hueX + BAR_W, hy - 2, hueX + BAR_W + 4, hy + 3, 0xFF000000);
        g.fill(hueX - 3, hy - 1, hueX, hy + 2, 0xFFFFFFFF);
        g.fill(hueX + BAR_W, hy - 1, hueX + BAR_W + 3, hy + 2, 0xFFFFFFFF);
    }

    /** alpha 游标:贯穿横条的竖线,黑底白芯,上下各探出 2px */
    private void drawAlphaCursor(GuiGraphics g) {
        int ax = abX + Math.round(alpha * (abW - 1));
        g.fill(ax - 2, abY - 2, ax + 3, abY + AB_H + 2, 0xFF000000);
        g.fill(ax, abY - 2, ax + 1, abY + AB_H + 2, 0xFFFFFFFF);
    }

    /** 棋盘格（透明度指示底） */
    private void drawChecker(GuiGraphics g, int x, int y, int w, int h) {
        for (int cy = 0; cy < h; cy += CHECK) {
            for (int cx = 0; cx < w; cx += CHECK) {
                int c = (cx / CHECK + cy / CHECK) % 2 == 0 ? 0xFF3F3F3F : 0xFF282828;
                g.fill(x + cx, y + cy, Math.min(x + cx + CHECK, x + w), Math.min(y + cy + CHECK, y + h), c);
            }
        }
    }

    private int parseSafe(String s) {
        try {
            String h = (s == null ? "" : s.replace("#", ""));
            if (h.length() == 6) return 0xFF000000 | (int) Long.parseLong(h, 16);
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    private void done() {
        finish(currentHex());
    }

    private void finish(String value) {
        onDone.accept(value);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent); // 取消:不写回
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        dragSv = inRect(mx, my, svX, svY, svW, svH);
        dragHue = inRect(mx, my, hueX, svY, BAR_W, svH);
        dragAlpha = inRect(mx, my, abX, abY, abW, AB_H);
        if (dragSv) pickSv(mx, my);
        if (dragHue) pickHue(my);
        if (dragAlpha) pickAlpha(mx);
        if (dragSv || dragHue || dragAlpha) syncHexBox();
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragSv) pickSv(mx, my);
        if (dragHue) pickHue(my);
        if (dragAlpha) pickAlpha(mx);
        if (dragSv || dragHue || dragAlpha) syncHexBox();
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragSv = dragHue = dragAlpha = false;
        return super.mouseReleased(mx, my, button);
    }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void pickSv(double mx, double my) {
        sat = (float) clamp01((mx - svX) / (svW - 1.0));
        val = 1f - (float) clamp01((my - svY) / (svH - 1.0));
    }

    private void pickHue(double my) {
        hue = (float) clamp01((my - svY) / (svH - 1.0));
    }

    private void pickAlpha(double mx) {
        alpha = (float) clamp01((mx - abX) / (abW - 1.0));
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private void syncHexBox() {
        if (hexBox != null && !hexBox.isFocused()) hexBox.setValue("#" + currentHex());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hexBox != null && hexBox.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            done();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (hexBox == null || !hexBox.isFocused())) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
