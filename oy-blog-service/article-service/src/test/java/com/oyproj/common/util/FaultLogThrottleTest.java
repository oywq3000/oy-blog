package com.oyproj.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 故障日志限流器。
 *
 * <p>故障期间「每个事件一条 WARN+堆栈」会把日志管道打成第二个故障点，
 * 所以这里钉的是：一个窗口只放行一条，其余全部静默并计数，
 * 计数在下一次放行时被取走 —— 也就是日志里能看到的
 * 「本窗口已抑制 N 条同类故障」。</p>
 */
@DisplayName("FaultLogThrottle 故障日志限流")
class FaultLogThrottleTest {

    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private FaultLogThrottle throttle(long windowMillis) {
        return new FaultLogThrottle(windowMillis, clock::get);
    }

    @Test
    @DisplayName("窗口内只放行第一条，其余被抑制")
    void firstAllowed_restSuppressed() {
        FaultLogThrottle throttle = throttle(60_000L);

        assertTrue(throttle.allow(), "第一条必须放行（否则故障完全不可见）");
        assertFalse(throttle.allow());
        assertFalse(throttle.allow());
        assertFalse(throttle.allow());

        assertEquals(3L, throttle.drainSuppressed(), "被抑制的条数要能被下一次放行的日志播报出去");
        assertEquals(0L, throttle.drainSuppressed(), "取走之后必须清零，否则计数会无限累计");
    }

    @Test
    @DisplayName("窗口过后重新放行，并带上上一窗口的抑制计数")
    void nextWindow_allowsAgainAndReportsSuppressed() {
        FaultLogThrottle throttle = throttle(60_000L);

        assertTrue(throttle.allow());
        assertFalse(throttle.allow());
        assertFalse(throttle.allow());

        clock.addAndGet(60_000L);

        assertTrue(throttle.allow(), "窗口已过，必须重新放行");
        assertEquals(2L, throttle.drainSuppressed(), "播报的是上一窗口抑制的 2 条");
    }

    @Test
    @DisplayName("窗口内不会因任何原因提前放行")
    void staysSuppressedWithinWindow() {
        FaultLogThrottle throttle = throttle(60_000L);
        assertTrue(throttle.allow());

        // 每次只前进 1ms，反复调用都不该再次放行
        for (int i = 0; i < 100; i++) {
            clock.addAndGet(1L);
            assertFalse(throttle.allow());
        }
    }

    @Test
    @DisplayName("窗口长度有下限，配置成 0 也不会退化成「每条都放行」")
    void zeroWindow_doesNotDisableThrottling() {
        FaultLogThrottle throttle = throttle(0L);

        assertTrue(throttle.allow());
        assertFalse(throttle.allow(), "窗口 <=0 必须被夹到 >=1ms，不能变成不限流");
    }

    @Test
    @DisplayName("并发下每个窗口只有一个线程拿到配额")
    void concurrent_accessIsSafe() throws Exception {
        FaultLogThrottle throttle = throttle(60_000L);
        int threads = 8;
        int perThread = 200;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        AtomicLong allowed = new AtomicLong();

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    if (throttle.allow()) {
                        allowed.incrementAndGet();
                    }
                }
            });
            workers[t].start();
        }
        start.countDown();
        for (Thread w : workers) {
            w.join();
        }

        assertEquals(1L, allowed.get(), "同一个窗口内只允许一条日志通过");
        assertEquals(threads * perThread - 1, throttle.drainSuppressed(),
                "没能拿到配额的调用必须都被计成抑制，一条都不能丢（丢了就查不出故障规模）");
    }
}
