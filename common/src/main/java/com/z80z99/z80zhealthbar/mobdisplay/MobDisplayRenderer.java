package com.z80z99.z80zhealthbar.mobdisplay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.status.EntityStatusSnapshot;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 实体显示主调度器。
 *
 * <ul>
 *   <li>每实体每帧只采集一次 {@link EntityStatusSnapshot}，三样式共用（数据/渲染解耦）。</li>
 *   <li>样式互斥（任务书 3.2）：OFF / MOBHEALTHBAR / MOBPLAQUES / ASTEORBAR 四选一，
 *       默认仅绘制所选样式；护甲/韧性/氧气经 EntityAddonsConfig 主动开启后作附加行叠加。</li>
 *   <li>淡入/死亡淡出动画（DisplayAnimation）。</li>
 *   <li>同屏数量上限（maxConcurrentDisplays）按当帧先到先得近似。</li>
 * </ul>
 */
public final class MobDisplayRenderer {

    private static final Map<Integer, Long> FIRST_SEEN = new HashMap<>();
    private static final Map<Integer, Integer> FRAME_RENDERED = new HashMap<>();
    private static long lastFrameTime = -1;

    static {
        MobDisplayRegistry.register(new HealthDisplayRenderer());
        MobDisplayRegistry.register(new ArmorDisplayRenderer());
        MobDisplayRegistry.register(new AirDisplayRenderer());
        MobDisplayRegistry.register(new ToughnessDisplayRenderer());
    }

    private MobDisplayRenderer() {}

    public static void render(LivingEntity entity, PoseStack poseStack,
                              MultiBufferSource buffer, float partialTick, int packedLight) {
        var cfg = ConfigManager.getConfig();
        EntityHealthStyle style = cfg.entityStyleParsed();
        boolean barEnabled = style != EntityHealthStyle.OFF && cfg.barStyle.enableHealthBar;
        boolean popupEnabled = cfg.damagePopup.enabled;
        if (!barEnabled && !popupEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null) return;

        double distSqr = entity.distanceToSqr(player);
        if (barEnabled && !MobVisibilityChecker.shouldRender(entity, distSqr)) barEnabled = false;
        if (!barEnabled && !popupEnabled) return;

        long gameTime = mc.level.getGameTime();
        if (gameTime != lastFrameTime) {
            purgeStale(gameTime);
            lastFrameTime = gameTime;
        }
        if (barEnabled) {
            // 同屏数量上限仅约束血条（近似：当帧先渲染的优先）
            int maxConcurrent = cfg.visibility.maxConcurrentDisplays;
            if (maxConcurrent > 0 && !FRAME_RENDERED.containsKey(entity.getId())
                    && FRAME_RENDERED.size() >= maxConcurrent) {
                barEnabled = false;
            }
        }

        EntityStatusSnapshot snap = EntityStatusSnapshot.capture(entity, distSqr);

        // 插值位置:与实体模型渲染同源(Mth.lerp(partialTick, xOld, x))。若用原始 getX()
        // (tick 量化位置,20Hz),血条会在 60+fps 的平滑世界中以 20Hz 阶梯跳动——表现为
        // "没固定在头上"与卡顿感,这是移动/转身时血条帧率观感差的直接原因
        double rx = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double ry = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double rz = Mth.lerp(partialTick, entity.zOld, entity.getZ());

        // 弹字数据源：快照喂给管理器（估算路径 + 锚点缓存 + 去重基线，同样使用插值位置）
        if (popupEnabled) {
            DamagePopupManager.onSnapshot(snap, rx, ry, rz);
        }
        // 遮挡采样门控:可见比例 ≥25% 才画血条(画则以无深度屏显方式完整可见);
        // 完全躲在墙后(<25%)则整条隐藏。采样每 250ms 刷新一次并缓存
        if (barEnabled && !occlusionGate(mc, entity)) {
            barEnabled = false;
        }
        if (!barEnabled) return;

        float alpha = DisplayAnimation.alphaFor(entity.getId(), snap, gameTime);
        FRAME_RENDERED.put(entity.getId(), (int) gameTime);

        // 血条改为"实体期记录 → renderLevel 末尾统一绘制":末尾处于云/雨/水之后,
        // 血条作为屏幕覆盖层不再被天上的云或雨幕遮挡(与跳字同一层级)
        PENDING_BARS.put(entity.getId(), new PendingBar(style, entity, snap,
                rx, ry, rz, distSqr, alpha));
    }

    // ================= 血条绘制通道 =================
    // 实体渲染期只记录状态;renderLevel TAIL(云/雨之后)按距离排序统一绘制,屏幕覆盖层语义。

    private record PendingBar(EntityHealthStyle style, LivingEntity entity, EntityStatusSnapshot snap,
                              double x, double y, double z, double distSqr, float alpha) {}
    private static final Map<Integer, PendingBar> PENDING_BARS = new java.util.LinkedHashMap<>();

    /** 是否有待绘制血条(通道门控)。碎裂遗留会话也算待绘——否则无条期间遗留碎片
     *  永不推进/回收（性能审查 §1 半泄漏） */
    public static boolean barsPending() {
        if (PENDING_BARS.isEmpty()) return ShatterFx.hasActive();
        if (!ConfigManager.getConfig().barStyle.enableHealthBar) {
            PENDING_BARS.clear();
            return ShatterFx.hasActive();
        }
        return true;
    }

    /** 血条统一绘制:视锥/遮挡/开关已在前序门控,此处按距离 远→近 绘制 */
    public static void renderBarsGlobal(org.joml.Matrix4f viewMatrix) {
        if (PENDING_BARS.isEmpty()) return;
        var cfg = ConfigManager.getConfig();
        if (!cfg.barStyle.enableHealthBar) { PENDING_BARS.clear(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { PENDING_BARS.clear(); return; }
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Font font = mc.font;
        var bs = (net.minecraft.client.renderer.MultiBufferSource.BufferSource) mc.renderBuffers().bufferSource();
        List<PendingBar> list = new ArrayList<>(PENDING_BARS.values());
        PENDING_BARS.clear();
        list.sort((a, b) -> Double.compare(b.distSqr(), a.distSqr())); // 远→近
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f); // 复位残留染色

        long now = System.currentTimeMillis();
        // 碎裂接管收集（循环后统一绘制——循环内样式切换 render type 会提前 flush barRect 批,
        // 提前 get 的顶点引用会写进已结束的批次,碎片丢失）
        record ShatterJob(int id, PoseStack pose, double rx, double ry, double rz) {}
        List<ShatterJob> shatterJobs = new ArrayList<>();

        for (PendingBar st : list) {
            PoseStack pose = new PoseStack();
            pose.mulPoseMatrix(viewMatrix);
            pose.translate((float) (st.x() - camPos.x), (float) (st.y() - camPos.y),
                    (float) (st.z() - camPos.z)); // 实体脚部世界锚点,样式方法内部按 entityHeight 抬升
            // 死亡碎裂接管：dying + shatter + 有条矩形记录 → 原样式不画,碎片起飞
            if (st.snap().dying && ShatterFx.hasBox(st.snap().entityId)) {
                shatterJobs.add(new ShatterJob(st.snap().entityId, pose,
                        st.x() - camPos.x, st.y() - camPos.y, st.z() - camPos.z));
                continue;
            }
            switch (st.style()) {
                case MOBHEALTHBAR -> {
                    MobHealthBarStyle.render(st.snap(), pose, bs, 0xF000F0, st.alpha());
                    // 附加行（护甲/韧性/氧气）此前仅样式3 生效——样式1 也按同一实体附加组件开关渲染
                    // （设置页说明与工具提示均承诺"样式1/3 通用"）
                    renderStyleAAddonRows(st.snap(), pose, bs, 0xF000F0, st.alpha(), font);
                }
                case MOBPLAQUES -> renderPlaques(st.snap(), st.entity(), pose, bs, 0, 0xF000F0, st.alpha(), font);
                case ASTEORBAR -> {
                    renderAsteorBar(st.entity(), st.snap(), pose, bs, 0, st.alpha(), font);
                    renderAddonPlaqueRows(st.entity(), st.snap(), pose, bs, 0, 0xF000F0, st.alpha(), font);
                }
                default -> { }
            }
        }
        // 碎裂绘制（含遗留碎片）：全新 buffer 引用,不受样式渲染批切换影响
        if (!shatterJobs.isEmpty() || ShatterFx.hasActive()) {
            var vcShatter = ShatterFx.begin(bs);
            for (ShatterJob job : shatterJobs) {
                ShatterFx.render(job.id(), job.pose(), vcShatter, now,
                        job.rx(), job.ry(), job.rz(), true);
            }
            PoseStack orphanBase = new PoseStack();
            orphanBase.mulPoseMatrix(viewMatrix);
            ShatterFx.renderOrphans(orphanBase, vcShatter, now);
        }
        bs.endBatch();
    }

    // ================= 遮挡采样门控 =================
    // 语义:实体身体可见比例 ≥25% → 血条完整绘制(无深度屏显);
    //      <25%(基本整只躲在墙后)→ 血条整条隐藏。采样结果缓存 250ms。
    // 开销控制:8 点采样(2×2×2) + 近距早退——采样是逐方块射线,是帧率的主要热点之一

    private static final Map<Integer, Boolean> OCCLUSION_CACHE = new HashMap<>();
    private static final Map<Integer, Long> OCCLUSION_STAMP = new HashMap<>();
    private static final long OCCLUSION_INTERVAL_MS = 250L;
    /** 可见比例阈值(用户规则:能看到 25% 以上部分则血条完整可见) */
    private static final float OCCLUSION_MIN_RATIO = 0.25f;
    /** 近距早退平方距离(3 格内不可能被墙完全遮挡,免采样) */
    private static final double OCCLUSION_NEAR_EARLY_OUT = 9.0;

    private static boolean occlusionGate(Minecraft mc, LivingEntity entity) {
        // 近距早退:贴脸实体直接放行(射线采样成本与其意义都不存在)
        if (mc.player != null && entity.distanceToSqr(mc.player) < OCCLUSION_NEAR_EARLY_OUT) {
            return true;
        }
        long now = System.currentTimeMillis();
        long stamp = OCCLUSION_STAMP.getOrDefault(entity.getId(), 0L);
        if (now - stamp >= OCCLUSION_INTERVAL_MS) {
            OCCLUSION_STAMP.put(entity.getId(), now);
            OCCLUSION_CACHE.put(entity.getId(),
                    sampleVisibleRatio(mc, entity) >= OCCLUSION_MIN_RATIO);
        }
        return OCCLUSION_CACHE.getOrDefault(entity.getId(), true);
    }

    /** 包围盒 2x2x2 网格共 8 个采样点,射线(相机→采样点)未命中方块 = 该点可见 */
    private static float sampleVisibleRatio(Minecraft mc, LivingEntity entity) {
        if (mc.level == null) return 1f;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        var box = entity.getBoundingBox();
        int visible = 0;
        final int n = 2;
        for (int yi = 0; yi < n; yi++) {
            for (int xi = 0; xi < n; xi++) {
                for (int zi = 0; zi < n; zi++) {
                    Vec3 target = new Vec3(
                            box.minX + (box.maxX - box.minX) * (xi + 0.5) / n,
                            box.minY + (box.maxY - box.minY) * (yi + 0.5) / n,
                            box.minZ + (box.maxZ - box.minZ) * (zi + 0.5) / n);
                    var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, target,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, null));
                    if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) visible++;
                }
            }
        }
        return visible / (float) (n * n * n);
    }

    /** 样式 C 主条（复用 HealthDisplayRenderer.renderBar，含吸收环与文本） */
    private static void renderAsteorBar(LivingEntity entity, EntityStatusSnapshot snap, PoseStack poseStack,
            MultiBufferSource buffer, float partialTick,
            float alpha, Font font) {
        var cfg = ConfigManager.getConfig();
        var barCfg = cfg.barStyle;
        var health = (HealthDisplayRenderer) getRenderer("health");

        int barWidth = health.getBarWidth(snap);
        int barH = barCfg.barHalfHeight * 2;

        poseStack.pushPose();
        poseStack.translate(0, snap.entityHeight + (float) barCfg.barOffsetY, 0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.translate(0, 0, Math.max(0.002f, Mth.sqrt((float) snap.distanceSqr) * 0.002f));
        float worldScale = (float) barCfg.barScale * 0.025f;
        poseStack.scale(-worldScale, -worldScale, worldScale); // 原版名牌约定（-,-,+）：文字正立
        DisplayAnimation.applyScreenFx(poseStack, snap); // 整条动画（像素空间）

        int barX = -barWidth / 2;
        int barY = -barH; // 条底对齐挂点，向下绘制
        health.renderBar(poseStack, buffer, font, snap, barX, barY, barWidth, alpha, worldScale);
        poseStack.popPose();
    }

    /** 附加行组统一紧凑尺度（牌匾尺度的一半） */
    private static final float ADDON_ROW_SCALE = 0.025f * 0.5f;

    /** 附加行清单（entityAddons 开关 + 各行自身可见性）：护甲 / 韧性 / 氧气 */
    private static List<IMobDisplayRenderer> collectAddonRows(EntityStatusSnapshot snap) {
        var addons = ConfigManager.getConfig().entityAddons;
        List<IMobDisplayRenderer> rows = new ArrayList<>(3);
        var armor = getRenderer("armor");
        var air = getRenderer("air");
        var toughness = getRenderer("toughness");
        if (addons.armorRow && armor.wantsToRender(snap)) rows.add(armor);
        if (addons.toughnessRow && toughness.wantsToRender(snap)) rows.add(toughness);
        if (addons.airRow && air.wantsToRender(snap)) rows.add(air);
        return rows;
    }

    /** 样式 C 附加牌匾行：锚点 = 条挂点（含条像素偏移）,行起点在条下方 2px */
    private static void renderAddonPlaqueRows(LivingEntity entity, EntityStatusSnapshot snap, PoseStack poseStack,
                                              MultiBufferSource buffer, float partialTick,
                                              int packedLight, float alpha, Font font) {
        List<IMobDisplayRenderer> rows = collectAddonRows(snap);
        if (rows.isEmpty()) return;
        var barCfg = ConfigManager.getConfig().barStyle;
        // 条像素偏移同样带动附加行（条移动时整组跟随,否则附加行与条脱节）
        float pxPerPx = 0.025f * (float) barCfg.barScale;
        poseStack.pushPose();
        poseStack.translate(barCfg.barPixelOffsetX * pxPerPx,
                snap.entityHeight + (float) barCfg.barOffsetY + barCfg.barPixelOffsetY * pxPerPx, 0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-ADDON_ROW_SCALE, -ADDON_ROW_SCALE, ADDON_ROW_SCALE); // 原版名牌约定
        DisplayAnimation.applyScreenFx(poseStack, snap); // 附加行组跟随主条整条动画
        poseStack.translate(0, 2, 0); // 主条下方
        drawAddonRows(snap, poseStack, buffer, alpha, font, packedLight, rows);
        poseStack.popPose();
    }

    /** 样式 A（贴图条）附加牌匾行：按样式1 的条几何换算锚点——条底 + 数值行 + 2px 行距,
     *  并计入 scaleBar 缩放补偿与条像素偏移；心形排（barType 1）从锚点上堆,下缘即锚点。 */
    private static void renderStyleAAddonRows(EntityStatusSnapshot snap, PoseStack poseStack,
                                              MultiBufferSource buffer, int packedLight, float alpha, Font font) {
        List<IMobDisplayRenderer> rows = collectAddonRows(snap);
        if (rows.isEmpty()) return;
        var cfg = ConfigManager.getConfig().styleA;
        float s1 = 0.025f * (float) cfg.scaleBar; // 样式1 像素 → 方块
        float hs = cfg.scaleBarHeight > 0 ? (float) cfg.scaleBarHeight : 1f;
        float barBottom = cfg.barType == 1 ? 0f : (cfg.barType == 2 ? 11f : MobHealthBarStyle.frameHeight()) * hs;
        float numScale = (float) (cfg.scaleNums * 0.7);
        float textTop = cfg.barType == 1 ? 2f : barBottom + 2f; // 与 renderTexts 的 ty 同式
        float below = (cfg.showHp ? textTop + 8f * numScale : barBottom) + 2f;
        // 与 MobHealthBarStyle 相同的 scaleBar 高度补偿（条下沉/上移时附加行同随）
        float compPx = cfg.scaleBar < 1.0 ? 1.5f * (1f - (float) cfg.scaleBar) / 0.025f
                : cfg.scaleBar > 1.0 ? -((float) cfg.scaleBar - 1f) * 1.5f / 0.025f : 0f;
        poseStack.pushPose();
        poseStack.translate(cfg.offsetX * s1, snap.entityHeight + cfg.heightOffset
                + (compPx + (float) cfg.offsetY + below) * s1, 0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-ADDON_ROW_SCALE, -ADDON_ROW_SCALE, ADDON_ROW_SCALE); // 原版名牌约定
        DisplayAnimation.applyScreenFx(poseStack, snap); // 附加行组跟随主条整条动画
        drawAddonRows(snap, poseStack, buffer, alpha, font, packedLight, rows);
        poseStack.popPose();
    }

    /** 附加行绘制：调用方已完成定位与紧凑尺度缩放,y=0 = 行组顶部;行距 = entityAddons.rowGap */
    private static void drawAddonRows(EntityStatusSnapshot snap, PoseStack poseStack, MultiBufferSource buffer,
                                      float alpha, Font font, int packedLight, List<IMobDisplayRenderer> rows) {
        int rowGap = ConfigManager.getConfig().entityAddons.rowGap;
        int totalIconW = 0;
        for (IMobDisplayRenderer r : rows) totalIconW += r.getPlaqueWidth(font, snap) + 2;
        int maxTextW = 0;
        for (IMobDisplayRenderer r : rows) {
            String t = r.getValueText(snap);
            maxTextW = Math.max(maxTextW, t == null ? 0 : font.width(t) + 2);
        }
        int totalW = totalIconW + maxTextW;

        int rowX = -totalW / 2;
        int rowY = 0;
        int i = 0;
        for (IMobDisplayRenderer r : rows) {
            if (i > 0) rowY += rowGap;
            r.renderPlaque(poseStack, buffer, snap, rowX, rowY, font, packedLight, alpha, ADDON_ROW_SCALE);
            String text = r.getValueText(snap);
            if (text != null) {
                int color = ColorHelper.modifyAlpha(r.getValueColor(snap), (int) (alpha * 255));
                font.drawInBatch(text, rowX + r.getPlaqueWidth(font, snap) + 2, rowY + 1, color, false,
                        poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
            rowX += r.getPlaqueWidth(font, snap) + 2;
            i++;
        }
    }

    /** 样式 B：Mob Plaques 牌匾(实体绑定渲染,与原版名牌同管线;行换行 + 距离缩放 + 背景盒 + 数值文本) */
    private static void renderPlaques(EntityStatusSnapshot snap, LivingEntity entity,
                                      PoseStack poseStack, MultiBufferSource buffer,
                                      float partialTick, int packedLight, float alpha, Font font) {
        var plaqueCfg = ConfigManager.getConfig().plaqueStyle;
        Minecraft mc = Minecraft.getInstance();

        // 锚点固定在原版名牌基线（height + 0.5）
        poseStack.pushPose();
        poseStack.translate(0, snap.entityHeight + 0.5, 0);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float scale = 0.025f * (float) plaqueCfg.plaqueScale;
        float plaqueScale = scale / 0.025f;
        if (plaqueCfg.scaleWithDistance) {
            float pickRange = mc.gameMode != null ? mc.gameMode.getPickRange() : 4.5f;
            double denom = Math.pow(pickRange * 2.0, 2.0) / 2.0;
            double numer = snap.distanceSqr - Math.pow(pickRange / 2.0, 2.0);
            float ratio = (float) Mth.clamp(numer / denom, 0.0, 2.0);
            scale *= 1.0f + ratio;
        }
        poseStack.scale(-scale, -scale, scale);
        DisplayAnimation.applyScreenFx(poseStack, snap); // 整条动画（弹入/抖动/上浮/死亡收缩,像素空间）
        drawPlaqueRows(snap, poseStack, buffer, packedLight, alpha, font, plaqueScale);
        poseStack.popPose();
    }

    /**
     * 牌匾行组装与绘制 —— 世界路径与设置页实时预览共用。
     * 调用方位姿须已处于镜像像素空间（即世界路径的 scale(-0.025*plaqueScale) 之后）。
     * 行内偏移（belowNameTag/heightOffset 的 0.5/plaqueScale 补偿）与游戏内公式一致。
     */
    public static void drawPlaqueRows(EntityStatusSnapshot snap, PoseStack poseStack, MultiBufferSource buffer,
                                      int packedLight, float alpha, Font font, float plaqueScale) {
        var plaqueCfg = ConfigManager.getConfig().plaqueStyle;
        // 不透明度：图标/背景/数值统一淡出
        alpha *= Math.max(0f, Math.min(1f, plaqueCfg.opacity / 255f));

        // 整行偏移（牌匾像素；正值=屏幕向右/向下）；内含 push/pop，世界路径与预览共用
        poseStack.pushPose();
        poseStack.translate(plaqueCfg.xOffset, plaqueCfg.yOffset, 0);

        // 参与渲染的牌匾（样式 B 中各行为固有组件，受 plaqueStyle.showXRow 控制）
        List<IMobDisplayRenderer> active = new ArrayList<>(4);
        var health = getRenderer("health");
        var armor = getRenderer("armor");
        var air = getRenderer("air");
        var toughness = getRenderer("toughness");
        if (health.wantsToRender(snap)) active.add(health);
        if (plaqueCfg.showArmorRow && armor.wantsToRender(snap)) active.add(armor);
        if (plaqueCfg.showToughnessRow && toughness.wantsToRender(snap)) active.add(toughness);
        if (plaqueCfg.showAirRow && air.wantsToRender(snap)) active.add(air);
        if (active.isEmpty()) return;

        // 行打包（超过 maxPlaqueRowWidth 换行）——Mob Plaques getPlaquesWidths 算法
        int maxWidth = plaqueCfg.maxPlaqueRowWidth;
        List<List<IMobDisplayRenderer>> rows = new ArrayList<>();
        List<Integer> rowWidths = new ArrayList<>();
        int currentW = -1;
        List<IMobDisplayRenderer> currentRow = new ArrayList<>();
        for (IMobDisplayRenderer r : active) {
            int w = plaqueWidth(font, snap, r);
            if (currentW == -1 || maxWidth < currentW + 2 + w) {
                if (!currentRow.isEmpty()) { rows.add(currentRow); rowWidths.add(currentW); }
                currentRow = new ArrayList<>();
                currentW = w;
            } else {
                currentW += 2 + w;
            }
            currentRow.add(r);
        }
        if (!currentRow.isEmpty()) { rows.add(currentRow); rowWidths.add(currentW); }

        // 起始 Y（原 Mob Plaques 语义，全部在缩放后的像素空间内计算）：
        //   默认 -3；名牌下方模式 +23×(0.5/scale)；名牌上方模式 -(总行高+2)×(0.5/scale)；
        //   heightOffset 为正时向上抬升。0.5/scale 补偿使世界偏移与 plaqueScale 无关。
        float pxToWorldComp = 0.5f / plaqueScale;
        int totalRowsH = rows.size() * 13 - 2;
        int y = -3;
        if (plaqueCfg.renderBelowNameTag) {
            y += (int) (23 * pxToWorldComp);
        } else {
            y -= (int) ((totalRowsH + 2) * pxToWorldComp);
        }
        // 垂直位置改用 yOffset（见上方 pose 平移，正值向下）；旧 heightOffset 由校验器迁移

        // 数值文本统一最后绘制:字体渲染类型不写深度,若与后续牌匾的背景/容器共面绘制,
        // 后画者会盖住先画的文本(实测"深色容器叠住绿色数字")
        List<Object[]> textPass = new ArrayList<>(4);

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            List<IMobDisplayRenderer> row = rows.get(rowIdx);
            int rowW = rowWidths.get(rowIdx);
            // 行锚定只看图标宽度：数值位数变化（9→10→100）不再推挤/移动整行图标,
            // 文本从图标右侧向后延伸（行宽仍含文本,供换行与背景盒覆盖）
            int iconW = 0;
            for (IMobDisplayRenderer r : row) iconW += r.getPlaqueWidth(font, snap);
            iconW += Math.max(0, 2 * (row.size() - 1));
            int x = -iconW / 2;

            if (plaqueCfg.plaqueBackground) {
                int bg = ColorHelper.modifyAlpha(0xC0101010, (int) (alpha * 255));
                fillRect(poseStack, buffer, x - 1, y - 1, rowW + 2, 11, bg);
            }

            for (IMobDisplayRenderer r : row) {
                r.renderPlaque(poseStack, buffer, snap, x, y, font, packedLight, alpha, 0.025f * (float) plaqueScale);
                String text = r.getValueText(snap);
                if (text != null) {
                    int color = ColorHelper.modifyAlpha(r.getValueColor(snap), (int) (alpha * 255));
                    textPass.add(new Object[]{text, (float) (x + r.getPlaqueWidth(font, snap) + 2),
                            (float) (y + 1), color});
                }
                x += plaqueWidth(font, snap, r) + 2;
            }
            y += 13; // 行高 11 + 间距 2
        }

        // 文本遍历:置于本行全部背景/图标之上(覆盖层不写深度,层叠=绘制顺序)
        // 数值缩放/偏移：以文本左上锚点缩放，行宽已在 plaqueWidth 按缩放重算避免溢出
        float textScale = (float) plaqueCfg.textScale;
        for (Object[] t : textPass) {
            poseStack.pushPose();
            poseStack.translate((Float) t[1] + plaqueCfg.textOffsetX,
                    (Float) t[2] + plaqueCfg.textOffsetY, 0);
            poseStack.scale(textScale, textScale, 1f);
            font.drawInBatch((String) t[0], 0f, 0f, (Integer) t[3], false,
                    poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static int plaqueWidth(Font font, EntityStatusSnapshot snap, IMobDisplayRenderer r) {
        int iconW = r.getPlaqueWidth(font, snap);
        String t = r.getValueText(snap);
        if (t == null) return iconW;
        // 文本宽度按数值缩放重算，避免放大后溢出牌匾背景/行宽
        float ts = (float) ConfigManager.getConfig().plaqueStyle.textScale;
        return iconW + (int) (font.width(t) * ts) + 2;
    }

    // ================= 调试：Mob Plaques 原版复刻（逐行移植自反编译 MobPlaqueRenderer/Handler） =================

    private static final net.minecraft.resources.ResourceLocation ICONS_ORIGINAL =
            new net.minecraft.resources.ResourceLocation(
                    com.z80z99.z80zhealthbar.Z80ZHealthBar.MOD_ID, "textures/gui/icons_original.png");

    /** 原版牌匾记录：值/图标 UV/颜色（TransitionPlaque 常量 0x1EB100→0xED230D）。
     *  ownSheet：原 Mob Plaques 中仅韧性图标在其自有贴图表，其余（心/护甲/气泡）用原版 icons.png */
    private record OrigPlaque(String text, int iconU, int iconV, int color,
                              boolean containerBg, boolean ownSheet) {}

    /**
     * 原版复刻（仅调试，plaqueStyle.originalRender 开启时替换本类 renderPlaques）：
     * 牌匾 = 文字(左) + 单图标(右)，宽 = textWidth+2+9+2，高 11；
     * 顺序 health→air→armor→toughness（原 LinkedHashMap 注册序）；
     * 背景色 = options.getBackground(0.25f)；文字/图标双通道绘制（穿墙幽灵层）。
     */
    private static void renderPlaquesOriginal(EntityStatusSnapshot snap, LivingEntity entity,
                                              PoseStack poseStack, MultiBufferSource buffer,
                                              int packedLight, float alpha, Font font) {
        var cfg = ConfigManager.getConfig();
        var plaqueCfg = cfg.plaqueStyle;
        if (!plaqueCfg.enablePlaques) return;

        Minecraft mc = Minecraft.getInstance();

        // 原版注册顺序：health, air, armor, toughness
        List<OrigPlaque> plaques = new ArrayList<>(4);
        int healthValue = (int) Math.ceil(Math.min(snap.health, snap.maxHealth)) + (int) Math.ceil(snap.absorption);
        int healthMax = (int) Math.ceil(snap.maxHealth) + (int) Math.ceil(snap.absorption);
        boolean fullHealthHidden = false; // 满血隐藏由 MobVisibilityChecker 统一裁决，与原版等价
        var health = getRenderer("health");
        if (health.wantsToRender(snap) && !fullHealthHidden) {
            float ratio = healthMax <= 0 ? 0 : Mth.clamp(healthValue / (float) healthMax, 0f, 1f);
            plaques.add(new OrigPlaque(healthValue + "x", heartIconU(snap), heartIconV(),
                    transitionedColor(ratio), true, false));
        }
        if (plaqueCfg.showAirRow && snap.isUnderwater() && snap.airSupply > 0) {
            // 氧气耗尽（0）不占行：原版 0 氧气不显示气泡/计数（与附加行同一判据）
            plaques.add(new OrigPlaque(Math.max(0, snap.airSupply / 20) + "x", 16, 18, 0xFFFFFF, false, false));
        }
        if (plaqueCfg.showArmorRow && snap.hasArmor() && snap.armor > 0) {
            plaques.add(new OrigPlaque(snap.armor + "x", 34, 9, 0xFFFFFF, false, false));
        }
        if (plaqueCfg.showToughnessRow && snap.hasToughness()) {
            plaques.add(new OrigPlaque(snap.armorToughness + "x", 18, 0, 0xFFFFFF, false, true));
        }
        if (plaques.isEmpty()) return;

        int maxDist = plaqueCfg.maxRenderDistance;
        if (entity.isCrouching()) maxDist /= 2;
        if (snap.distanceSqr > (double) maxDist * maxDist) return;

        float plaqueScale = (float) plaqueCfg.plaqueScale;
        if (plaqueCfg.scaleWithDistance) {
            float pickRange = mc.gameMode != null ? mc.gameMode.getPickRange() : 4.5f;
            double denom = Math.pow(pickRange * 2.0, 2.0) / 2.0;
            double numer = snap.distanceSqr - Math.pow(pickRange / 2.0, 2.0);
            plaqueScale *= 1.0f + (float) Mth.clamp(numer / denom, 0.0, 2.0);
        }

        poseStack.pushPose();
        poseStack.translate(0, snap.entityHeight + 0.5, 0);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float scale = 0.025f * plaqueScale;
        poseStack.scale(-scale, -scale, scale);

        // 行打包（原 getPlaquesWidths：每块宽 + 2 间距）
        int maxWidth = plaqueCfg.maxPlaqueRowWidth;
        List<List<OrigPlaque>> rows = new ArrayList<>();
        List<Integer> rowWidths = new ArrayList<>();
        int currentW = -1;
        List<OrigPlaque> currentRow = new ArrayList<>();
        for (OrigPlaque p : plaques) {
            int w = font.width(p.text()) + 2 + 9 + 2;
            if (currentW == -1 || maxWidth < currentW + 2 + w) {
                if (!currentRow.isEmpty()) { rows.add(currentRow); rowWidths.add(currentW); }
                currentRow = new ArrayList<>();
                currentW = w;
            } else {
                currentW += 2 + w;
            }
            currentRow.add(p);
        }
        if (!currentRow.isEmpty()) { rows.add(currentRow); rowWidths.add(currentW); }

        // 起始 Y：与我们的常规路径同公式（镜像原版：默认 -3，下方模式 +23，上方模式 -总高）
        float pxComp = 0.5f / plaqueScale;
        int totalRowsH = rows.size() * 13 - 2;
        int y = -3;
        if (plaqueCfg.renderBelowNameTag) {
            y += (int) (23 * pxComp);
        } else {
            y -= (int) ((totalRowsH + 2) * pxComp);
        }
        y -= (int) (plaqueCfg.heightOffset * pxComp);

        int bg = mc.options.getBackgroundColor(0.25f); // 原版名牌背景色（反编译 m_92170_ = getBackgroundColor）
        int ghostLight = 0xF000F0;
        // 原版两 MOD 的实体空间绘制均为 POSITION_COLOR_TEX_LIGHTMAP 管线（color→uv→uv2）。
        // 图标按原版分表：心/护甲/气泡 = 原版 icons.png；韧性 = Mob Plaques 自有 icons.png
        VertexConsumer vanillaIcons = buffer.getBuffer(
                ModRenderType.originalBar(new net.minecraft.resources.ResourceLocation("minecraft", "textures/gui/icons.png")));
        VertexConsumer mpIcons = buffer.getBuffer(ModRenderType.originalBar(ICONS_ORIGINAL));

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            List<OrigPlaque> row = rows.get(rowIdx);
            int rowW = rowWidths.get(rowIdx);
            int x = -rowW / 2;
            for (OrigPlaque p : row) {
                int w = font.width(p.text()) + 2 + 9 + 2;
                // 背景（原版在 z=0.03，无深度测试下等价）
                fillRect(poseStack, buffer, x, y, w, 11, bg);
                // 文字双通道：幽灵层（SEE_THROUGH）+ 主层
                // 幽灵层 alpha 固定 0x20：不能 OR 进自带 0xFF alpha 的渐变色（会变全不透明）
                int textColor = 0xFF000000 | p.color();
                font.drawInBatch(p.text(), x + 1, y + 2, (p.color() & 0x00FFFFFF) | 0x20000000, false,
                        poseStack.last().pose(), buffer,
                        plaqueCfg.behindWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, 0, ghostLight);
                font.drawInBatch(p.text(), x + 1, y + 2, textColor, false,
                        poseStack.last().pose(), buffer,
                        plaqueCfg.behindWalls && plaqueCfg.fullBrightness == com.z80z99.z80zhealthbar.config.configs.PlaqueStyleConfig.FullBrightnessMode.ALWAYS
                                ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, 0, ghostLight);
                // 图标：容器底（仅生命）+ 双通道图标
                int iconX = x + w - 1 - 9;
                int iconY = y + 1;
                VertexConsumer icons = p.ownSheet() ? mpIcons : vanillaIcons;
                if (p.containerBg()) {
                    iconQuadTinted(icons, poseStack.last().pose(), iconX, iconY, 16, 0, ghostLight, 0x20FFFFFF);
                }
                iconQuadTinted(icons, poseStack.last().pose(), iconX, iconY, p.iconU(), p.iconV(), ghostLight, 0x20FFFFFF);
                int realLight = plaqueCfg.fullBrightness != com.z80z99.z80zhealthbar.config.configs.PlaqueStyleConfig.FullBrightnessMode.NEVER
                        ? 0xF000F0 : packedLight;
                if (p.containerBg()) {
                    iconQuadTinted(icons, poseStack.last().pose(), iconX, iconY, 16, 0, realLight, 0xFFFFFFFF);
                }
                iconQuadTinted(icons, poseStack.last().pose(), iconX, iconY, p.iconU(), p.iconV(), realLight, 0xFFFFFFFF);
                x += w + 2;
            }
            y += 13; // 11 高 + 2 间距（原版常量）
        }
        poseStack.popPose();
    }

    private static int heartIconU(EntityStatusSnapshot snap) {
        if (snap.absorption > 0) return com.z80z99.z80zhealthbar.mobdisplay.plaques.HeartType.ABSORBING.getTextureX();
        if (snap.poisoned) return com.z80z99.z80zhealthbar.mobdisplay.plaques.HeartType.POISON.getTextureX();
        if (snap.withered) return com.z80z99.z80zhealthbar.mobdisplay.plaques.HeartType.WITHER.getTextureX();
        if (snap.frozen) return com.z80z99.z80zhealthbar.mobdisplay.plaques.HeartType.FROZEN.getTextureX();
        return com.z80z99.z80zhealthbar.mobdisplay.plaques.HeartType.NORMAL.getTextureX();
    }

    private static int heartIconV() { return 0; }

    /** 原 TransitionPlaqueRenderer 渐变：满(0x1EB100 绿)→空(0xED230D 红)，ratio=1 必须取绿色 */
    private static int transitionedColor(float ratio) {
        return ColorHelper.lerp(0xFFED230D, 0xFF1EB100, Mth.clamp(ratio, 0f, 1f));
    }

    private static void iconQuadTinted(VertexConsumer vc, Matrix4f m, int x, int y,
                                       int u, int v, int light, int color) {
        float u1 = u / 256f, v1 = v / 256f, u2 = (u + 9) / 256f, v2 = (v + 9) / 256f;
        vc.vertex(m, x, y, 0).color(color).uv(u1, v1).uv2(light).endVertex();
        vc.vertex(m, x, y + 9, 0).color(color).uv(u1, v2).uv2(light).endVertex();
        vc.vertex(m, x + 9, y + 9, 0).color(color).uv(u2, v2).uv2(light).endVertex();
        vc.vertex(m, x + 9, y, 0).color(color).uv(u2, v1).uv2(light).endVertex();
    }

    private static void fillRect(PoseStack poseStack, MultiBufferSource buffer,
                                 int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(ModRenderType.barRect());
        vc.vertex(matrix, x, y, 0).color(color).endVertex();
        vc.vertex(matrix, x, y + h, 0).color(color).endVertex();
        vc.vertex(matrix, x + w, y + h, 0).color(color).endVertex();
        vc.vertex(matrix, x + w, y, 0).color(color).endVertex();
    }

    private static IMobDisplayRenderer getRenderer(String path) {
        return MobDisplayRegistry.get(
                new net.minecraft.resources.ResourceLocation(
                        com.z80z99.z80zhealthbar.Z80ZHealthBar.MOD_ID, path));
    }

    private static void purgeStale(long gameTime) {
        Iterator<Map.Entry<Integer, Integer>> it = FRAME_RENDERED.entrySet().iterator();
        while (it.hasNext()) {
            if (gameTime - it.next().getValue() > 40) it.remove();
        }
        // 遮挡缓存按自身采样时间戳清理（10 秒未采样 = 实体已远离/卸载）。
        // 此前用 retainAll(FRAME_RENDERED) —— 被墙挡住（采样不通过）的实体永远不进
        // FRAME_RENDERED,缓存每 tick 被清,8 次射线采样从 4Hz 退化到 20Hz（性能审查 P2）
        long nowMs = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> occIt = OCCLUSION_STAMP.entrySet().iterator();
        while (occIt.hasNext()) {
            var e = occIt.next();
            if (nowMs - e.getValue() > 10_000L) {
                occIt.remove();
                OCCLUSION_CACHE.remove(e.getKey());
            }
        }
        ShatterFx.purgeStale(FRAME_RENDERED.keySet());
        if (FIRST_SEEN.size() > 512) {
            Iterator<Map.Entry<Integer, Long>> it2 = FIRST_SEEN.entrySet().iterator();
            while (it2.hasNext()) {
                if (gameTime - it2.next().getValue() > 200) it2.remove();
            }
        }
    }

    /** 淡入 + 死亡淡出 + 整条位移动画（弹入/受击抖动/治疗上浮/死亡收缩——三种样式共用） */
    static final class DisplayAnimation {
        static float alphaFor(int entityId, EntityStatusSnapshot snap, long gameTime) {
            var cfg = ConfigManager.getConfig().visibility;
            float alpha = 1f;
            long first = FIRST_SEEN.getOrDefault(entityId, gameTime);
            FIRST_SEEN.put(entityId, first);
            if (cfg.fadeInTicks > 0) {
                alpha *= Mth.clamp((gameTime - first) / (float) cfg.fadeInTicks, 0f, 1f);
            }
            if (cfg.fadeOnDeath && snap.dying) {
                // 死亡进度用渲染侧本地时钟（不依赖实体 deathTime 同步——同步时序问题会让
                // deathTime 一步跳满,alpha 瞬间归零,实测血条"瞬间消失"而无渐隐/收缩）
                alpha *= 1f - deathProgressLocal(entityId, System.currentTimeMillis());
            }
            return alpha;
        }

        /** 弹入首次出现时间（ms）;容量超限整表重建（与 FIRST_SEEN 同生命周期语义） */
        private static final Map<Integer, Long> SPAWN_AT = new HashMap<>();
        /** 死亡起始时间（ms,渲染侧本地计时,1 秒动画）;实体未死即移除记录 */
        private static final Map<Integer, Long> DEATH_AT = new HashMap<>();

        /** 本地死亡进度 0..1（1 秒）;死亡期间保证平滑推进,与实体 deathTime 解耦。
         *  记录仅在容量超限时整表重建（实体死亡 1 秒后即从管线消失,无逐条清理必要） */
        static float deathProgressLocal(int entityId, long now) {
            if (DEATH_AT.size() > 1024) DEATH_AT.clear();
            long at = DEATH_AT.computeIfAbsent(entityId, k -> now);
            return Mth.clamp((now - at) / 1000f, 0f, 1f);
        }

        /**
         * 整条位移动画：在 billboard（mulPose 相机朝向）之后、镜像缩放之前调用。
         * 依次应用 弹入缩放/落入 → 受击抖动 y → 治疗上浮 y → 死亡收缩 scale+y（单位 = 像素样式单位）。
         */
        static void applyScreenFx(com.mojang.blaze3d.vertex.PoseStack pose, EntityStatusSnapshot snap) {
            var fx = ConfigManager.getConfig().dynamicFx;
            if (!fx.enabled) return;
            long now = System.currentTimeMillis();

            if (fx.spawnPop) {
                if (SPAWN_AT.size() > 1024) SPAWN_AT.clear();
                long spawnAt = SPAWN_AT.computeIfAbsent(snap.entityId, k -> now);
                float[] pop = com.z80z99.z80zhealthbar.overlay.HudFx.popIn(now - spawnAt);
                if (pop[0] != 1f || pop[1] != 0f) {
                    pose.translate(0, pop[1], 0);
                    pose.scale(pop[0], pop[0], 1f);
                }
            }

            // 受击抖动/治疗上浮：同帧二次 tick BarFx（dt≈0,状态与样式内首帧一致,renderTexts 同款先例）
            if (fx.hitShake || fx.healLift) {
                var st = BarFx.tick(snap.entityId, snap.plainHealthRatio(),
                        snap.hurtTime > 0, now);
                if (fx.hitShake) {
                    int dy = com.z80z99.z80zhealthbar.overlay.HudFx.shakeByMode(fx.shakeMode, now,
                            st.flash(), st.lastDamage());
                    if (dy != 0) pose.translate(0, dy, 0);
                }
                if (fx.healLift && st.heal() > 0.01f) {
                    pose.translate(0, -1.5f * st.heal(), 0);
                }
            }

            if (fx.deathShrink && snap.dying) {
                // 收缩同样用本地时钟（与渐隐同源）;sqrt 前置曲线保证渐隐早期即明显塌缩。
                // 不做 dp>=1 清记录——清了会让下一帧重新起表,死亡动画循环重播
                float dp = Mth.sqrt(deathProgressLocal(snap.entityId, now));
                float s = 1f - 0.4f * dp;
                pose.translate(0, 6f * dp, 0);
                pose.scale(s, s, 1f);
            }
        }
    }
}
