package com.z80z99.z80zhealthbar.mobdisplay;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** 实体选择器 - 替代 MobPlaques 的 MobPlaquesSelector */
public enum MobPlaquesSelector {
    ALL(e -> true),
    MONSTERS(e -> e instanceof Enemy || e.getType().getCategory() == MobCategory.MONSTER),
    CREATURES(e -> e.getType().getCategory() == MobCategory.CREATURE),
    AMBIENT(e -> e.getType().getCategory() == MobCategory.AMBIENT),
    WATER_CREATURES(e -> e.getType().getCategory() == MobCategory.WATER_CREATURE),
    MISC(e -> e.getType().getCategory() == MobCategory.MISC),
    PLAYERS(e -> e.getType() == EntityType.PLAYER);

    private final Predicate<LivingEntity> predicate;

    MobPlaquesSelector(Predicate<LivingEntity> predicate) {
        this.predicate = predicate;
    }

    public boolean matches(LivingEntity entity) {
        return predicate.test(entity);
    }

    public static MobPlaquesSelector fromString(String name) {
        for (MobPlaquesSelector s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        return ALL;
    }

    /** 检查实体是否被允许显示 */
    public static boolean isAllowed(LivingEntity entity,
                                     List<MobPlaquesSelector> allowed,
                                     List<MobPlaquesSelector> disallowed) {
        for (MobPlaquesSelector d : disallowed) {
            if (d.matches(entity)) return false;
        }
        if (allowed.isEmpty()) return true;
        for (MobPlaquesSelector a : allowed) {
            if (a.matches(entity)) return true;
        }
        return false;
    }
}
