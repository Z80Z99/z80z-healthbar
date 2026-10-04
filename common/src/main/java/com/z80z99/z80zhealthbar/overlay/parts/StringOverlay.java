package com.z80z99.z80zhealthbar.overlay.parts;

import com.z80z99.z80zhealthbar.overlay.BaseOverlay;
import com.z80z99.z80zhealthbar.overlay.OverlayManager;
import com.z80z99.z80zhealthbar.overlay.RenderGui;
import net.minecraft.client.gui.GuiGraphics;

public class StringOverlay extends BaseOverlay {

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int w, int h) {
        OverlayManager.renderStrings(graphics);
    }
}
