package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.mobdisplay.HealthDisplayRenderer;
import com.z80z99.z80zhealthbar.mobdisplay.MobDisplayRenderer;
import com.z80z99.z80zhealthbar.mobdisplay.MobHealthBarStyle;
import com.z80z99.z80zhealthbar.mobdisplay.MobVisibilityChecker;
import com.z80z99.z80zhealthbar.status.EntityStatusSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;

/**
 * F3 调试叠加层（诊断工具）：F3 打开且准星指向生物时，显示本 MOD 读取到的原始数值
 * 与将要绘制的血条文本，用于核对"读到的"与"画出的"是否一致。
 * 文本为诊断输出，仅 F3 屏可见，不进入正常 HUD 语言键体系。
 */
public final class EntityDebugOverlay {

    private EntityDebugOverlay() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.renderDebug) return;
        if (!(mc.crosshairPickEntity instanceof LivingEntity le)) return;
        var player = mc.player;
        if (player == null || le == player) return;

        EntityStatusSnapshot snap = EntityStatusSnapshot.capture(le, le.distanceToSqr(player));
        Font font = mc.font;
        String l1 = "[Z80Z] " + BuiltInRegistries.ENTITY_TYPE.getKey(le.getType())
                + " id=" + le.getId();
        String l2 = String.format(java.util.Locale.ROOT,
                "raw hp=%.2f max=%.2f abs=%.2f", le.getHealth(), le.getMaxHealth(), le.getAbsorptionAmount());
        String l3 = "snapshot text=\"" + MobHealthBarStyle.formatValue(snap.health) + "/"
                + MobHealthBarStyle.formatValue(snap.maxHealth) + "\""
                + " barW=" + new HealthDisplayRenderer().getBarWidth(snap);
        // 可见性诊断：血条为什么没显示（原因名见 MobVisibilityChecker.reasonName）+
        // 遮挡采样可见比例（≥0.25 才绘制；3 格内近距早退 → near/no-sample）
        double dist = Math.sqrt(le.distanceToSqr(player));
        int reason = MobVisibilityChecker.check(le, player, le.distanceToSqr(player));
        float ratio = MobDisplayRenderer.lastVisibleRatio(le.getId());
        String l4 = "vis=" + MobVisibilityChecker.reasonName(reason)
                + " dist=" + String.format(java.util.Locale.ROOT, "%.1f", dist)
                + " occl=" + (ratio < 0 ? "near/no-sample" : String.format(java.util.Locale.ROOT, "%.2f", ratio));
        var addons = com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().entityAddons;
        String l5 = "style=" + com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().entityStyle
                + " addon armor/tough/air=" + addons.armorRow + "/" + addons.toughnessRow + "/" + addons.airRow
                + " air=" + snap.airSupply + "/" + snap.maxAirSupply
                + " eyeWater=" + snap.eyeInWater
                + " armor=" + snap.armor + " tough=" + snap.armorToughness;

        int y = 100;
        for (String line : new String[]{l1, l2, l3, l4, l5}) {
            g.fill(4, y - 2, 6 + font.width(line), y + 9, 0x90000000);
            g.drawString(font, line, 6, y, 0xFFFF55, true);
            y += 11;
        }
    }
}
