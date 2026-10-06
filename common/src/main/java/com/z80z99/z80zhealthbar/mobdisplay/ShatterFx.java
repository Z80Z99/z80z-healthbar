package com.z80z99.z80zhealthbar.mobdisplay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 死亡碎裂（dynamicFx.shatter）：实体死亡瞬间血条像玻璃一样碎成矩形碎片——
 * 各碎片带随机初速（外抛+上抛）、重力下坠、自转、约 0.9 秒淡出。
 *
 * <p>数据流：各样式渲染器把条矩形 record 进来（样式像素空间 + 该空间的缩放系数）；
 * {@code renderBarsGlobal} 在 dying &amp; shatter 时跳过原样式渲染,改为
 * {@link #render}——首帧生成碎片,后续帧推进物理并绘制;实体离开管线后
 * 遗留碎片按记录的世界锚点继续飞散直至寿命耗尽。
 */
final class ShatterFx {

    /** 碎片寿命（ms） */
    private static final float LIFE_MS = 900f;
    private static final float GRAVITY = 130f / 1000f / 1000f; // px/ms²
    private static final int COLS = 4, ROWS = 3;

    /** 单碎片：中心（相对条中心,px）、半宽高、速度（px/ms）、旋转与角速度 */
    private static final class Shard {
        float x, y, hw, hh, vx, vy, rot, vr;
    }

    /** 实体的碎裂会话：碎片组 + 出生时间 + 世界锚点/缩放（遗留渲染用） */
    private static final class Session {
        final List<Shard> shards = new ArrayList<>();
        long born;
        int color;
        double wx, wy, wz;   // 世界锚点（实体脚部,绝对坐标）
        float worldScale;    // 样式像素 → 世界单位系数（如 0.025*scale）
        float liftY;         // 条中心距实体脚部的抬升（世界单位,billboard 前的世界轴 y）
    }

    /** 样式渲染器 record 的条矩形（样式像素空间;每活帧刷新,dying 时被消费） */
    private static final Map<Integer, float[]> BOX = new HashMap<>();
    private static final Map<Integer, Integer> BOX_COLOR = new HashMap<>();
    private static final Map<Integer, Float> BOX_SCALE = new HashMap<>();
    private static final Map<Integer, Float> BOX_LIFT = new HashMap<>();
    /** 进行中的碎裂会话（含实体已离开管线的遗留碎片） */
    private static final Map<Integer, Session> ACTIVE = new HashMap<>();

    private ShatterFx() {}

    /** 样式渲染器每帧上报条矩形（cx/cy = 条中心,样式像素空间;scale = 该空间→世界系数;lift = 中心距脚部世界高度） */
    static void record(int entityId, float cx, float cy, float w, float h, int color, float scale, float lift) {
        if (w <= 0 || h <= 0) return;
        BOX.put(entityId, new float[]{cx, cy, w, h});
        BOX_COLOR.put(entityId, color);
        BOX_SCALE.put(entityId, scale);
        BOX_LIFT.put(entityId, lift);
    }

    /** 该实体是否有可碎裂的条矩形记录 */
    static boolean hasBox(int entityId) {
        return BOX.containsKey(entityId);
    }

    /**
     * 碎裂渲染（renderBarsGlobal 调用）：dying 实体首帧生成碎片并绘制;遗留会话继续推进。
     *
     * @param anchorWx/anchorWy/anchorWz 实体脚部世界锚点（相对相机,与 renderBarsGlobal 的 translate 一致）
     * @return true = 已接管（调用方跳过原样式渲染）
     */
    static boolean render(int entityId, PoseStack pose, VertexConsumer vc, long now,
                          double anchorWx, double anchorWy, double anchorWz, boolean dying) {
        var fx = ConfigManager.getConfig().dynamicFx;
        if (!fx.enabled || !fx.shatter) return false;

        Session s = ACTIVE.get(entityId);
        if (s == null) {
            if (!dying) return false;
            float[] box = BOX.get(entityId);
            if (box == null) return false;
            s = spawn(entityId, box, now);
            // 存绝对世界坐标（遗留渲染时相机已移动,不能存相对相机的值）;锚点 = 实体脚部
            var cam = net.minecraft.client.Minecraft.getInstance()
                    .gameRenderer.getMainCamera().getPosition();
            s.wx = anchorWx + cam.x;
            s.wy = anchorWy + cam.y;
            s.wz = anchorWz + cam.z;
            s.worldScale = BOX_SCALE.getOrDefault(entityId, 0.025f);
            s.liftY = BOX_LIFT.getOrDefault(entityId, 1.5f);
        }

        float t = (now - s.born) / LIFE_MS;
        if (t >= 1f) {
            ACTIVE.remove(entityId);
            return true; // 寿命已尽:不画也不走原渲染（原实体早已消失/渐隐归零）
        }

        drawShards(s, t, pose, vc);
        return true;
    }

    /** 是否存在仍需绘制的遗留会话（renderBarsGlobal 末尾补画用） */
    static boolean hasActive() {
        return !ACTIVE.isEmpty();
    }

    /** 遗留会话渲染（实体已离开管线的碎片继续飞散）;base = 仅含 viewMatrix 的 pose */
    static void renderOrphans(PoseStack base, VertexConsumer vc, long now) {
        if (ACTIVE.isEmpty()) return;
        var cam = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.getMainCamera().getPosition();
        List<Integer> done = new ArrayList<>();
        for (Map.Entry<Integer, Session> e : ACTIVE.entrySet()) {
            Session s = e.getValue();
            float t = (now - s.born) / LIFE_MS;
            if (t >= 1f) {
                done.add(e.getKey());
                continue;
            }
            PoseStack pose = new PoseStack();
            pose.mulPoseMatrix(base.last().pose());
            pose.translate((float) (s.wx - cam.x), (float) (s.wy - cam.y), (float) (s.wz - cam.z));
            drawShards(s, t, pose, vc);
        }
        for (int id : done) ACTIVE.remove(id);
    }

    private static Session spawn(int entityId, float[] box, long now) {
        Session s = new Session();
        s.born = now;
        s.color = BOX_COLOR.getOrDefault(entityId, 0xFFFFFFFF);
        Random r = new Random(entityId * 7919L + now);
        float w = box[2], h = box[3];
        float cw = w / COLS, ch = h / ROWS;
        for (int i = 0; i < COLS; i++) {
            for (int j = 0; j < ROWS; j++) {
                Shard sh = new Shard();
                sh.hw = cw / 2f;
                sh.hh = ch / 2f;
                sh.x = -w / 2f + cw * (i + 0.5f);
                sh.y = -h / 2f + ch * (j + 0.5f);
                // 初速：越靠外抛得越远（玻璃向外炸）,整体略向上
                float dirX = sh.x / Math.max(1f, w / 2f);
                sh.vx = (20f + r.nextFloat() * 26f) / 1000f * (dirX + (r.nextFloat() - 0.5f) * 0.6f);
                sh.vy = -(8f + r.nextFloat() * 30f) / 1000f;
                sh.rot = 0f;
                sh.vr = (r.nextFloat() - 0.5f) * 8f / 1000f;
                s.shards.add(sh);
            }
        }
        ACTIVE.put(entityId, s);
        return s;
    }

    /** 推进物理并绘制旋转 quad（pose = 实体脚部世界锚点;内部抬升至条中心 + billboard + 样式缩放） */
    private static void drawShards(Session s, float t, PoseStack pose, VertexConsumer vcRaw) {
        float alpha = 1f - t * t; // 后段加速淡出
        int color = ColorHelper.modifyAlpha(s.color, (int) (alpha * 255));
        float dt = 16f; // 单帧近似（渲染频率驱动,视觉无需精确积分）
        pose.pushPose();
        pose.translate(0, s.liftY, 0); // 世界轴抬升至条中心（billboard 前）
        var camOri = net.minecraft.client.Minecraft.getInstance()
                .getEntityRenderDispatcher().cameraOrientation();
        pose.mulPose(camOri);
        pose.scale(-s.worldScale, -s.worldScale, s.worldScale);
        Matrix4f m = pose.last().pose();
        for (Shard sh : s.shards) {
            sh.vy += GRAVITY * dt;
            sh.x += sh.vx * dt;
            sh.y += sh.vy * dt;
            sh.rot += sh.vr * dt;
            quad(vcRaw, m, sh, color);
        }
        pose.popPose();
    }

    /** 旋转矩形 quad：四角 = 中心 + R(rot)·(±hw,±hh) */
    private static void quad(VertexConsumer vc, Matrix4f m, Shard sh, int color) {
        float c = (float) Math.cos(sh.rot), sn = (float) Math.sin(sh.rot);
        float ex = sh.hw * c, ey = sh.hw * sn;   // 边向量 1（宽方向）
        float fx = -sh.hh * sn, fy = sh.hh * c;  // 边向量 2（高方向）
        float x = sh.x, y = sh.y;
        vc.vertex(m, x - ex - fx, y - ey - fy, 0).color(color).endVertex();
        vc.vertex(m, x - ex + fx, y - ey + fy, 0).color(color).endVertex();
        vc.vertex(m, x + ex + fx, y + ey + fy, 0).color(color).endVertex();
        vc.vertex(m, x + ex - fx, y + ey - fy, 0).color(color).endVertex();
    }

    static VertexConsumer begin(MultiBufferSource bs) {
        return bs.getBuffer(ModRenderType.barRect());
    }

    /** 清理已离开渲染管线的实体记录（保留仍有碎裂会话的 id） */
    static void purgeStale(java.util.Set<Integer> alive) {
        BOX.keySet().retainAll(alive);
        BOX_COLOR.keySet().retainAll(alive);
        BOX_SCALE.keySet().retainAll(alive);
        BOX_LIFT.keySet().retainAll(alive);
    }
}
