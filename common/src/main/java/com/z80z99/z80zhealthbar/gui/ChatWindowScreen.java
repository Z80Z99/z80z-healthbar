package com.z80z99.z80zhealthbar.gui;

import com.z80z99.z80zhealthbar.chat.ChatWindowManager;
import com.z80z99.z80zhealthbar.chat.ChatWindowRenderer;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * 自定义聊天输入界面（1.0.150）：继承 ChatScreen 全委托（命令 Tab 补全/输入历史/Esc/回车
 * 原样保留）,只做三件事：
 * <ul>
 *   <li>输入框重定位——吸附窗口下沿,或独立位置（可拖离分离）;</li>
 *   <li>窗口标题栏拖动 / 右下角缩放 / 输入框拖离;</li>
 *   <li>窗口内消息行点击（链接/命令）。</li>
 * </ul>
 * 聊天记录窗本体由 ChatWindowRenderer 经 HUD 回调持续绘制（打开时附加标题栏/角标）。
 */
public class ChatWindowScreen extends ChatScreen {

    private static final int INPUT_H = 12;

    private int dragMode; // 0 无 1 窗口拖动 2 窗口缩放 3 输入框拖动
    private int grabDX, grabDY;

    public ChatWindowScreen(String initial) {
        super(initial);
    }

    private com.z80z99.z80zhealthbar.config.configs.ChatWindowConfig cfg() {
        return ChatWindowManager.cfg();
    }

    /** 输入框解析位置（屏幕坐标） */
    private int[] inputPos() {
        var c = cfg();
        if (c.inputAttached) {
            int[] r = ChatWindowRenderer.rect();
            float s = (float) c.scale;
            return new int[]{r[0] + (int) (2 * s), r[1] + (int) ((r[3] + 2) * s)};
        }
        int x = c.inputX < 0 ? Math.max(4, width / 2 - 100) : c.inputX;
        int y = c.inputY < 0 ? height - INPUT_H - 6 : c.inputY;
        return new int[]{x, y};
    }

    private void layoutInput() {
        int[] p = inputPos();
        int boxW = Math.min(width - 8, (int) (ChatWindowRenderer.rect()[2] * (float) cfg().scale) - 8);
        boxW = Math.max(80, boxW);
        input.setX(p[0]);
        input.setY(p[1]);
        input.setWidth(boxW);
    }

    @Override
    protected void init() {
        super.init(); // 原版:input + commandSuggestions + 历史/补全全部就位
        layoutInput();
        ChatWindowRenderer.openedAt = System.currentTimeMillis();
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        layoutInput();
    }

    private boolean inputHit(double mx, double my) {
        return mx >= input.getX() - 2 && mx <= input.getX() + input.getWidth() + 2
                && my >= input.getY() - 2 && my <= input.getY() + input.getHeight() + 2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 窗口内链接点击优先
        var style = ChatWindowRenderer.styleAt(mx, my);
        if (style != null) {
            handleComponentClicked(style);
            return true;
        }
        var c = cfg();
        if (btn == 0) {
            if (ChatWindowRenderer.cornerHit(mx, my)) {
                dragMode = 2;
                var r = ChatWindowRenderer.rect();
                grabDX = (int) mx - (r[0] + (int) (r[2] * c.scale));
                grabDY = (int) my - (r[1] + (int) (r[3] * c.scale));
                return true;
            }
            if (ChatWindowRenderer.headerHit(mx, my)) {
                dragMode = 1;
                var r = ChatWindowRenderer.rect();
                grabDX = (int) mx - r[0];
                grabDY = (int) my - r[1];
                return true;
            }
            // 输入框空白区拖动（点输入框文字区=聚焦编辑,由 super 处理——精确判定:框内上半文字带）
            if (inputHit(mx, my) && my > input.getY() + 4) {
                dragMode = 3;
                grabDX = (int) mx - input.getX();
                grabDY = (int) my - input.getY();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        var c = cfg();
        if (dragMode == 1) {
            int[] r = ChatWindowRenderer.rect();
            c.x = ChatWindowManager.clamp((int) mx - grabDX, 0, Math.max(0, width - 10));
            c.y = ChatWindowManager.clamp((int) my - grabDY, 0, Math.max(0, height - 10));
            ConfigManager.saveConfig();
            layoutInput();
            return true;
        }
        if (dragMode == 2) {
            float s = (float) c.scale;
            int[] r = ChatWindowRenderer.rect();
            c.width = ChatWindowManager.clamp((int) ((mx - grabDX - r[0]) / s),
                    ChatWindowManager.MIN_W, ChatWindowManager.MAX_W);
            c.height = ChatWindowManager.clamp((int) ((my - grabDY - r[1]) / s),
                    ChatWindowManager.MIN_H, ChatWindowManager.MAX_H);
            ConfigManager.saveConfig();
            layoutInput();
            return true;
        }
        if (dragMode == 3) {
            c.inputAttached = false;
            c.inputX = ChatWindowManager.clamp((int) mx - grabDX, 0, Math.max(0, width - 40));
            c.inputY = ChatWindowManager.clamp((int) my - grabDY, 0, Math.max(0, height - INPUT_H));
            ConfigManager.saveConfig();
            layoutInput();
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragMode = 0;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (ChatWindowRenderer.inside(mx, my)) {
            ChatWindowRenderer.onScroll(delta);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // 不画原版 ChatScreen 的深色全屏渐变;只画输入框底板 + super 的输入框/补全建议
        var c = cfg();
        int[] p = inputPos();
        int a = (int) (0.8 * 255);
        g.fill(p[0] - 3, p[1] - 3, p[0] + input.getWidth() + 3,
                p[1] + input.getHeight() + 3, (a / 2) << 24);
        g.fill(p[0] - 2, p[1] - 2, p[0] + input.getWidth() + 2,
                p[1] + input.getHeight() + 2, a << 24);
        g.renderOutline(p[0] - 2, p[1] - 2, input.getWidth() + 4,
                input.getHeight() + 4, 0x507FD4FF);
        if (!c.inputAttached) {
            String hint = net.minecraft.network.chat.Component.translatable(
                    "z80zhealthbar.chat.input_detached").getString();
            font.drawInBatch(hint, p[0], p[1] - 12, 0x8088A0B8, true,
                    g.pose().last().pose(), g.bufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }
        super.render(g, mx, my, pt); // input + commandSuggestions(跟随 input 位置)
    }
}
