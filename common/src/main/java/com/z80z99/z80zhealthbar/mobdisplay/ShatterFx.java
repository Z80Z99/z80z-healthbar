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
 * 死亡碎裂（dynamicFx.shatter）：实体死亡瞬间血条像玻璃一样碎成**不规则四边形碎片**——
 * 条矩形按随机斜线递归切分（玻璃断面感,非均匀网格）,每片带独立亮度微差（断面反光）、
 * 随机初速（外抛+上抛）、重力下坠、自转、约 0.9 秒淡出。
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
    /** 目标碎片数（切分产出;过小的条自然少于该值） */
    private static final int SHARD_TARGET = 14;

    /** 单碎片：四角偏移（相对碎片中心,未旋转局部系）+ 物理（中心/速度/旋转）+ 断面亮度 */
    private static final class Shard {
        final float[] cx = new float[4];  // 角点局部 x
        final float[] cy = new float[4];  // 角点局部 y
        float x, y;          // 中心（相对条中心,px）
        float vx, vy;        // 速度（px/ms）
        float rot, vr;       // 旋转与角速度
        float bright;        // 断面亮度系数 0.8..1.2（每片独立,玻璃反光差）
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
    /** 已完成碎裂的实体（死亡动画长于 LIFE_MS 时防止二次生成——实测双爆;死亡结束即清除,容量封顶） */
    private static final java.util.Set<Integer> FINISHED = new java.util.HashSet<>();

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
            if (!dying) { FINISHED.remove(entityId); return false; }
            if (FINISHED.contains(entityId)) return true; // 本死亡已完成碎裂:余下帧继续接管（不重生）
            if (FINISHED.size() > 512) FINISHED.clear();
            float[] box = BOX.get(entityId);
            if (box == null) return false;
            s = spawn(entityId, box, now);
            com.z80z99.z80zhealthbar.Z80ZHealthBar.LOGGER.debug(
                    "[shatter] entity={} shards spawned", entityId);
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
            FINISHED.add(entityId); // 防死亡动画尾段二次生成
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

    // ================= 玻璃切分（纯函数,可单测） =================

    /**
     * 把 w×h 矩形按随机斜线递归切分成不规则四边形（玻璃断面）。
     *
     * <p>切法：每次取面积最大的碎片,沿其长边方向切一刀——两个对边上各取一个随机参数点
     * （0.3..0.7）连线,斜线保证产物是非矩形四边形;重复直到达到 target 或无可切碎片
     * （最小边 < 2px 不切,防细条被切成渣）。
     *
     * @return 四边形列表,每项 {x0,y0,x1,y1,x2,y2,x3,y3}（相对条中心;角点按序绕四边）
     */
    static List<float[]> glassSplit(float w, float h, Random r, int target) {
        List<float[]> pieces = new ArrayList<>();
        float hw = w / 2f, hh = h / 2f;
        pieces.add(new float[]{-hw, -hh, hw, -hh, hw, hh, -hw, hh});
        float minSplit = 2.0f; // px;最小可切边长
        while (pieces.size() < target) {
            // 取面积最大且可切的碎片（大块优先——玻璃裂纹分布均匀,不产渣）
            int best = -1;
            float bestArea = 0;
            boolean bestVertical = false;
            for (int i = 0; i < pieces.size(); i++) {
                float[] p = pieces.get(i);
                float bw = spanX(p), bh = spanY(p);
                boolean vertical = bw >= bh; // 沿长边切:竖切（分左右）当更宽时
                float cutSpan = vertical ? bw : bh;
                if (cutSpan < minSplit * 2) continue;
                float area = Math.abs(shoelace(p));
                if (area > bestArea) { bestArea = area; best = i; bestVertical = vertical; }
            }
            if (best < 0) break; // 全部太碎
            float[] p = pieces.remove(best);
            // 竖切:连 c0→c1（上边）与 c3→c2（下边）上的参数点;横切:连 c0→c3（左边）与 c1→c2（右边）
            float t1 = 0.3f + r.nextFloat() * 0.4f;
            float t2 = 0.3f + r.nextFloat() * 0.4f;
            float[] a, b;
            if (bestVertical) {
                float px0 = lerp(p[0], p[2], t1), py0 = lerp(p[1], p[3], t1); // 上边点
                float px1 = lerp(p[6], p[4], t2), py1 = lerp(p[7], p[5], t2); // 下边点
                a = new float[]{p[0], p[1], px0, py0, px1, py1, p[6], p[7]};
                b = new float[]{px0, py0, p[2], p[3], p[4], p[5], px1, py1};
            } else {
                float px0 = lerp(p[0], p[6], t1), py0 = lerp(p[1], p[7], t1); // 左边点
                float px1 = lerp(p[2], p[4], t2), py1 = lerp(p[3], p[5], t2); // 右边点
                a = new float[]{p[0], p[1], p[2], p[3], px1, py1, px0, py0};
                b = new float[]{px0, py0, px1, py1, p[4], p[5], p[6], p[7]};
            }
            pieces.add(a);
            pieces.add(b);
        }
        return pieces;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** 有向面积（角点按序;绝对值 = 面积,符号 = 绕向） */
    static float shoelace(float[] p) {
        float s = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            s += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1];
        }
        return s / 2f;
    }

    private static float spanX(float[] p) {
        return Math.max(Math.max(p[0], p[2]), Math.max(p[4], p[6]))
                - Math.min(Math.min(p[0], p[2]), Math.min(p[4], p[6]));
    }

    private static float spanY(float[] p) {
        return Math.max(Math.max(p[1], p[3]), Math.max(p[5], p[7]))
                - Math.min(Math.min(p[1], p[3]), Math.min(p[5], p[7]));
    }

    private static Session spawn(int entityId, float[] box, long now) {
        Session s = new Session();
        s.born = now;
        s.color = BOX_COLOR.getOrDefault(entityId, 0xFFFFFFFF);
        Random r = new Random(entityId * 7919L + now);
        float w = box[2], h = box[3];
        for (float[] q : glassSplit(w, h, r, SHARD_TARGET)) {
            Shard sh = new Shard();
            // 角点转为"相对碎片质心"的局部系（旋转绕质心,物理推质心）
            float mx = 0, my = 0;
            for (int i = 0; i < 4; i++) { mx += q[i * 2]; my += q[i * 2 + 1]; }
            mx /= 4f; my /= 4f;
            for (int i = 0; i < 4; i++) {
                sh.cx[i] = q[i * 2] - mx;
                sh.cy[i] = q[i * 2 + 1] - my;
            }
            sh.x = mx;
            sh.y = my;
            // 初速：越靠外抛得越远（玻璃向外炸）,整体略向上
            float dirX = sh.x / Math.max(1f, w / 2f);
            sh.vx = (20f + r.nextFloat() * 26f) / 1000f * (dirX + (r.nextFloat() - 0.5f) * 0.6f);
            sh.vy = -(8f + r.nextFloat() * 30f) / 1000f;
            sh.rot = 0f;
            sh.vr = (r.nextFloat() - 0.5f) * 8f / 1000f;
            // 断面亮度:0.8..1.2 每片独立（玻璃碎面反光各异）
            sh.bright = 0.8f + r.nextFloat() * 0.4f;
            s.shards.add(sh);
        }
        ACTIVE.put(entityId, s);
        return s;
    }

    /** 推进物理并绘制旋转四边形（pose = 实体脚部世界锚点;内部抬升至条中心 + billboard + 样式缩放） */
    private static void drawShards(Session s, float t, PoseStack pose, VertexConsumer vcRaw) {
        float alpha = 1f - t * t; // 后段加速淡出
        float dt = 16f; // 单帧近似（渲染频率驱动,视觉无需精确积分）
        pose.pushPose();
        pose.translate(0, s.liftY, 0); // 世界轴抬升至条中心（billboard 前）
        var camOri = net.minecraft.client.Minecraft.getInstance()
                .getEntityRenderDispatcher().cameraOrientation();
        pose.mulPose(camOri);
        pose.scale(-s.worldScale, -s.worldScale, s.worldScale);
        Matrix4f m = pose.last().pose();
        int baseA = (int) (alpha * 255);
        int[] colors = new int[s.shards.size()];
        for (int i = 0; i < s.shards.size(); i++) {
            colors[i] = brighten(ColorHelper.modifyAlpha(s.color, baseA), s.shards.get(i).bright);
        }
        int i = 0;
        for (Shard sh : s.shards) {
            sh.vy += GRAVITY * dt;
            sh.x += sh.vx * dt;
            sh.y += sh.vy * dt;
            sh.rot += sh.vr * dt;
            quad(vcRaw, m, sh, colors[i++]);
        }
        pose.popPose();
    }

    /** RGB 乘亮度系数（>1 钳到 255,防溢出色偏） */
    private static int brighten(int argb, float k) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) Math.min(255, ((argb >>> 16) & 0xFF) * k);
        int g = (int) Math.min(255, ((argb >>> 8) & 0xFF) * k);
        int b = (int) Math.min(255, (argb & 0xFF) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 旋转任意四边形:四角 = 质心 + R(rot)·(cx,cy) */
    private static void quad(VertexConsumer vc, Matrix4f m, Shard sh, int color) {
        float c = (float) Math.cos(sh.rot), sn = (float) Math.sin(sh.rot);
        for (int i = 0; i < 4; i++) {
            float lx = sh.cx[i], ly = sh.cy[i];
            vc.vertex(m, sh.x + lx * c - ly * sn, sh.y + lx * sn + ly * c, 0)
                    .color(color).endVertex();
        }
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
