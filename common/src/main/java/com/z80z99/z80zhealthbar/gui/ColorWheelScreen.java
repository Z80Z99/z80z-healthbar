package com.z80z99.z80zhealthbar.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * 颜色轮盘（HSV 选择器）：SV 方块（横=饱和度,纵=亮度反转）+ 色相竖条 + alpha 竖条
 * + hex 直输入 + 新旧色预览。确定后经 {@code onDone} 写回（#RRGGBBAA）;
 * "清除"回调 onDone 传空串（= 恢复继承默认/状态自动变色）。
 *
 * <p>2026-10-09 修复:(1) 性能——按钮此前在 render() 里 addRenderableWidget,每帧累积
 * 3 个 widget（实测"取色器界面卡顿"根因）,全部移入 init();(2) 布局——hex 框/预览色块/
 * alpha 条/按钮相互重叠,重排为分区布局（SV→预览+hex→按钮,alpha 条纳入右侧栏）。
 */
public class ColorWheelScreen extends Screen {

    private static final int SV_W = 200, SV_H = 120, BAR_W = 14, GAP = 14;

    private final Screen parent;
    private final String initial;
    private final String title;
    private final Consumer<String> onDone;

    private float hue = 0f;          // 0..1
    private float sat = 1f, val = 1f; // 0..1
    private float alpha = 1f;        // 0..1
    private EditBox hexBox;
    private boolean dragSv, dragHue, dragAlpha;

    private int svX, svY, hueX, alphaY, prevY, hexX, hexY, btnY, y0;

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
        int cx = (width - (SV_W + GAP + BAR_W)) / 2;
        y0 = Math.max(34, (height - 236) / 2);
        svX = cx;
        svY = y0 + 20;
        hueX = svX + SV_W + GAP;
        alphaY = svY + SV_H + 12;   // 右侧栏:色相条下方接 alpha 条
        prevY = svY + SV_H + 12;    // 左列:预览色块行
        hexX = svX + 96;
        hexY = prevY + 11;
        btnY = prevY + 60;

        hexBox = new EditBox(font, hexX, hexY, 116, 18, Component.translatable("z80zhealthbar.editor.color.hex"));
        hexBox.setMaxLength(9);
        hexBox.setValue(currentHex());
        hexBox.setResponder(this::applyHex);
        addRenderableWidget(hexBox);

        // 按钮在此创建一次（此前在 render() 每帧添加,是卡顿根因）
        addRenderableWidget(Button.builder(
                Component.translatable("z80zhealthbar.editor.color.ok"), b -> done())
                .bounds(width / 2 - 102, btnY, 64, 18).build());
        addRenderableWidget(Button.builder(
                Component.translatable("z80zhealthbar.editor.color.clear"), b -> finish(""))
                .bounds(width / 2 - 32, btnY, 64, 18).build());
        addRenderableWidget(Button.builder(
                Component.translatable("z80zhealthbar.editor.color.cancel"), b -> onClose())
                .bounds(width / 2 + 38, btnY, 64, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, y0 - 6, 0xFFCFE6FF);

        // SV 方块：底层 横向 白→纯色 渐变,上层 纵向 透明→黑
        g.fillGradient(svX, svY, svX + SV_W, svY + SV_H, 0xFFFFFFFF, 0xFF000000 | hueRgb(hue));
        g.fillGradient(svX, svY, svX + SV_W, svY + SV_H, 0x00000000, 0xFF000000);
        g.renderOutline(svX - 1, svY - 1, SV_W + 2, SV_H + 2, 0x60FFFFFF);
        int px = svX + (int) (sat * (SV_W - 1));
        int py = svY + (int) ((1f - val) * (SV_H - 1));
        g.fill(px - 5, py, px + 5, py + 1, 0xFFFFFFFF);
        g.fill(px, py - 5, px + 1, py + 5, 0xFFFFFFFF);

        // 色相竖条（6 段渐变）+ 游标
        float[] stops = {0f, 1f / 6, 2f / 6, 3f / 6, 4f / 6, 5f / 6, 1f};
        int segH = SV_H / 6;
        for (int i = 0; i < 6; i++) {
            g.fillGradient(hueX, svY + i * segH, hueX + BAR_W, svY + (i + 1) * segH + (i == 5 ? SV_H % 6 : 0),
                    0xFF000000 | hueRgb(stops[i]), 0xFF000000 | hueRgb(stops[i + 1]));
        }
        g.renderOutline(hueX - 1, svY - 1, BAR_W + 2, SV_H + 2, 0x60FFFFFF);
        int hy = svY + (int) (hue * (SV_H - 1));
        g.fill(hueX - 3, hy - 1, hueX, hy + 2, 0xFFFFFFFF);
        g.fill(hueX + BAR_W, hy - 1, hueX + BAR_W + 3, hy + 2, 0xFFFFFFFF);

        // alpha 竖条（色相条正下方,同列不重叠）:下=透明 上=不透明;叠当前纯色示色
        int ah = 46;
        g.fillGradient(hueX, alphaY, hueX + BAR_W, alphaY + ah, 0xFF000000, 0xFFFFFFFF);
        g.fillGradient(hueX, alphaY, hueX + BAR_W, alphaY + ah, (currentRgb() & 0xFFFFFF) | 0xFF000000, currentRgb());
        g.renderOutline(hueX - 1, alphaY - 1, BAR_W + 2, ah + 2, 0x60FFFFFF);
        int aY = alphaY + (int) ((1f - alpha) * (ah - 1));
        g.fill(hueX - 3, aY, hueX, aY + 2, 0xFFFFFFFF);
        g.fill(hueX + BAR_W, aY, hueX + BAR_W + 3, aY + 2, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("z80zhealthbar.editor.color.alpha").getString(),
                hueX - 40, alphaY + ah / 2 - 4, 0xFFB8C4D0);

        // 预览：新色 / 旧色（左列,与 hex 框同行不重叠——新色 40 宽、旧色 40 宽、hex 右侧）
        g.fill(svX, prevY, svX + 40, prevY + 40, 0xFF000000);
        g.fill(svX + 1, prevY + 1, svX + 39, prevY + 39, currentArgb());
        g.renderOutline(svX, prevY, 40, 40, 0x80FFFFFF);
        g.fill(svX + 44, prevY, svX + 84, prevY + 40, 0xFF000000);
        g.fill(svX + 45, prevY + 1, svX + 83, prevY + 39, parseSafe(initial));
        g.renderOutline(svX + 44, prevY, 40, 40, 0x60FFFFFF);
        g.drawString(font, "hex:", hexX - 30, hexY + 5, 0xFFB8C4D0);

        super.render(g, mouseX, mouseY, partialTick);
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
        dragSv = inRect(mx, my, svX, svY, SV_W, SV_H);
        dragHue = inRect(mx, my, hueX, svY, BAR_W, SV_H);
        dragAlpha = inRect(mx, my, hueX, alphaY, BAR_W, 46);
        if (dragSv) pickSv(mx, my);
        if (dragHue) pickHue(my);
        if (dragAlpha) pickAlpha(my);
        if (dragSv || dragHue || dragAlpha) syncHexBox();
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragSv) pickSv(mx, my);
        if (dragHue) pickHue(my);
        if (dragAlpha) pickAlpha(my);
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
        sat = (float) clamp01((mx - svX) / (SV_W - 1.0));
        val = 1f - (float) clamp01((my - svY) / (SV_H - 1.0));
    }

    private void pickHue(double my) {
        hue = (float) clamp01((my - svY) / (SV_H - 1.0));
    }

    private void pickAlpha(double my) {
        alpha = 1f - (float) clamp01((my - alphaY) / 45.0);
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private void syncHexBox() {
        if (hexBox != null && !hexBox.isFocused()) hexBox.setValue(currentHex());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !hexBox.isFocused()) {
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
