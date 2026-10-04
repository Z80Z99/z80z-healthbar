package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import net.minecraft.client.gui.Gui;

/** HUD 渲染上下文，桥梁对象 */
public class RenderGui {
    private final Gui gui;

    public RenderGui(Gui gui) {
        this.gui = gui;
    }

    public Gui gui() {
        return gui;
    }

    public int getLeftHeight(int offset) {
        return OverlayManager.cornerLeftHeight + offset;
    }

    public int getRightHeight(int offset) {
        return OverlayManager.cornerRightHeight + offset;
    }

    public void setLeftHeight(int value) {
        OverlayManager.cornerLeftHeight = value;
    }

    public void setRightHeight(int value) {
        OverlayManager.cornerRightHeight = value;
    }
}
