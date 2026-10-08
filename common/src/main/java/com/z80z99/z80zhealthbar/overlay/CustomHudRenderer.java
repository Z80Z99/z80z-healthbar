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
            CUR.set(c); // 颜色覆盖上下文（drawCard/drawTextIn/各填充点经 fillOf/textOf/cardOf 读取）

            // 组件级透明度（opacity）× 动态 HUD（idleFadeSecs：数值无变化淡出/变化淡入）
            float compAlpha = (c.opacity / 100f) * idleFadeAlpha(key, base, player, c);
            if (compAlpha <= 0.01f) continue; // 完全透明整帧跳过
            boolean tinted = compAlpha < 1f;
            if (tinted) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, compAlpha);

            graphics.pose().pushPose();
            graphics.pose().translate(box.x(), box.y(), 0);
            // 旋转角度（绕组件中心;0 = 不旋转;rotationDegrees 收"度",此前误传弧度 → 180° 实际只转 3.14°）
            if (c.rotation != 0) {
                float cx = box.width() / 2f, cy = box.height() / 2f;
                graphics.pose().translate(cx, cy, 0);
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) c.rotation));
                graphics.pose().translate(-cx, -cy, 0);
            }
            try {
                if (isText) {
                    // 分离文本元素：独立定位（缩放 = 组件缩放 × 文本缩放;模板优先于默认文本）
                    float s = (float) (c.scale * c.textScale);
                    graphics.pose().scale(s, s, 1f);
                    String t = componentText(base, player);
                    if (t != null) graphics.drawString(mc.font, t, 0, 0, 0xFFFFFFFF, true);
                } else if (isIcon) {
                    // 分离图标元素：独立定位（缩放 = 组件缩放 × 图标缩放）
                    float s = (float) (c.scale * c.iconScale);
                    graphics.pose().scale(s, s, 1f);
                    int[] uv = iconUV(base, player);
                    if (uv != null) drawComponentIcon(graphics, c, 0, 0, 9, uv[0], uv[1]);
                } else {
                    float scale = (float) c.scale;
                    graphics.pose().scale(scale, scale, 1f);
                    // 类型分发（typeOf：显式 type > 键名 '#' 前段）——同一类型可有多实例（health#2）,
                    // 并支持原子组件（纯文本/纯图标/自由文本）,全部可自由增删组合
                    String type = HudLayoutConfig.typeOf(key, c);
                    if (type.endsWith("_text")) {
                        // 纯文本组件：只渲染对应数值文本（模板优先;帧内缓存）
                        String t = componentText(key, player);
                        if (t != null) graphics.drawString(mc.font, t, 0, 0, 0xFFFFFFFF, true);
                    } else if (type.endsWith("_icon")) {
                        // 纯图标组件：只渲染状态图标
                        int[] uv = iconUV(type, player);
                        if (uv != null) drawComponentIcon(graphics, c, 0, 0, 9, uv[0], uv[1]);
                    } else if (type.equals("text")) {
                        // 自由文本组件：内容 = 模板串（可引用任意玩家数据变量,组件间互相调用）
                        String t = formatText(c.textFormat, player);
                        if (t != null && !t.isEmpty()) graphics.drawString(mc.font, t, 0, 0, 0xFFFFFFFF, true);
                    } else if (type.equals("saturation_bar")) {
                        renderSaturationBar(graphics, mc, player, c);
                    } else if (type.startsWith("compat_")) {
                        // 兼容状态单行组件（compat_saturation/exhaustion/thirst/stamina）:
                        // 从兼容组拆出的独立行,可单独摆放/配置（实测"上下不能分开成两个组件"）
                        renderCompat(graphics, player, c, type.substring("compat_".length()));
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
            } finally {
                if (tinted) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                graphics.pose().popPose();
                CUR.remove();
            }
        }
    }

    /** 当前正在渲染的组件（颜色覆盖上下文;渲染单线程,渲染前 set/finally remove） */
    private static final ThreadLocal<ComponentLayout> CUR = new ThreadLocal<>();

    /** 组件自定义色覆盖：非空则替换 fallback 的 RGB——**alpha 保留 fallback 通道**
     *  （组件透明度/淡入淡出动画统一走 alpha,自定义色只管色相） */
    private static int overrideRGB(String custom, int fallback) {
        if (custom == null || custom.isBlank()) return fallback;
        int v = ColorHelper.parseColor(custom);
        if (v == 0) return fallback;
        return (v & 0x00FFFFFF) | (fallback & 0xFF000000);
    }

    /** 条填充色覆盖（当前组件 colorFill;非空时替代状态自动变色） */
    private static int fillOf(ComponentLayout c, int fallback) {
        return c == null ? fallback : overrideRGB(c.colorFill, fallback);
    }

    /** 副填充段色覆盖（组件 colorFill2:生命吸收段/食物饱和度金段——与主填充色独立可调） */
    private static int fill2Of(ComponentLayout c, int fallback) {
        return c == null ? fallback : overrideRGB(c.colorFill2, fallback);
    }

    /** 数值文本色覆盖（当前组件 colorText） */
    private static int textOf(ComponentLayout c, int fallback) {
        return c == null ? fallback : overrideRGB(c.colorText, fallback);
    }

    /** 动态 HUD 跟踪器（key → 上次数值/变化时间;数值=组件当前显示文本,空视为不变） */
    private static final Map<String, String> IDLE_LAST = new java.util.HashMap<>();
    private static final Map<String, Long> IDLE_CHANGE = new java.util.HashMap<>();

    /** 帧内文本缓存：同一帧（50ms 窗口）内同一组件的文本只算一次——
     *  measureAll / 渲染循环 / idleFadeAlpha 此前各算一遍（每帧 2-3 次重复,性能审查 §3） */
    private static final Map<String, String> FRAME_TEXT = new java.util.HashMap<>();
    private static long frameTextAt;

    private static String onceText(String cacheKey, java.util.function.Supplier<String> compute) {
        long now = System.currentTimeMillis();
        if (now - frameTextAt > 50) {
            FRAME_TEXT.clear();
            frameTextAt = now;
        }
        return FRAME_TEXT.computeIfAbsent(cacheKey, k -> compute.get());
    }

    /** 组件的显示文本（模板优先于默认值;带帧内缓存） */
    private static String componentText(String key, Player p) {
        ComponentLayout c = ConfigManager.getConfig().hudLayout.components.get(key);
        if (c == null) return valueText(key, p);
        return onceText(key, () -> c.textFormat != null && !c.textFormat.isBlank()
                ? formatText(c.textFormat, p) : valueText(key, p));
    }

    /** 动态 HUD 透明度系数（idleFadeSecs=0 恒 1）：数值变化重置计时,无变化 N 秒后线性淡出 */
    private static float idleFadeAlpha(String key, String base, Player player, ComponentLayout c) {
        if (c.idleFadeSecs <= 0) return 1f;
        String cur = componentText(base, player);
        // 无可比较数值的组件（compat 多行动态行等）不参与淡出——否则一律被判为"无变化",
        // 计时永不重置,组件恒停在 5% 轮廓（实测 compat 组件永远半透明）
        if (cur == null) return 1f;
        String last = IDLE_LAST.put(key, cur);
        long now = System.currentTimeMillis();
        if (!cur.equals(last)) {
            IDLE_CHANGE.put(key, now);
        }
        long changedAt = IDLE_CHANGE.getOrDefault(key, now);
        long hold = c.idleFadeSecs * 1000L;
        long dt = now - changedAt;
        if (dt <= hold) return 1f;
        float out = 1f - Math.min(1f, (dt - hold) / 600f);
        return Math.max(0.05f, out); // 不完全归零:保留一点轮廓便于找到/再编辑
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
                // 纯文本/自由文本组件：尺寸 = 实际文本宽（帧内缓存复用）
                String t = type.equals("text") ? formatText(c.textFormat, player) : componentText(key, player);
                sizes.put(key, new int[]{Math.max(8, t == null ? 8 : mc.font.width(t)), 10});
                continue;
            }
            if (type.equals(HudLayoutConfig.COMPAT) || type.startsWith("compat_") || type.equals("saturation_bar")) {
                // 兼容行/饱和度条只有卡片+文本形态（ICON 档无对应渲染）→ 一律按条形度量;
                // 兼容组还要按行数算高度,否则求解器只留一行:第 2 行起画到框外/屏幕外并压住下方组件
                int rows = 1;
                if (type.equals(HudLayoutConfig.COMPAT) && player != null) {
                    rows = Math.max(1, com.z80z99.z80zhealthbar.compat.CompatAdapters.collect(player).size());
                }
                sizes.put(key, compatGroupSize(c, rows));
            } else {
                sizes.put(key, HudLayoutSolver.measure(c));
            }
            if (player == null || c.modeParsed() != HudLayoutConfig.ComponentMode.BAR) continue;
            String t = componentText(key, player);
            if (c.showText && c.textAnchorParsed() != null && t != null) {
                sizes.put(key + ".text", new int[]{mc.font.width(t), 8});
            }
            if (c.showIcon && c.iconAnchorParsed() != null && iconUV(key, player) != null) {
                sizes.put(key + ".icon", new int[]{9, 9});
            }
        }
        return sizes;
    }

    /** 兼容组/饱和度条度量：行高与单行条形度量同源,多行时加上 spacing 行距（与 renderCompat 绘制一致） */
    private static int[] compatGroupSize(ComponentLayout c, int rows) {
        int[] bar = HudLayoutSolver.measureBar(c);
        if (rows <= 1) return bar;
        int h = bar[1] - 2; // 单行度量含 2px 余量,取回纯行高
        int gap = Math.max(0, c.spacing);
        return new int[]{bar[0], rows * h + (rows - 1) * gap + 2};
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
            // 信息类数据源（可转化组件扩展）
            case "saturation" -> {
                // 预览读 mock（与饱食度条金段同源）——此前预览硬编码 12.5,与同屏金段（5→0）矛盾
                float sat = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.saturation
                        : p.getFoodData().getSaturationLevel();
                yield sat > 0 ? fmt(sat) : null;
            }
            case "coords" -> {
                var pos = p.blockPosition();
                yield "X" + pos.getX() + " Y" + pos.getY() + " Z" + pos.getZ();
            }
            case "fps" -> String.valueOf(Minecraft.getInstance().getFps());
            case "biome" -> {
                var level = p.level();
                var pos = p.blockPosition();
                var biomeKey = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                        .getKey(level.getBiome(pos).value());
                String path = biomeKey.getPath();
                // 群系名美化:plains → Plains（首字母大写,下划线转空格）
                String[] parts = path.split("_");
                StringBuilder sb = new StringBuilder();
                for (String s : parts) {
                    if (!s.isEmpty()) {
                        if (!sb.isEmpty()) sb.append(' ');
                        sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
                    }
                }
                yield sb.toString();
            }
            case "time" -> {
                long dayTicks = pv ? 6000L : (p.level().getDayTime() % 24000L);
                int hours = (int) ((dayTicks / 1000L + 6) % 24);
                int minutes = (int) (dayTicks % 1000L * 60L / 1000L);
                yield String.format("%02d:%02d", hours, minutes);
            }
            default -> null; // compat 为多行动态行，不参与分离
        };
    }

    /** 文本模板变量替换（组件互相调用：任何文本组件可引用任意玩家数据）。
     *  变量：{health} {max_health} {absorption} {food} {food_max} {air} {air_max}
     *  {armor} {toughness} {level} {xp_percent} {mount_health} {mount_max} */
    public static String formatText(String template, Player p) {
        if (template == null || template.isBlank()) return "";
        // 早退：无 '{' 的模板（纯文字）不做 18 连全串扫描与全量变量取值（性能审查 §4）
        if (template.indexOf('{') < 0) return template;
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
                .replace("{mount_max}", fmt(mMax))
                // 信息类变量（可转化组件扩展）
                .replace("{saturation}", fmt(pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.saturation
                        : p.getFoodData().getSaturationLevel()))
                .replace("{exhaustion}", fmt(pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.exhaustion
                        : p.getFoodData().getExhaustionLevel()))
                .replace("{x}", String.valueOf(p.blockPosition().getX()))
                .replace("{y}", String.valueOf(p.blockPosition().getY()))
                .replace("{z}", String.valueOf(p.blockPosition().getZ()))
                .replace("{fps}", String.valueOf(Minecraft.getInstance().getFps()))
                .replace("{xp_level}", String.valueOf(level));
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
        color = fillOf(c, color); // 组件自定义填充色覆盖（非空时替代状态自动变色）

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
        // 第二层动态效果：低血脉冲/治疗泛光修饰填充色;受击抖动 y 偏移（与残影/填充同帧生效）
        long nowMs = System.currentTimeMillis();
        boolean lowHp = rawRatio <= ConfigManager.getConfig().overlay.lowHealthRate;
        int dynColor = color;
        if (dxFxCfg.enabled) {
            if (dxFxCfg.lowHpPulse && lowHp) {
                dynColor = ColorHelper.lerp(dynColor, 0xFFFFFFFF,
                        HudFx.pulse(nowMs) * 0.45f);
            }
            if (dxFxCfg.healGlow) {
                dynColor = ColorHelper.lerp(dynColor, 0xFF50E080, fxSt.heal() * 0.45f);
            }
        }
        int hitShift = dxFxCfg.enabled && dxFxCfg.hitShake
                ? HudFx.shakeByMode(dxFxCfg.shakeMode, nowMs, fxSt.flash(), fxSt.lastDamage()) : 0;
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
                                    (int) (ghostA * 255)), hitShift);
                }
            }
            HudBarPainter.drawFillWidth(g, 0, 0, w, h, healthW, dynColor, hitShift);
            drawSheen(g, w, h, healthW);
            if (absorption > 0) {
                // 金段终点按 (平滑生命+平滑吸收)/total 一次取整——两段各自取整会累计丢 1~2px,
                // 满血+吸收（总量正好撑满条）时条尾出现细缝（实测"没有填满条"）
                int absEnd = Math.min(innerW, Math.max(healthW,
                        Math.round((dispR * max + absDisp * max) / total * innerW)));
                HudBarPainter.drawSegment(g, 0, 0, w, h, healthW, absEnd,
                        fill2Of(c, ColorHelper.parseColor(colors.absorption)));
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
            drawComponentIcon(g, c, iconX(c, w), 0, h, healthIconU(p), 0);
        }
        if (c.showText && c.textAnchorParsed() == null) {
            // 数字滚动（与实体血条同语义,dynamicFx.numRoll）：数字跟随平滑填充一起滚,
            // 否则掉血瞬间数字即时跳变而条还在缓动——两者不一致（用户实测）
            // 模板优先：textFormat 非空时按模板渲染（组件互相调用）
            float shownHealth = dxFxCfg.enabled && dxFxCfg.numRoll ? dispR * max : health;
            String txt = c.textFormat != null && !c.textFormat.isBlank()
                    ? formatText(c.textFormat, p) : fmt(shownHealth) + "/" + fmt(max);
            // 数字受伤红/治疗绿（dynamicFx.numTint,与实体条/长条管线同语义）
            int txtColor = 0xFFFFFFFF;
            if (dxFxCfg.enabled && dxFxCfg.numTint) {
                float[] tint = HudFx.textTint(fxSt.flash(), fxSt.heal());
                if (tint[0] > 0f) txtColor = ColorHelper.lerp(txtColor, 0xFFFF5050, tint[0]);
                if (tint[1] > 0f) txtColor = ColorHelper.lerp(txtColor, 0xFF50E080, tint[1]);
            }
            drawText(g, mc.font, txt, w, h, c, txtColor);
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
        float sat = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.saturation
                : p.getFoodData().getSaturationLevel();
        float exhaustion = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.exhaustion
                : p.getFoodData().getExhaustionLevel();

        if (c.modeParsed() == HudLayoutConfig.ComponentMode.ICON) {
            for (int i = 0; i < 10; i++) GuiHelper.drawTexturedRect(ICONS, g, i * 9, 0, 16, 0 + 27, 9, 9);
            int full = food / 2;
            for (int i = 0; i < full; i++) GuiHelper.drawTexturedRect(ICONS, g, i * 9, 0, 52, 27, 9, 9);
            if (food % 2 == 1 && full < 10) GuiHelper.drawTexturedRect(ICONS, g, full * 9, 0, 61, 27, 9, 9);
            return;
        }

        // 动态效果（与生命条同源 BarFx）：饱食度平滑 + 饱和度平滑（像血条吸收段被消耗）;
        // 消耗值 = 临近扣除预告（>3 时条尾呼吸微光,扣除瞬间淡出,饱食/饱和度平滑回落）
        var dxCfg = ConfigManager.getConfig().dynamicFx;
        long nowMs = System.currentTimeMillis();
        float foodRaw = Mth.clamp(food / (float) max, 0f, 1f);
        var foodFx = com.z80z99.z80zhealthbar.mobdisplay.BarFx.tick(
                com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.fxKeyFood(p.getId()),
                foodRaw, false, nowMs);
        float foodDisp = dxCfg.enabled && dxCfg.smooth
                ? Mth.clamp(foodFx.display(), 0f, 1f) : foodRaw;
        // 饱和度比例基准与其它路径统一（长条/饱和度条/saturation_bar 均用 fullSaturationValue）
        float satMax = (float) Math.max(1, ConfigManager.getConfig().overlay.fullSaturationValue > 0
                ? ConfigManager.getConfig().overlay.fullSaturationValue : 20);
        float satRaw = Mth.clamp(sat / satMax, 0f, 1f);
        var satFx = com.z80z99.z80zhealthbar.mobdisplay.BarFx.tick(
                com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.fxKeySat(p.getId()),
                satRaw, false, nowMs);
        float satDisp = dxCfg.enabled && dxCfg.smooth
                ? Mth.clamp(satFx.display(), 0f, 1f) : satRaw;

        int w = c.barWidth, h = barH(c);
        if (c.showBar) {
            drawCard(g, 0, 0, w, h);
            int color = p.hasEffect(MobEffects.HUNGER)
                    ? ColorHelper.parseColor(colors.foodHunger)
                    : ColorHelper.parseColor(colors.foodNormal);
    color = fillOf(c, color); // 组件自定义填充色
            int innerW = HudBarPainter.innerWidth(w);
            // 饱和度显示方式（组件级可选;0=覆盖 1=右侧追加(吸收式) 2=顶部细条 3=底部细条 4=关闭）
            int satMode = Math.max(0, Math.min(4, c.saturationMode));
            boolean satVisible = sat > 0.01f && satMode != 4;
            int goldColor = fill2Of(c, ColorHelper.parseColor(colors.saturation));
            int foodW = Math.round(foodDisp * innerW);
            if (satVisible && satMode == 1) {
                // 右侧追加（与血条吸收段同几何）：容量扩展 total = max(上限, 饱食+饱和),
                // 棕段=饱食/total、金段接其后——"把缓冲层放在后面"的可选样式
                float total = Math.max(max, food + sat);
                foodW = Math.round(Mth.clamp(foodDisp * max / total, 0f, 1f) * innerW);
                int satEnd = Math.round(Mth.clamp((foodDisp * max + satDisp * satMax) / total, 0f, 1f) * innerW);
                HudBarPainter.drawFillWidth(g, 0, 0, w, h, foodW, color);
                HudBarPainter.drawSegment(g, 0, 0, w, h, foodW, Math.min(innerW, satEnd), goldColor);
            } else {
                HudBarPainter.drawFillWidth(g, 0, 0, w, h, foodW, color);
                if (satVisible) {
                    int satW = Math.round(satDisp * innerW);
                    if (satMode == 2 || satMode == 3) {
                        // 顶部/底部细条：内嵌细带（不遮主填充,饱和度作为"薄缓冲层"呈现）
                        int ih = HudBarPainter.innerHeight(h);
                        int stripH = Math.max(1, ih / 3);
                        int y0 = HudBarPainter.fillTop(0, h)
                                + (satMode == 3 ? ih - stripH : 0);
                        if (satW > 0) {
                            g.fill(HudBarPainter.INSET, y0,
                                    HudBarPainter.INSET + Math.min(innerW, satW), y0 + stripH, goldColor);
                        }
                    } else {
                        // 覆盖（AppleSkin 式,默认）：从左侧覆盖在饱食度填充之上
                        if (satW > 0) {
                            HudBarPainter.drawSegment(g, 0, 0, w, h, 0, Math.min(innerW, satW), goldColor);
                        }
                    }
                }
            }
            // 消耗值预告：exhaustion→4 临近一次扣除（扣饱和度/饱食度）;>3 时条尾微光呼吸,
            // 扣除瞬间（消耗值回落）微光消失——把"马上要掉饱和度/饱食度"可视化
            if (dxCfg.enabled && exhaustion > 3f) {
                float near = Mth.clamp((exhaustion - 3f) / 1f, 0f, 1f);
                float glow = (0.4f + 0.6f * HudFx.pulse(nowMs)) * near;
                int glowA = (int) (glow * 90) << 24 | 0x00FFD080;
                int tailW = Math.max(3, innerW / 12);
                HudBarPainter.drawSegment(g, 0, 0, w, h, innerW - tailW, innerW, glowA);
            }
        }
        if (c.showIcon && c.iconAnchorParsed() == null) {
            drawComponentIcon(g, c, iconX(c, w), 0, h, 52, 27);
        }
        if (c.showText && c.textAnchorParsed() == null) {
            // 模板变量：{food} 饱食度 {saturation} 饱和度 {exhaustion} 消耗值（默认 饱食/上限）
            String txt;
            if (c.textFormat != null && !c.textFormat.isBlank()) {
                txt = formatText(c.textFormat, p);
            } else {
                txt = food + "/" + max;
            }
            drawText(g, mc.font, txt, w, h, c);
        }
    }

    /** 饱和度条（从兼容状态行独立成组件）：卡片 + 金色比例填充 + 可选数值文本 */
    private static void renderSaturationBar(GuiGraphics g, Minecraft mc, Player p, ComponentLayout c) {
        var colors = ConfigManager.getConfig().colors;
        boolean pv = com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active;
        // 预览与饱食度条金段同源（此前预览硬编码 12.5：同一帧两个饱和度显示互相矛盾）
        float sat = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.saturation
                : p.getFoodData().getSaturationLevel();
        if (sat <= 0) return; // 无饱和度不渲染（与原版语义一致）

        float max = (float) Math.max(1, ConfigManager.getConfig().overlay.fullSaturationValue > 0
                ? ConfigManager.getConfig().overlay.fullSaturationValue : 20);
        int w = c.barWidth, h = barH(c);
        if (c.showBar) {
            drawCard(g, 0, 0, w, h);
            HudBarPainter.drawRatioFill(g, 0, 0, w, h,
                    Mth.clamp(sat / max, 0f, 1f), fill2Of(c, ColorHelper.parseColor(colors.saturation)));
        }
        if (c.showText && c.textAnchorParsed() == null) {
            String txt = c.textFormat != null && !c.textFormat.isBlank()
                    ? formatText(c.textFormat, p) : fmt(sat);
            drawText(g, mc.font, txt, w, h, c);
        }
    }

    private static void renderAir(GuiGraphics g, Minecraft mc, Player p, ComponentLayout c) {
        var colors = ConfigManager.getConfig().colors;
        boolean pv = com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active;
        int air = pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.air : p.getAirSupply();
        int maxAir = Math.max(1, pv ? com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.maxAir : p.getMaxAirSupply());
        // 出入水淡入淡出（与长条管线共用 AirFade）：可见 = 水下且氧气未满;淡出期继续渲染
        boolean wants = (pv || p.isUnderWater()) && air < maxAir;
        float fade = AirFade.alpha(wants, System.currentTimeMillis());
        if (!wants && AirFade.fullyHidden(fade)) return;

        if (fade < 1f) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, fade);
        try {
            renderAirInner(g, mc, p, c, colors, air, maxAir);
        } finally {
            if (fade < 1f) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private static void renderAirInner(GuiGraphics g, Minecraft mc, Player p, ComponentLayout c,
                                       com.z80z99.z80zhealthbar.config.configs.ColorConfig colors,
                                       int air, int maxAir) {
        if (c.modeParsed() == HudLayoutConfig.ComponentMode.ICON) {
            int bubbles = (int) Math.ceil(air / (float) maxAir * 10);
            for (int i = 0; i < bubbles; i++) GuiHelper.drawTexturedRect(ICONS, g, i * 9, 0, 16, 18, 9, 9);
            return;
        }

        int w = c.barWidth, h = barH(c);
        if (c.showBar) {
            drawCard(g, 0, 0, w, h);
            HudBarPainter.drawRatioFill(g, 0, 0, w, h,
                    Mth.clamp(air / (float) maxAir, 0, 1), fillOf(c, ColorHelper.parseColor(colors.air)));
        }
        if (c.showIcon && c.iconAnchorParsed() == null) {
            drawComponentIcon(g, c, iconX(c, w), 0, h, 16, 18);
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
                    fillOf(c, ColorHelper.parseColor(colors.experience)));
            return;
        }

        int w = c.barWidth, h = barH(c);
        if (c.showBar) {
            drawCard(g, 0, 0, w, h);
            HudBarPainter.drawRatioFill(g, 0, 0, w, h,
                    Mth.clamp(progress, 0, 1), fillOf(c, ColorHelper.parseColor(colors.experience)));
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
                    Mth.clamp(armor / (float) max, 0, 1), fillOf(c, ColorHelper.parseColor(colors.armor)));
        }
        if (c.showIcon && c.iconAnchorParsed() == null) {
            drawComponentIcon(g, c, iconX(c, w), 0, h, 34, 9);
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
                    Mth.clamp(health / max, 0, 1), fillOf(c, ColorHelper.parseColor(colors.mountHealth)));
        }
        if (c.showIcon && c.iconAnchorParsed() == null) {
            drawComponentIcon(g, c, iconX(c, w), 0, h, 52, 0);
        }
        if (c.showText && c.textAnchorParsed() == null) {
            String txt = c.textFormat != null && !c.textFormat.isBlank()
                    ? formatText(c.textFormat, p) : fmt(health) + "/" + fmt(max);
            drawText(g, mc.font, txt, w, h, c);
        }
    }

    // ============== 绘制原语（与 ASTEORBAR 模式同风格：卡片底/填充/图标/文本） ==============

    /** 兼容状态组：thirst/stamina/exhaustion 等动态行（数据源 CompatAdapters）。
     *  组件配置全量接入：文本模板（{name}{value}{max}）/对齐/偏移/缩放/行间距（spacing）。
     *  statFilter 非空 = 单行模式（独立组件 compat_xxx 只渲染该行;如 compat_saturation） */
    private static void renderCompat(GuiGraphics g, Player p, ComponentLayout c) {
        renderCompat(g, p, c, null);
    }

    private static void renderCompat(GuiGraphics g, Player p, ComponentLayout c, String statFilter) {
        var stats = com.z80z99.z80zhealthbar.compat.CompatAdapters.collect(p);
        if (stats.isEmpty()) return;
        if (statFilter != null) {
            stats.removeIf(s -> !s.key().endsWith("." + statFilter));
        }
        if (stats.isEmpty()) return;
        int w = c.barWidth, h = barH(c);
        int rowGap = Math.max(0, c.spacing);
        int y = 0;
        for (var stat : stats) {
            // showBar 关闭 = 只留文本部件（与其它组件一致的"自由组合"语义）
            if (c.showBar) {
                drawCard(g, 0, y, w, h);
                if (stat.max() != null && stat.max() > 0) {
                    HudBarPainter.drawRatioFill(g, 0, y, w, h,
                            (float) Math.max(0d, Math.min(1d, stat.value() / stat.max())), fillOf(c, stat.color()));
                }
            }
            if (c.showText) {
                String vs = stat.value() >= 100 ? String.valueOf(Math.round(stat.value()))
                        : String.format(java.util.Locale.ROOT, "%.1f", stat.value());
                String text;
                if (c.textFormat != null && !c.textFormat.isBlank()) {
                    // 模板（与长条管线同语义）:{name} 状态名 {value} 当前值 {max} 上限
                    text = c.textFormat
                            .replace("{name}", net.minecraft.network.chat.Component.translatable(
                                    stat.langKey()).getString())
                            .replace("{value}", vs)
                            .replace("{max}", stat.max() == null ? "—" : String.valueOf(Math.round(stat.max())));
                } else {
                    text = net.minecraft.network.chat.Component.translatable(stat.langKey()).getString()
                            + ": " + vs + (stat.max() == null ? "" : "/" + Math.round(stat.max()));
                }
                drawTextScaled(g, Minecraft.getInstance().font, text, w, h, y, c);
            }
            y += h + rowGap;
        }
    }

    /** drawTextIn + 文本缩放（compat 行文本用;缩放以文本锚点为原点） */
    private static void drawTextScaled(GuiGraphics g, Font font, String text, int barW, int barH,
                                       int yBase, ComponentLayout c) {
        float s = (float) Math.max(0.25, Math.min(3.0, c.textScale));
        if (Math.abs(s - 1f) < 0.01f) {
            drawTextIn(g, font, text, barW, barH, yBase, c);
            return;
        }
        int tw = font.width(text);
        int tws = Math.round(tw * s); // 对齐基准用缩放后宽度——否则放大时右对齐/居中会溢出卡片
        int tx = switch (c.textAlignParsed()) {
            case LEFT -> 4;
            case RIGHT -> barW - tws - 4;
            default -> (barW - tws) / 2;
        };
        tx += c.textOffsetX;
        int ty = yBase + (barH - 8) / 2 + c.textOffsetY;
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private static int barH(ComponentLayout c) {
        return Math.max(5, Math.min(16, c.barHeight > 0 ? c.barHeight : BAR_H_DEFAULT));
    }

    /** 图标 x 坐标：LEFT = 条左侧 -11；RIGHT = 条右侧 +2（由编辑器 iconSide 控制） */
    private static int iconX(ComponentLayout c, int barW) {
        return c.iconSideParsed() == HudLayoutConfig.IconSide.RIGHT ? barW + 2 : -11;
    }

    private static void drawCard(GuiGraphics g, int x, int y, int w, int h) {
        ComponentLayout c = CUR.get();
        if (c != null && c.colorCard != null && !c.colorCard.isBlank()) {
            int v = ColorHelper.parseColor(c.colorCard);
            if (v != 0) {
                // 自定义卡片底：纯色平铺（保留原版卡片的 alpha 感）+ 顶 1px 微高光
                fill(g, x, y, w, h, v);
                fill(g, x, y, w, 1, 0x30FFFFFF);
                return;
            }
        }
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

    /** 按组件配置画图标：iconTexture 非空用外部贴图（config/z80zhealthbar/icons/,整图缩放 9×9）,
     *  否则原版 icons.png UV。外部缺失时回退原版。 */
    private static void drawComponentIcon(GuiGraphics g, ComponentLayout c, int x, int y, int barH, int u, int v) {
        int iconY = y + (barH - 9) / 2;
        var tex = c.iconTexture != null && !c.iconTexture.isBlank()
                ? IconTextures.get(c.iconTexture) : null;
        if (tex != null) {
            g.blit(tex, x, iconY, 0, 0, 9, 9, 9, 9);
        } else {
            GuiHelper.drawTexturedRect(ICONS, g, x, iconY, u, v, 9, 9);
        }
    }

    /** 数值文本：按组件对齐（左/中/右）+ 文本偏移绘制 */
    private static void drawText(GuiGraphics g, Font font, String text, int barW, int barH,
                                 ComponentLayout c, int color) {
        drawTextIn(g, font, text, barW, barH, 0, c, color);
    }

    @SuppressWarnings("unused")
    private static void drawText(GuiGraphics g, Font font, String text, int barW, int barH, ComponentLayout c) {
        drawTextIn(g, font, text, barW, barH, 0, c);
    }

    private static void drawTextIn(GuiGraphics g, Font font, String text, int barW, int barH,
                                   int yBase, ComponentLayout c) {
        drawTextIn(g, font, text, barW, barH, yBase, c, textOf(c, 0xFFFFFFFF));
    }

    /** 文本绘制（带颜色——受伤红/治疗绿等数字动效用） */
    private static void drawTextIn(GuiGraphics g, Font font, String text, int barW, int barH,
                                   int yBase, ComponentLayout c, int color) {
        int tw = font.width(text);
        int tx = switch (c.textAlignParsed()) {
            case LEFT -> 4;
            case RIGHT -> barW - tw - 4;
            default -> (barW - tw) / 2;
        };
        tx += c.textOffsetX;
        int ty = yBase + (barH - 8) / 2 + c.textOffsetY;
        g.drawString(font, text, tx, ty, color, true);
    }

    /** 扫光流动（局部坐标版：组件原点即条左上） */
    private static void drawSheen(GuiGraphics g, int w, int h, int fillW) {
        var dxCfg = ConfigManager.getConfig().dynamicFx;
        if (!dxCfg.enabled || !dxCfg.sheen || fillW <= 0) return;
        int innerW = HudBarPainter.innerWidth(w);
        double phase = HudFx.advanceSheen(-2, // 自定义管线血条固定编号
                fillW, System.currentTimeMillis(), false);
        int[] band = HudFx.sheenBandPhase(innerW, fillW, phase);
        if (band == null) return;
        int x0 = HudBarPainter.INSET + band[0];
        int x1 = HudBarPainter.INSET + band[1];
        int edge = HudFx.sheenEdgeW(innerW);
        int y0 = HudBarPainter.fillTop(0, h);
        int ih = HudBarPainter.innerHeight(h);
        if (ih <= 0) return;
        g.fill(x0 + edge, y0, x1, y0 + ih, 0x10FFFFFF);
        g.fill(x0, y0, Math.min(x0 + edge, x1), y0 + ih, 0x20FFFFFF);
    }

    /** 数值格式化：整数直接显示（20/20 而非 20.0/20.0），非整保留一位小数 */
    private static String fmt(float v) {
        float r = Math.round(v * 10) / 10f;
        if (r == (int) r) return String.valueOf((int) r);
        return String.valueOf(r);
    }
}
