package com.elfmcys.yesstevemodel.client.util;

import com.elfmcys.yesstevemodel.YesSteveModel;

/**
 * 卡顿看门狗：检测渲染线程长时间没有推进，并打印各线程堆栈。
 *
 * <p>用途：游戏"卡死"类问题（死锁、无限循环、同步阻塞）在日志里往往毫无痕迹，
 * 事后无从定位。这个看门狗在检测到主线程超过阈值未 tick 时，把渲染线程
 * 以及持有锁的线程的堆栈打到日志里，直接指出卡在哪一行。
 *
 * <p>开销极低：正常运行时每次 tick 只做一次 volatile 写；后台线程每秒醒一次
 * 比较时间戳。触发后每 10 秒最多打印一次，避免刷屏。
 *
 * <p>默认阈值 3 秒。可通过系统属性 {@code -Dysm.stallWatchdogMs=5000} 调整，
 * 设为 0 或负数可关闭。
 */
public final class StallWatchdog {

    private static final long THRESHOLD_MS = Long.getLong("ysm.stallWatchdogMs", 3000L);

    /** 每次 tick 更新的时间戳。 */
    private static volatile long lastTickMs = System.currentTimeMillis();

    /** 被监视的线程（首次 tick 时记录）。 */
    private static volatile Thread watchedThread;

    /** 上一次打印堆栈的时间，用于限流。 */
    private static volatile long lastDumpMs = 0L;

    private static volatile boolean started = false;

    private StallWatchdog() {
    }

    /** 在客户端 tick 里调用。 */
    public static void tick() {
        watchedThread = Thread.currentThread();
        lastTickMs = System.currentTimeMillis();
        if (!started && THRESHOLD_MS > 0) {
            start();
        }
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
            long idle = System.currentTimeMillis() - lastTickMs;
            if (idle < THRESHOLD_MS) {
                continue;
            }
            long now = System.currentTimeMillis();
            if (now - lastDumpMs < 10_000L) {
                continue;
            }
            lastDumpMs = now;
            dump(idle);
        }
    }

    private static void dump(long idleMs) {
        try {
            Thread watched = watchedThread;
            StringBuilder sb = new StringBuilder(4096);
            sb.append("[YSM] 检测到主线程卡顿：已 ")
                    .append(idleMs).append(" ms 未推进（阈值 ").append(THRESHOLD_MS).append(" ms）\n");

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
