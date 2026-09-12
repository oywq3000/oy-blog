package com.oyproj.common.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 故障日志限流器：每个窗口只放行一条日志（调用方仍带完整堆栈），窗口内其余同类故障
 * 只累加计数；下一次放行的日志会把「上一窗口抑制了多少条」一起带出来。
 *
 * <p>为什么需要它：热榜是装饰功能，中间件（Redis / Kafka）挂掉时不能连带把日志管道也拖垮。
 * 没有限流时，行为事件是逐条消费的，一条故障日志一条堆栈 —— 一次 Redis 抖动就是每秒几十条
 * WARN+堆栈进 Filebeat，而 ELK 是刚上线的、吞吐余量最小的那一段，等于把一次中间件故障
 * 放大成第二次故障。</p>
 *
 * <p>线程安全：Kafka 消费线程（{@code recordEvent}）与定时任务线程（{@code recompute} /
 * {@code topArticleIds}）会并发使用同一个实例，配额用 CAS 抢占，保证窗口内只有一个线程
 * 输出日志。</p>
 */
public final class FaultLogThrottle {

    /** 默认抑制窗口：1 分钟。窗口长度换取的是一条日志/分钟的稳态噪音上限。 */
    public static final long DEFAULT_WINDOW_MILLIS = 60_000L;

    private final long windowMillis;
    private final LongSupplier clock;

    /** 下一次允许输出的时间戳（epoch millis） */
    private final AtomicLong nextAllowedAt = new AtomicLong(0L);
    /** 上一次放行以来被抑制的条数 */
    private final AtomicLong suppressed = new AtomicLong(0L);

    public FaultLogThrottle() {
        this(DEFAULT_WINDOW_MILLIS);
    }

    public FaultLogThrottle(long windowMillis) {
        this(windowMillis, System::currentTimeMillis);
    }

    /** 测试用：注入可控时钟，避免依赖真实时间流逝 */
    FaultLogThrottle(long windowMillis, LongSupplier clock) {
        this.windowMillis = Math.max(1L, windowMillis);
        this.clock = clock;
    }

    /**
     * 申请一次日志输出配额。
     *
     * @return true 表示调用方应当输出日志（含 throwable）；false 表示本窗口内已被别的
     *         调用抢先输出，本次应静默丢弃
     */
    public boolean allow() {
        long now = clock.getAsLong();
        long next = nextAllowedAt.get();
        if (now < next) {
            suppressed.incrementAndGet();
            return false;
        }
        // 窗口已过期：CAS 抢占「本轮第一」；抢输的线程按被抑制处理
        if (nextAllowedAt.compareAndSet(next, now + windowMillis)) {
            return true;
        }
        suppressed.incrementAndGet();
        return false;
    }

    /**
     * 取出并清零「上一次放行以来被抑制的条数」。
     *
     * <p>调用时机应紧跟一次返回 true 的 {@link #allow()}：那正是把上窗口的抑制计数
     * 顺带播报出去的时刻（否则这个计数器永远不会被读到）。</p>
     */
    public long drainSuppressed() {
        return suppressed.getAndSet(0L);
    }

    public long windowSeconds() {
        return windowMillis / 1000L;
    }
}
