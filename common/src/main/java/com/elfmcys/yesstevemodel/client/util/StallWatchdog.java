package com.elfmcys.yesstevemodel.client.util;

import com.elfmcys.yesstevemodel.YesSteveModel;

/**
 * 卡顿看门狗：检测渲染线程长时间没有推进，并打印各线程堆栈。
 *
 * <p>用途：游戏"卡死"类问题（死锁、无限循环、同步阻塞）在日志里往往毫无痕迹，
 * 事后无从定位。这个看门狗在检测到主线程超过阈值未 tick 时，把渲染线程
 * 以及持有锁的线程的堆栈打到日志里，直接指出卡在哪一行。
 *
 * <p><b>启动宽限期</b>：首次进世界时要解析全部模型，渲染线程会连续占用数秒，
 * 这属于正常工作量而非卡死。因此在 {@link #arm()} 被调用前（模型扫描完成时），
 * 阈值放宽到 {@link #GRACE_MS}；之后才按正常阈值判定。若因故一直没收到
 * {@link #arm()}，运行满 {@link #ARM_TIMEOUT_MS} 后也会自动进入严格模式，
 * 避免宽限期无限延长。
 *
 * <p>开销极低：正常运行时每次 tick 只做一次 volatile 写；后台线程每秒醒一次
 * 比较时间戳。触发后每 10 秒最多打印一次，避免刷屏。
 *
 * <p>默认阈值 3 秒。可通过系统属性 {@code -Dysm.stallWatchdogMs=5000} 调整，
 * 设为 0 或负数可关闭。
 */
public final class StallWatchdog {

    private static final long THRESHOLD_MS = Long.getLong("ysm.stallWatchdogMs", 3000L);

    /** 启动阶段的宽限阈值：模型解析本来就要几秒，不能当成卡死。 */
    private static final long GRACE_MS = Math.max(THRESHOLD_MS * 10L, 30_000L);

    /** 迟迟收不到 {@link #arm()} 时，最多宽限这么久。 */
    private static final long ARM_TIMEOUT_MS = 180_000L;

    /** 每次 tick 更新的时间戳。 */
    private static volatile long lastTickMs = System.currentTimeMillis();

    /** 第一次 tick 的时间，用于宽限期的兜底解除。 */
    private static volatile long firstTickMs = 0L;

    /** 被监视的线程（首次 tick 时记录）。 */
    private static volatile Thread watchedThread;

    /** 上一次打印堆栈的时间，用于限流。 */
    private static volatile long lastDumpMs = 0L;

    private static volatile boolean started = false;

    /** 是否已进入严格模式（模型扫描完成后）。 */
    private static volatile boolean armed = false;

    private StallWatchdog() {
    }

    /** 在客户端 tick 里调用。 */
    public static void tick() {
        long now = System.currentTimeMillis();
        watchedThread = Thread.currentThread();
        lastTickMs = now;
        if (firstTickMs == 0L) {
            firstTickMs = now;
        }
        if (!armed && now - firstTickMs > ARM_TIMEOUT_MS) {
            armed = true;
        }
        if (!started && THRESHOLD_MS > 0) {
            start();
        }
    }

    /**
     * 进入严格模式。模型扫描完成后调用 —— 此后渲染线程再长时间不推进就是真卡住了。
     * 可重复调用。
     */
    public static void arm() {
        armed = true;
    }

    private static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread t = new Thread(StallWatchdog::loop, "YSM Stall Watchdog");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
    }

    private static void loop() {
        while (true) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                return;
            }
            long threshold = armed ? THRESHOLD_MS : GRACE_MS;
            long idle = System.currentTimeMillis() - lastTickMs;
            if (idle < threshold) {
                continue;
            }
            long now = System.currentTimeMillis();
            if (now - lastDumpMs < 10_000L) {
                continue;
            }
            lastDumpMs = now;
            dump(idle, threshold);
        }
    }

    private static void dump(long idleMs, long thresholdMs) {
        try {
            Thread watched = watchedThread;
            StringBuilder sb = new StringBuilder(4096);
            sb.append("[YSM] 检测到主线程卡顿：已 ")
                    .append(idleMs).append(" ms 未推进（阈值 ").append(thresholdMs).append(" ms")
                    .append(armed ? "" : "，启动宽限期").append("）\n");

            if (watched != null) {
                sb.append("--- 被监视线程 [").append(watched.getName()).append("] 状态=")
                        .append(watched.getState()).append(" ---\n");
                appendStack(sb, watched.getStackTrace(), 40);
            }

            // 再抓一下所有持锁/阻塞的线程，死锁和同步阻塞通常在这里现形
            sb.append("--- 其它处于 BLOCKED / WAITING 的线程 ---\n");
            int dumped = 0;
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t == watched || dumped >= 6) {
                    continue;
                }
                Thread.State st = t.getState();
                if (st != Thread.State.BLOCKED && st != Thread.State.WAITING) {
                    continue;
                }
                String name = t.getName();
                // 只看本模组与网络/渲染相关的线程，避免被无关线程刷屏
                if (!(name.startsWith("YSM") || name.contains("Render") || name.contains("Server"))) {
                    continue;
                }
                sb.append("  * [").append(name).append("] ").append(st).append('\n');
                appendStack(sb, t.getStackTrace(), 12);
                dumped++;
            }
            YesSteveModel.LOGGER.error(sb.toString());
        } catch (Throwable ignored) {
            // 看门狗自身绝不能抛异常
        }
    }

    private static void appendStack(StringBuilder sb, StackTraceElement[] stack, int limit) {
        int n = Math.min(stack.length, limit);
        for (int i = 0; i < n; i++) {
            sb.append("      at ").append(stack[i]).append('\n');
        }
        if (stack.length > limit) {
            sb.append("      ... ").append(stack.length - limit).append(" more\n");
        }
    }
}
