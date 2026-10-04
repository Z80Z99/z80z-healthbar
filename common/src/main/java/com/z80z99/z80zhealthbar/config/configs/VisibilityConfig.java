package com.z80z99.z80zhealthbar.config.configs;

import com.z80z99.z80zhealthbar.mobdisplay.MobPlaquesSelector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VisibilityConfig {
    public boolean showOnSelf = false;
    public boolean showOnPlayers = true;
    public boolean showOnHostile = true;
    public boolean showOnPassive = true;
    public boolean showOnBoss = true;
    public boolean showOnArmorStands = false;
    public boolean showOnFullHealthWithoutAbsorption = true;
    public boolean showOnFullHealthWithAbsorption = false;
    /** 视线遮挡检测：仅显示玩家视线可达的实体（射线检测有性能开销，默认关闭保持原版名牌体验） */
    public boolean showOnlyWhenVisible = false;
    public boolean showOnAggro = false;
    public boolean showHoveredMob = false;
    public boolean showDamaged = false;
    public double maxDistance = 64.0;
    /** 同屏最多显示的实体状态栏数量（0 = 不限制）；实现为当帧先到先得，约 2 秒后回收名额 */
    public int maxConcurrentDisplays = 0;
    /** 实体进入视野后淡入时长（tick，0 = 立即显示） */
    public int fadeInTicks = 4;
    /** 实体死亡后状态栏随死亡动画淡出 */
    public boolean fadeOnDeath = true;
    public List<String> entityBlacklist = new ArrayList<>();

    // MobPlaquesSelector 字符串列表 (ALL, MONSTERS, CREATURES, AMBIENT 等)
    public List<String> allowedSelectors = List.of("ALL");
    public List<String> disallowedSelectors = new ArrayList<>();

    // 运行时解析缓存
    public transient Set<EntityType<?>> parsedBlacklist = new HashSet<>();
    public transient List<MobPlaquesSelector> parsedAllowed = new ArrayList<>();
    public transient List<MobPlaquesSelector> parsedDisallowed = new ArrayList<>();

    public void parseAll() {
        parseBlacklist();
        parseSelectors();
    }

    public void parseBlacklist() {
        parsedBlacklist.clear();
        for (String entry : entityBlacklist) {
            if (entry == null || entry.isBlank()) continue;
            // Wave 6: 支持高级黑名单语法 (合并计划承诺):
            //   #namespace:path  -> 整个 tag 下的实体都加入黑名单
            //   prefix*          -> 前缀通配 (例 minecraft:zombie* 匹配所有 zombie 变种)
            //   namespace:path   -> 精确匹配 (legacy)
            //   ! 前缀 -> 排除语义 (留 V1.5)
            if (entry.startsWith("#")) {
                String tagLoc = entry.substring(1);
                var tagKey = net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.ENTITY_TYPE,
                        new ResourceLocation(tagLoc));
                var tagOpt = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getTag(tagKey);
                tagOpt.ifPresent(named -> named.stream().forEach(holder -> parsedBlacklist.add(holder.value())));
            } else if (entry.endsWith("*")) {
                String prefix = entry.substring(0, entry.length() - 1);
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.forEach(type -> {
                    var loc = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    if (loc != null && loc.toString().startsWith(prefix)) {
                        parsedBlacklist.add(type);
                    }
                });
            } else {
                EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .get(new ResourceLocation(entry));
                if (type != null) parsedBlacklist.add(type);
            }
        }
    }

    private void parseSelectors() {
        parsedAllowed.clear();
        parsedDisallowed.clear();
        for (String s : allowedSelectors) parsedAllowed.add(MobPlaquesSelector.fromString(s));
        for (String s : disallowedSelectors) parsedDisallowed.add(MobPlaquesSelector.fromString(s));
    }

    public boolean isBlacklisted(EntityType<?> type) {
        return parsedBlacklist.contains(type);
    }
}
