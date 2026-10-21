package com.z80z99.z80zhealthbar.datasource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 后台变化探测会话（**静态、不写配置**）。
 *
 * <p>解决的问题：单人游戏打开编辑器时世界暂停，玩家必须关掉界面才能喝水/吃东西；
 * 回来时已经忘了刚才在测哪一个。会话在关闭编辑器后继续采样，并用 HUD 面板实时显示，
 * 玩家做动作时直接看着结果，不需要记忆。
 *
 * <p>性能护栏：候选上限 {@link #MAX_CANDIDATES}、{@link #AUTO_STOP_MS} 自动停止、
 * 单候选熔断（见 {@link Candidate}）、会话结束（玩家退出世界）即清空。
 */
public final class ProbeSession {

    public static final int MAX_CANDIDATES = 20;
    public static final long AUTO_STOP_MS = 180_000L;
    /** 单次调用超过此值 → 该候选熔断 */
    public static final long SLOW_SINGLE_MS = 10L;
    /** 平均耗时超过此值 → 该候选熔断 */
    public static final double SLOW_AVG_MS = 1.0;

    /** 单个候选的运行状态 */
    public static final class Candidate {
        public final String key;
        public final String label;
        public final Function<Void, Float> reader;
        public Float baseline;
        public Float current;
        public int changes;
        public long firstChangeAt = -1;
        public long totalNanos;
        public int samples;
        public boolean aborted;
        public String abortReason;

        Candidate(String key, String label, Function<Void, Float> reader,
                  Float baseline, Float current) {
            this.key = key;
            this.label = label;
            this.reader = reader;
            this.baseline = baseline;
            this.current = current;
        }

        /** 相对基线是否已变化 */
        public boolean changed() {
            return changes > 0;
        }

        /** 平均耗时（毫秒） */
        public double avgMs() {
            return samples == 0 ? 0 : totalNanos / 1_000_000.0 / samples;
        }
    }

    private static final List<Candidate> CANDIDATES = new ArrayList<>();
    private static String sessionKey = "";
    private static String title = "";
    private static long startedAt;
    private static long lastSampleAt;
    private static long stoppedAt;
    private static boolean stopped;
    private static long absentSince = -1;

    private ProbeSession() {}

    /** 会话是否处于采样中 */
    public static boolean isActive() {
        return !stopped && !CANDIDATES.isEmpty();
    }

    /** 是否有会话（含已停止但结果仍可查看） */
    public static boolean hasSession() {
        return !CANDIDATES.isEmpty();
    }

    public static String sessionKey() {
        return sessionKey;
    }

    public static String title() {
        return title;
    }

    public static long startedAt() {
        return startedAt;
    }

    public static long stoppedAt() {
        return stoppedAt;
    }

    public static boolean isStopped() {
        return stopped;
    }

    public static List<Candidate> candidates() {
        return CANDIDATES;
    }

    /** 已运行秒数 */
    public static int elapsedSeconds(long now) {
        long end = stopped ? stoppedAt : now;
        return (int) Math.max(0, (end - startedAt) / 1000L);
    }

    /**
     * 开始（或替换）一个会话。
     *
     * @param key       会话标识（通常 = 类名）
     * @param title     显示标题（模组名/类简单名）
     * @param labels    候选显示名（与 readers 一一对应）
     * @param readers   读取函数（返回 null = 读取失败）
     */
    public static void start(String key, String title, List<String> labels,
                             List<Function<Void, Float>> readers, long now) {
        CANDIDATES.clear();
        sessionKey = key == null ? "" : key;
        ProbeSession.title = title == null ? "" : title;
        startedAt = now;
        lastSampleAt = 0;
        stopped = false;
        stoppedAt = 0;
        absentSince = -1;
        int n = Math.min(MAX_CANDIDATES, Math.min(labels == null ? 0 : labels.size(),
                readers == null ? 0 : readers.size()));
        for (int i = 0; i < n; i++) {
            String label = labels.get(i) == null ? ("#" + i) : labels.get(i);
            Function<Void, Float> reader = readers.get(i);
            Float v = safeRead(reader);
            CANDIDATES.add(new Candidate(key + "#" + i, label, reader, v, v));
        }
    }

    /** 重置基线（保留候选与读取函数，重新计时） */
    public static void resetBaseline(long now) {
        startedAt = now;
        stopped = false;
        stoppedAt = 0;
        for (Candidate c : CANDIDATES) {
            c.baseline = c.current;
            c.changes = 0;
            c.firstChangeAt = -1;
        }
    }

    /** 停止采样（结果保留，可查看/选择） */
    public static void stop(long now) {
        if (stopped) return;
        stopped = true;
        stoppedAt = now;
    }

    /** 清空会话（退出世界/关闭时调用） */
    public static void clear() {
        CANDIDATES.clear();
        sessionKey = "";
        title = "";
        startedAt = 0;
        lastSampleAt = 0;
        stoppedAt = 0;
        stopped = false;
        absentSince = -1;
    }

    /**
     * 采样一次（由 tick 钩子按 2Hz 调用）。逐候选计时并熔断慢调用。
     *
     * @return 本次实际采样的候选数
     */
    public static int sample(long now) {
        if (!isActive()) return 0;
        if (now - startedAt >= AUTO_STOP_MS) {
            stop(now);
            return 0;
        }
        if (now - lastSampleAt < 500L) return 0;
        lastSampleAt = now;
        int sampled = 0;
        for (Candidate c : CANDIDATES) {
            if (c.aborted) continue;
            long t0 = System.nanoTime();
            Float v = safeRead(c.reader);
            long cost = System.nanoTime() - t0;
            c.totalNanos += cost;
            c.samples++;
            if (cost / 1_000_000.0 > SLOW_SINGLE_MS || c.avgMs() > SLOW_AVG_MS) {
                c.aborted = true;
                c.abortReason = "slow";
                continue;
            }
            if (v == null) continue;
            c.current = v;
            if (c.baseline != null && !v.equals(c.baseline)) {
                c.changes++;
                c.firstChangeAt = now;
                c.baseline = v; // 基线跟随最新值,变化次数累计
            } else if (c.baseline == null) {
                c.baseline = v;
            }
            sampled++;
        }
        return sampled;
    }

    /** 玩家离开世界检测：连续 2 秒无玩家 → 清空（避免残留到下一个存档） */
    public static void tickPresence(boolean playerPresent, long now) {
        if (playerPresent) {
            absentSince = -1;
            return;
        }
        if (absentSince < 0) absentSince = now;
        else if (now - absentSince > 2000L) clear();
    }

    /** 变化候选优先，其余保持原顺序 */
    public static List<Candidate> sortedForDisplay() {
        List<Candidate> out = new ArrayList<>(CANDIDATES);
        out.sort(Comparator.comparing((Candidate c) -> !c.changed()));
        return out;
    }

    /** 变化候选数 */
    public static int changedCount() {
        int n = 0;
        for (Candidate c : CANDIDATES) if (c.changed()) n++;
        return n;
    }

    /** 会话快照（面板渲染用，避免渲染期读可变状态） */
    public static Map<String, Float> snapshot() {
        Map<String, Float> out = new LinkedHashMap<>();
        for (Candidate c : CANDIDATES) out.put(c.key, c.current);
        return out;
    }

    private static Float safeRead(Function<Void, Float> reader) {
        if (reader == null) return null;
        try {
            return reader.apply(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
