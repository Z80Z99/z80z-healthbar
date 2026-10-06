package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig.ComponentLayout;
import com.z80z99.z80zhealthbar.layout.HudLayoutSolver;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import com.z80z99.z80zhealthbar.util.GuiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CUSTOM 模式渲染器：按 HudLayoutConfig/HudLayoutSolver 输出组件。
 *
 * <p>每个组件已拆解为三类元素，均可独立配置/锚定/定位（布局编辑器内操作）：
 * <ul>
 *   <li>条（{@code key}）：卡片底 + 状态填充 +（未分离时）图标与数值文本；</li>
 *   <li>文本（{@code key.text}）：配置了独立锚点时脱离条单独定位（valueText × textScale）；</li>
 *   <li>图标（{@code key.icon}）：配置了独立锚点时脱离条单独定位（iconUV × iconScale）。</li>
 * </ul>
 * BAR 模式 = 卡片底 + 状态填充 + 原版图标 + 数值文本；ICON 模式 = 原版图标行。
 * 布局与测量与编辑器完全共用（solve + measureAll）。
 */
public final class CustomHudRenderer {

    private static final ResourceLocation ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");
    /** 条形卡片默认高度（像素）；可经布局编辑器 barHeight 调整（5..16） */
    private static final int BAR_H_DEFAULT = 9;

    private CustomHudRenderer() {}

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        render(graphics, partialTick,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    /** 指定屏幕尺寸渲染：设置页 HUD 预览用虚拟屏幕调用（所见即布局在真实屏幕的相对位置）；游戏内/编辑器走真实尺寸 */
    public static void render(GuiGraphics graphics, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        HudLayoutConfig layout = ConfigManager.getConfig().hudLayout;

        Map<String, int[]> sizes = measureAll(layout, player);
        Map<String, HudLayoutSolver.Box> boxes = HudLayoutSolver.solve(layout, sizes, screenW, screenH);

        for (Map.Entry<String, HudLayoutSolver.Box> entry : boxes.entrySet()) {
            String key = entry.getKey();
            HudLayoutSolver.Box box = entry.getValue();
            boolean isText = key.endsWith(".text");
            boolean isIcon = key.endsWith(".icon");
            String base = (isText || isIcon) ? key.substring(0, key.lastIndexOf('.')) : key;
            ComponentLayout c = layout.get(base);

            graphics.pose().pushPose();
            graphics.pose().translate(box.x(), box.y(), 0);
            if (isText) {
                // 分离文本元素：独立定位（缩放 = 组件缩放 × 文本缩放;模板优先于默认文本）
                float s = (float) (c.scale * c.textScale);
                graphics.pose().scale(s, s, 1f);
                String t = c.textFormat != null && !c.textFormat.isBlank()
                        ? formatText(c.textFormat, player) : valueText(base, player);
                if (t != null) graphics.drawString(mc.font, t, 0, 0, 0xFFFFFFFF, true);
            } else if (isIcon) {
                // 分离图标元素：独立定位（缩放 = 组件缩放 × 图标缩放）
                float s = (float) (c.scale * c.iconScale);
                graphics.pose().scale(s, s, 1f);
                int[] uv = iconUV(base, player);
                if (uv != null) GuiHelper.drawTexturedRect(ICONS, graphics, 0, 0, uv[0], uv[1], 9, 9);
            } else {
                float scale = (float) c.scale;
                graphics.pose().scale(scale, scale, 1f);
                // 类型分发（typeOf：显式 type > 键名 '#' 前段）——同一类型可有多实例（health#2）,
                // 并支持原子组件（纯文本/纯图标/自由文本）,全部可自由增删组合
                String type = HudLayoutConfig.typeOf(key, c);
                if (type.endsWith("_text")) {
                    // 纯文本组件：只渲染对应数值文本（模板优先）
                    String t = c.textFormat != null && !c.textFormat.isBlank()
                            ? formatText(c.textFormat, player) : valueText(type, player);
                    if (t != null) graphics.drawString(mc.font, t, 0, 0, 0xFFFFFFFF, true);
                } else if (type.endsWith("_icon")) {
                    // 纯图标组件：只渲染状态图标
                    int[] uv = iconUV(type, player);
                    if (uv != null) GuiHelper.drawTexturedRect(ICONS, graphics, 0, 0, uv[0], uv[1], 9, 9);
                } else if (type.equals("text")) {
                    // 自由文本组件：内容 = 模板串（可引用任意玩家数据变量,组件间互相调用）
                    String t = formatText(c.textFormat, player);
                    if (t != null && !t.isEmpty()) graphics.drawString(mc.font, t, 0, 0, 0xFFFFFFFF, true);
                } else switch (type) {
                    case HudLayoutConfig.HEALTH -> renderHealth(graphics, mc, player, c);
                    case HudLayoutConfig.FOOD -> renderFood(graphics, mc, player, c);
                    case HudLayoutConfig.AIR -> renderAir(graphics, mc, player, c);
                    case HudLayoutConfig.EXPERIENCE -> renderExperience(graphics, mc, player, c);
                    case HudLayoutConfig.ARMOR -> renderArmor(graphics, mc, player, c);
                    case HudLayoutConfig.MOUNT -> renderMount(graphics, mc, player, c);
                    case HudLayoutConfig.COMPAT -> renderCompat(graphics, player, c);
                    default -> { }
                }
            }
            graphics.pose().popPose();
        }
    }

    /**
     * 全体元素尺寸（渲染与编辑器共用）：组件条 + 分离文本/图标。
     * 分离元素仅在配置了独立锚点且当前有值（文本非空/图标可用）时参与定位。
     */
    public static Map<String, int[]> measureAll(HudLayoutConfig layout, Player player) {
        Map<String, int[]> sizes = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getInstance();
        for (String key : layout.components.keySet()) {
            ComponentLayout c = layout.get(key);
            String type = HudLayoutConfig.typeOf(key, c);
            if (type.endsWith("_icon")) {
                sizes.put(key, new int[]{9, 9}); // 纯图标组件
                continue;
            }
            if (type.endsWith("_text") || type.equals("text")) {
                // 纯文本/自由文本组件：尺寸 = 实际文本宽
                String t = type.equals("text") ? formatText(c.textFormat, player)
                        : (c.textFormat != null && !c.textFormat.isBlank()
                                ? formatText(c.textFormat, player) : valueText(type, player));
                sizes.put(key, new int[]{Math.max(8, t == null ? 8 : mc.font.width(t)), 10});
                continue;
            }
            sizes.put(key, HudLayoutSolver.measure(c));
            if (player == null || c.modeParsed() != HudLayoutConfig.ComponentMode.BAR) continue;
            String t = c.textFormat != null && !c.textFormat.isBlank()
                    ? formatText(c.textFormat, player) : valueText(key, player);
            if (c.showText && c.textAnchorParsed() != null && t != null) {
                sizes.put(key + ".text", new int[]{mc.font.width(t), 8});
            }
            if (c.showIcon && c.iconAnchorParsed() != null && iconUV(key, player) != null) {
                sizes.put(key + ".icon", new int[]{9, 9});
            }
        }
        return sizes;
    }

    /** 原子组件键 → 基础数据类型（health_text/health_icon → health;xp_text → experience） */
    private static String dataSource(String key) {
        String k = HudLayoutConfig.baseKeyOf(key);
        if (k.endsWith("_text")) k = k.substring(0, k.length() - 5);
        else if (k.endsWith("_icon")) k = k.substring(0, k.length() - 5);
        if (k.equals("xp")) k = HudLayoutConfig.EXPERIENCE;
        return k;
    }

    /** 组件对应当前数值文本（null = 当前不显示）；分离文本元素与条内文本共用同一来源。
     *  模板（c.textFormat）由调用方判断——本方法只提供各组件的默认文本,不触碰配置。 */
    public static String valueText(String key, Player p) {
        var cfg = ConfigManager.getConfig();
        boolean pv = com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active;
        String type = dataSource(key);
        return switch (type) {
            case HudLayoutConfig.HEALTH -> fmt(pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.health : p.getHealth())
                    + "/" + fmt(Math.max(1, pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.maxHealth : p.getMaxHealth()));
            case HudLayoutConfig.FOOD -> {
                int max = cfg.overlay.fullFoodLevelValue;
                if (max <= 0) max = 20;
                yield (pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.food : p.getFoodData().getFoodLevel()) + "/" + max;
            }
            case HudLayoutConfig.AIR -> {
                int air = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.air : p.getAirSupply();
                int maxAir = Math.max(1, pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.maxAir : p.getMaxAirSupply());
                yield air >= maxAir ? null : String.valueOf(air);
            }
            case HudLayoutConfig.EXPERIENCE -> {
                if (!pv && p.isPassenger()) yield null;
                float progress = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.xpProgress : p.experienceProgress;
                int level = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.xpLevel : p.experienceLevel;
                yield level > 0 ? ("Lv." + level) : String.valueOf(Math.round(progress * 100)) + "%";
            }
            case HudLayoutConfig.ARMOR -> {
                int armor = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.armor : p.getArmorValue();
                int max = cfg.overlay.fullArmorValue;
                if (max <= 0) max = 20;
                yield armor > 0 ? armor + "/" + max : null;
            }
            case HudLayoutConfig.MOUNT -> p.getVehicle() instanceof LivingEntity mount
                    ? fmt(mount.getHealth()) + "/" + fmt(Math.max(1, mount.getMaxHealth())) : null;
            default -> null; // compat 为多行动态行，不参与分离
        };
    }

    /** 文本模板变量替换（组件互相调用：任何文本组件可引用任意玩家数据）。
     *  变量：{health} {max_health} {absorption} {food} {food_max} {air} {air_max}
     *  {armor} {toughness} {level} {xp_percent} {mount_health} {mount_max} */
    public static String formatText(String template, Player p) {
        if (template == null || template.isBlank()) return "";
        boolean pv = com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active;
        float hp = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.health : p.getHealth();
        float maxHp = Math.max(1, pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.maxHealth : p.getMaxHealth());
        float abs = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.absorption : p.getAbsorptionAmount();
        int food = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.food : p.getFoodData().getFoodLevel();
        int foodMax = Math.max(1, ConfigManager.getConfig().overlay.fullFoodLevelValue > 0
                ? ConfigManager.getConfig().overlay.fullFoodLevelValue : 20);
        int air = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.air : p.getAirSupply();
        int maxAir = Math.max(1, pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.maxAir : p.getMaxAirSupply());
        int armor = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.armor : p.getArmorValue();
        int level = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.xpLevel : p.experienceLevel;
        float prog = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.xpProgress : p.experienceProgress;
        float mHp = 0, mMax = 0;
        if (!pv && p.getVehicle() instanceof LivingEntity mount) {
            mHp = mount.getHealth();
            mMax = Math.max(1, mount.getMaxHealth());
        }
        return template
                .replace("{health}", fmt(hp))
                .replace("{max_health}", fmt(maxHp))
                .replace("{absorption}", fmt(abs))
                .replace("{food}", String.valueOf(food))
                .replace("{food_max}", String.valueOf(foodMax))
                .replace("{air}", String.valueOf(air))
                .replace("{air_max}", String.valueOf(maxAir))
                .replace("{armor}", String.valueOf(armor))
                .replace("{toughness}", String.valueOf((int) p.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)))
                .replace("{level}", String.valueOf(level))
                .replace("{xp_percent}", String.valueOf(Math.round(prog * 100)))
                .replace("{mount_health}", fmt(mHp))
                .replace("{mount_max}", fmt(mMax));
    }

    /** 组件对应图标 UV（null = 无图标/当前不可用） */
    private static int[] iconUV(String key, Player p) {
        return switch (dataSource(key)) {
            case HudLayoutConfig.HEALTH -> new int[]{healthIconU(p), 0};
            case HudLayoutConfig.FOOD -> new int[]{52, 27};
            case HudLayoutConfig.AIR -> p.getAirSupply() >= Math.max(1, p.getMaxAirSupply())
                    ? null : new int[]{16, 18};
            case HudLayoutConfig.ARMOR -> p.getArmorValue() > 0 ? new int[]{34, 9} : null;
            case HudLayoutConfig.MOUNT -> p.getVehicle() instanceof LivingEntity ? new int[]{52, 0} : null;
            default -> null; // 经验条无原版图标
        };
    }

    // ============== 组件渲染（局部坐标原点 = 组件框左上角，未缩放单位） ==============

    private static void renderHealth(GuiGraphics g, Minecraft mc, Player p, ComponentLayout c) {
        var colors = ConfigManager.getConfig().colors;
        boolean pv = com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active;
        float health = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.health : p.getHealth();
        float max = Math.max(1, pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.maxHealth : p.getMaxHealth());
        float absorption = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.absorption : p.getAbsorptionAmount();

        int color = ColorHelper.parseColor(colors.healthNormal);
        if (p.hasEffect(MobEffects.POISON)) color = ColorHelper.parseColor(colors.healthPoison);
        else if (p.hasEffect(MobEffects.WITHER)) color = ColorHelper.parseColor(colors.healthWither);
        else if (p.getTicksFrozen() > 0) color = ColorHelper.parseColor(colors.healthFrozen);

        if (c.modeParsed() == HudLayoutConfig.ComponentMode.ICON) {
            int hearts = (int) Math.ceil(max / 2f);
            int full = (int) Math.floor(health / 2f);
            for (int i = 0; i < hearts; i++) {
                GuiHelper.drawTexturedRect(ICONS, g, i * 9, 0, 16, 0, 9, 9);
            }
            for (int i = 0; i < full && i < hearts; i++) {
                GuiHelper.drawTexturedRect(ICONS, g, i * 9, 0, 52, 0, 9, 9);
            }
            if (full < hearts && health - full * 2 > 0f) {
                GuiHelper.drawTexturedRect(ICONS, g, full * 9, 0, 61, 0, 9, 9);
            }
            if (absorption > 0) {
                int abs = (int) Math.ceil(absorption / 2f);
                for (int i = 0; i < abs; i++) {
                    GuiHelper.drawTexturedRect(ICONS, g, (hearts + i) * 9, 0, 160, 0, 9, 9);
                }
            }
            return;
        }

        int w = c.barWidth, h = barH(c);
        if (c.showBar) drawCard(g, 0, 0, w, h);
        // 动态效果（与长条管线同源 BarFx）：填充平滑（90ms 无延迟）+ 伤害残影（区域+420ms 渐隐）。
        // 此前自定义生命组件两样都没有——用户 HUD 为自定义样式时残影完全不可见。
        var dxFxCfg = ConfigManager.getConfig().dynamicFx;
        float rawRatio = Mth.clamp(health / max, 0f, 1f);
        var fxSt = com.z80z99.z80zhealthbar.mobdisplay.BarFx.tick(
                com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.fxKey(p.getId()), rawRatio,
                p.hurtTime > 0, System.currentTimeMillis());
        float dispR = dxFxCfg.enabled && dxFxCfg.smooth ? Mth.clamp(fxSt.display(), 0f, 1f) : rawRatio;
        // 吸收容量扩展：显示吸收段时条容量 = max(max, hp+abs)——满血吃金苹果吸收段仍可见
        //（此前按"剩余空白×吸收/max"缩放,满血时剩余为 0 吸收段归零不可见,且长度随血量下降缩短语义不准）
        float total = Math.max(max, health + absorption);
        float compress = absorption > 0 ? max / total : 1f; // BarFx 比例(相对max)→条比例折算
        // 吸收段动态效果（与生命同款 BarFx,独立状态键）：吃金苹果平滑增长、被消耗平滑消退
        // 并在消耗区域留白色渐隐残影——此前直接用原始值,与生命的动态不一致
        float absRaw = Mth.clamp(absorption / max, 0f, 1f);
        var absFx = com.z80z99.z80zhealthbar.mobdisplay.BarFx.tick(
                com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.fxKeyAbs(p.getId()),
                absRaw, false, System.currentTimeMillis());
        float absDisp = dxFxCfg.enabled && dxFxCfg.smooth ? Mth.clamp(absFx.display(), 0f, 1f) : absRaw;
        int innerW = HudBarPainter.innerWidth(w);
        int healthW = Math.round(dispR * compress * innerW);
        if (c.showBar) {
            // 伤害残影：[当前填充, 掉血前血量] 区域白色渐隐（dynamicFx.ghost 控制）
            if (dxFxCfg.enabled && dxFxCfg.ghost) {
                float ghostA = Mth.clamp(fxSt.ghostAlpha(), 0f, 1f);
                int preHitW = Math.min(innerW, Math.round(Mth.clamp(fxSt.preHit(), 0f, 1f) * compress * innerW));
                if (preHitW > healthW && ghostA > 0f) {
                    HudBarPainter.drawSegment(g, 0, 0, w, h, healthW, preHitW,
                            ColorHelper.modifyAlpha(ColorHelper.parseColor(dxFxCfg.ghostColor),
                                    (int) (ghostA * 255)));
                }
            }
            HudBarPainter.drawFillWidth(g, 0, 0, w, h, healthW, color);
            if (absorption > 0) {
                // 金段终点按 (平滑生命+平滑吸收)/total 一次取整——两段各自取整会累计丢 1~2px,
                // 满血+吸收（总量正好撑满条）时条尾出现细缝（实测"没有填满条"）
                int absEnd = Math.min(innerW, Math.max(healthW,
                        Math.round((dispR * max + absDisp * max) / total * innerW)));
                HudBarPainter.drawSegment(g, 0, 0, w, h, healthW, absEnd,
                        ColorHelper.parseColor(colors.absorption));
                // 吸收残影：[金段终点, 消耗前终点] 白色渐隐（锚定当前填充右侧,随填充一起收缩）
                if (dxFxCfg.enabled && dxFxCfg.ghost) {
                    float gA = Mth.clamp(absFx.ghostAlpha(), 0f, 1f);
                    float pre = Mth.clamp(absFx.preHit(), 0f, 1f);
                    if (gA > 0f && pre > absDisp) {
                        int ghostW = Math.round((pre - absDisp) * max / total * innerW);
                        int ghostEnd = Math.min(innerW, absEnd + ghostW);
                        if (ghostEnd > absEnd) {
                            HudBarPainter.drawSegment(g, 0, 0, w, h, absEnd, ghostEnd,
                                    ColorHelper.modifyAlpha(ColorHelper.parseColor(dxFxCfg.ghostColor),
                                            (int) (gA * 255)));
                        }
                    }
                }
            }
        }
        if (c.showIcon && c.iconAnchorParsed() == null) {
            drawIcon(g, iconX(c, w), 0, h, healthIconU(p), 0);
        }
        if (c.showText && c.textAnchorParsed() == null) {
            // 数字滚动（与实体血条同语义,dynamicFx.numRoll）：数字跟随平滑填充一起滚,
            // 否则掉血瞬间数字即时跳变而条还在缓动——两者不一致（用户实测）
            // 模板优先：textFormat 非空时按模板渲染（组件互相调用）
            float shownHealth = dxFxCfg.enabled && dxFxCfg.numRoll ? dispR * max : health;
            String txt = c.textFormat != null && !c.textFormat.isBlank()
                    ? formatText(c.textFormat, p) : fmt(shownHealth) + "/" + fmt(max);
            drawText(g, mc.font, txt, w, h, c);
        }
    }

    private static int healthIconU(Player p) {
        if (p.getAbsorptionAmount() > 0) return 160;
        if (p.hasEffect(MobEffects.POISON)) return 88;
        if (p.hasEffect(MobEffects.WITHER)) return 124;
        if (p.getTicksFrozen() > 0) return 178;
        return 52;
    }

    private static void renderFood(GuiGraphics g, Minecraft mc, Player p, ComponentLayout c) {
        var colors = ConfigManager.getConfig().colors;
        boolean pv = com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active;
        int food = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.food : p.getFoodData().getFoodLevel();
        int max = ConfigManager.getConfig().overlay.fullFoodLevelValue;
        if (max <= 0) max = 20; // 0 = 跟随原版上限

        if (c.modeParsed() == HudLayoutConfig.ComponentMode.ICON) {
            for (int i = 0; i < 10; i++) GuiHelper.drawTexturedRect(ICONS, g, i * 9, 0, 16, 0 + 27, 9, 9);
            int full = food / 2;
            for (int i = 0; i < full; i++) GuiHelper.drawTexturedRect(ICONS, g, i * 9, 0, 52, 27, 9, 9);
            if (food % 2 == 1 && full < 10) GuiHelper.drawTexturedRect(ICONS, g, full * 9, 0, 61, 27, 9, 9);
            return;
        }

        int w = c.barWidth, h = barH(c);
        if (c.showBar) {
            drawCard(g, 0, 0, w, h);
            int color = p.hasEffect(MobEffects.HUNGER)
                    ? ColorHelper.parseColor(colors.foodHunger)
                    : ColorHelper.parseColor(colors.foodNormal);
            HudBarPainter.drawRatioFill(g, 0, 0, w, h,
                    Mth.clamp(food / (float) max, 0, 1), color);
        }
        if (c.showIcon && c.iconAnchorParsed() == null) {
            drawIcon(g, iconX(c, w), 0, h, 52, 27);
        }
        if (c.showText && c.textAnchorParsed() == null) {
            String txt = c.textFormat != null && !c.textFormat.isBlank()
                    ? formatText(c.textFormat, p) : food + "/" + max;
            drawText(g, mc.font, txt, w, h, c);
        }
    }

    private static void renderAir(GuiGraphics g, Minecraft mc, Player p, ComponentLayout c) {
        var colors = ConfigManager.getConfig().colors;
        boolean pv = com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active;
        int air = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.air : p.getAirSupply();
        int maxAir = Math.max(1, pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.maxAir : p.getMaxAirSupply());
        if (air >= maxAir) return; // 仅水下显示

        if (c.modeParsed() == HudLayoutConfig.ComponentMode.ICON) {
            int bubbles = (int) Math.ceil(air / (float) maxAir * 10);
            for (int i = 0; i < bubbles; i++) GuiHelper.drawTexturedRect(ICONS, g, i * 9, 0, 16, 18, 9, 9);
            return;
        }

        int w = c.barWidth, h = barH(c);
        if (c.showBar) {
            drawCard(g, 0, 0, w, h);
            HudBarPainter.drawRatioFill(g, 0, 0, w, h,
                    Mth.clamp(air / (float) maxAir, 0, 1), ColorHelper.parseColor(colors.air));
        }
        if (c.showIcon && c.iconAnchorParsed() == null) {
            drawIcon(g, iconX(c, w), 0, h, 16, 18);
        }
        if (c.showText && c.textAnchorParsed() == null) {
            String txt = c.textFormat != null && !c.textFormat.isBlank()
                    ? formatText(c.textFormat, p) : String.valueOf(air);
            drawText(g, mc.font, txt, w, h, c);
        }
    }

    private static void renderExperience(GuiGraphics g, Minecraft mc, Player p, ComponentLayout c) {
        var colors = ConfigManager.getConfig().colors;
        boolean pv = com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active;
        if (!pv && p.isPassenger()) return;
        float progress = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.xpProgress : p.experienceProgress;
        int level = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.xpLevel : p.experienceLevel;

        // 图标形式：经验条无原版图标行——图标本体 = 原版风格等级数字（绿色带阴影,原版即把
        // 等级数字悬浮于经验条上方）。不受 showText 控制（那只管长条形式的条内文本）,
        // 否则关掉文本后图标形式会彻底空白（实测"不显示?"）
        if (c.modeParsed() == HudLayoutConfig.ComponentMode.ICON) {
            String text = level > 0 ? ("Lv." + level)
                    : String.valueOf(Math.round(progress * 100)) + "%";
            // 居中于组件测量框（ICON 模式 = {54,9},与 HudLayoutSolver.measure 同源,编辑器框内对齐）
            int[] ms = com.z80z99.z80zhealthbar.layout.HudLayoutSolver.measure(c);
            g.drawCenteredString(mc.font, text, ms[0] / 2, Math.max(0, (ms[1] - 8) / 2),
                    ColorHelper.parseColor(colors.experience));
            return;
        }

        int w = c.barWidth, h = barH(c);
        if (c.showBar) {
            drawCard(g, 0, 0, w, h);
            HudBarPainter.drawRatioFill(g, 0, 0, w, h,
                    Mth.clamp(progress, 0, 1), ColorHelper.parseColor(colors.experience));
        }
        if (c.showText && c.textAnchorParsed() == null) {
            String def = level > 0 ? ("Lv." + level) : String.valueOf(Math.round(progress * 100)) + "%";
            String text = c.textFormat != null && !c.textFormat.isBlank()
                    ? formatText(c.textFormat, p) : def;
            drawText(g, mc.font, text, w, h, c);
        }
    }

    private static void renderArmor(GuiGraphics g, Minecraft mc, Player p, ComponentLayout c) {
        var colors = ConfigManager.getConfig().colors;
        boolean pv = com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active;
        int armor = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.armor : p.getArmorValue();
        if (armor <= 0) return;

        if (c.modeParsed() == HudLayoutConfig.ComponentMode.ICON) {
            int icons = (int) Math.ceil(armor / 2f);
            for (int i = 0; i < icons && i < 10; i++) {
                GuiHelper.drawTexturedRect(ICONS, g, i * 9, 0, 34, 9, 9, 9);
            }
            return;
        }

        int max = ConfigManager.getConfig().overlay.fullArmorValue;
        if (max <= 0) max = 20; // 0 = 跟随原版上限
        int w = c.barWidth, h = barH(c);
        if (c.showBar) {
            drawCard(g, 0, 0, w, h);
            HudBarPainter.drawRatioFill(g, 0, 0, w, h,
                    Mth.clamp(armor / (float) max, 0, 1), ColorHelper.parseColor(colors.armor));
        }
        if (c.showIcon && c.iconAnchorParsed() == null) {
            drawIcon(g, iconX(c, w), 0, h, 34, 9);
        }
        if (c.showText && c.textAnchorParsed() == null) {
            String txt = c.textFormat != null && !c.textFormat.isBlank()
                    ? formatText(c.textFormat, p) : armor + "/" + max;
            drawText(g, mc.font, txt, w, h, c);
        }
    }

    private static void renderMount(GuiGraphics g, Minecraft mc, Player p, ComponentLayout c) {
        var colors = ConfigManager.getConfig().colors;
        if (!(p.getVehicle() instanceof LivingEntity mount)) return;
        float health = mount.getHealth();
        float max = Math.max(1, mount.getMaxHealth());

        int w = c.barWidth, h = barH(c);
        if (c.showBar) {
            drawCard(g, 0, 0, w, h);
            HudBarPainter.drawRatioFill(g, 0, 0, w, h,
                    Mth.clamp(health / max, 0, 1), ColorHelper.parseColor(colors.mountHealth));
        }
        if (c.showIcon && c.iconAnchorParsed() == null) {
            drawIcon(g, iconX(c, w), 0, h, 52, 0);
        }
        if (c.showText && c.textAnchorParsed() == null) {
            String txt = c.textFormat != null && !c.textFormat.isBlank()
                    ? formatText(c.textFormat, p) : fmt(health) + "/" + fmt(max);
            drawText(g, mc.font, txt, w, h, c);
        }
    }

    // ============== 绘制原语（与 ASTEORBAR 模式同风格：卡片底/填充/图标/文本） ==============

    /** 兼容状态组：thirst/stamina/exhaustion 等动态行（数据源 CompatAdapters） */
    private static void renderCompat(GuiGraphics g, Player p, ComponentLayout c) {
        var stats = com.z80z99.z80zhealthbar.compat.CompatAdapters.collect(p);
        if (stats.isEmpty()) return;
        int w = c.barWidth, h = barH(c);
        int y = 0;
        for (var stat : stats) {
            drawCard(g, 0, y, w, h);
            if (stat.max() != null && stat.max() > 0) {
                HudBarPainter.drawRatioFill(g, 0, y, w, h,
                        (float) Math.max(0d, Math.min(1d, stat.value() / stat.max())), stat.color());
            }
            if (c.showText) {
                String vs = stat.value() >= 100 ? String.valueOf(Math.round(stat.value()))
                        : String.format(java.util.Locale.ROOT, "%.1f", stat.value());
                String text = net.minecraft.network.chat.Component.translatable(stat.langKey()).getString()
                        + ": " + vs + (stat.max() == null ? "" : "/" + Math.round(stat.max()));
                drawTextIn(g, Minecraft.getInstance().font, text, w, h, y, c);
            }
            y += h + 2;
        }
    }

    private static int barH(ComponentLayout c) {
        return Math.max(5, Math.min(16, c.barHeight > 0 ? c.barHeight : BAR_H_DEFAULT));
    }

    /** 图标 x 坐标：LEFT = 条左侧 -11；RIGHT = 条右侧 +2（由编辑器 iconSide 控制） */
    private static int iconX(ComponentLayout c, int barW) {
        return c.iconSideParsed() == HudLayoutConfig.IconSide.RIGHT ? barW + 2 : -11;
    }

    private static void drawCard(GuiGraphics g, int x, int y, int w, int h) {
        HudBarPainter.drawCard(g, x, y, w, h);
    }

    private static void fill(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0) return;
        GuiHelper.drawSolidColor(g, x, y, x + w, y + h, color);
    }

    private static void drawIcon(GuiGraphics g, int x, int y, int barH, int u, int v) {
        int iconY = y + (barH - 9) / 2;
        GuiHelper.drawTexturedRect(ICONS, g, x, iconY, u, v, 9, 9);
    }

    /** 数值文本：按组件对齐（左/中/右）+ 文本偏移绘制 */
    private static void drawText(GuiGraphics g, Font font, String text, int barW, int barH, ComponentLayout c) {
        drawTextIn(g, font, text, barW, barH, 0, c);
    }

    private static void drawTextIn(GuiGraphics g, Font font, String text, int barW, int barH,
                                   int yBase, ComponentLayout c) {
        int tw = font.width(text);
        int tx = switch (c.textAlignParsed()) {
            case LEFT -> 4;
            case RIGHT -> barW - tw - 4;
            default -> (barW - tw) / 2;
        };
        tx += c.textOffsetX;
        int ty = yBase + (barH - 8) / 2 + c.textOffsetY;
        g.drawString(font, text, tx, ty, 0xFFFFFFFF, true);
    }

    /** 数值格式化：整数直接显示（20/20 而非 20.0/20.0），非整保留一位小数 */
    private static String fmt(float v) {
        float r = Math.round(v * 10) / 10f;
        if (r == (int) r) return String.valueOf((int) r);
        return String.valueOf(r);
    }
}
