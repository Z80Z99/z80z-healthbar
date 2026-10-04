package com.z80z99.z80zhealthbar.mobdisplay.plaques;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** 心形图标类型 - 根据生物状态自适应选择 */
public enum HeartType {
    // Wave 7 修复（崩溃修复后像素验证）：坐标改用 vanilla icons.png 实际布局
    // (minecraft:textures/gui/icons.png 256x256；9x9 图标，行 0 = y0，行 1 = y9)
    CONTAINER(16, 0),   // 空槽心 (深色)
    NORMAL(52, 0),      // 正常红心
    POISON(88, 0),      // 中毒绿心
    WITHER(124, 0),     // 凋零暗心
    ABSORBING(160, 0),  // 吸收金心
    FROZEN(178, 0);     // 冰冻蓝心

    private final int textureX;
    private final int textureY;

    HeartType(int textureX, int textureY) {
        this.textureX = textureX;
        this.textureY = textureY;
    }

    public int getTextureX() { return textureX; }
    public int getTextureY() { return textureY; }

    public static HeartType selectFor(LivingEntity entity) {
        if (entity.getAbsorptionAmount() > 0) return ABSORBING;
        if (entity.hasEffect(MobEffects.POISON)) return POISON;
        if (entity.hasEffect(MobEffects.WITHER)) return WITHER;
        if (entity.getTicksFrozen() > 0) return FROZEN;
        return NORMAL;
    }
}
