package com.oyproj.common;

import com.oyproj.common.service.impl.CommonCacheImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("CommonCache 热榜所需 ZSet 能力")
class HotRankCacheOpsTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<Object, Object> redisTemplate = mock(RedisTemplate.class);
    private final RedisSerializer<Object> keySerializer = mock(RedisSerializer.class);

    private CommonCacheImpl cache;

    /**
     * 桩产出与 {@code String.getBytes(UTF_8)} <b>可区分</b>的字节。
     *
     * <p>如果桩直接返回 {@code arg.getBytes(UTF_8)}，那它和"绕过序列化器手写
     * {@code key.getBytes(UTF_8)}"产出的字节一模一样 —— CommonCacheImpl.keyBytes()
     * 就算退化成绕过序列化器，断言也照样通过。热榜链路上这种退化是静默的：
     * key 字节不一致时 ZUNIONSTORE 会把源集合当空集合并出一个空榜，接口则悄悄
     * 回退到 MySQL 全时段榜，没有任何报错。加前缀正是为了让"绕过"当场失败。</p>
     */
    private static final String SERIALIZER_MARKER = "jdk:";

    private static byte[] markerBytes(String key) {
        return (SERIALIZER_MARKER + key).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        // getKeySerializer() 返回 RedisSerializer<?>，thenReturn 无法绑定，故用 doReturn 形式
        doReturn(keySerializer).when(redisTemplate).getKeySerializer();
        // (Object) 强制 getArgument 的 T 推断为 Object，否则 String.valueOf 会选到 valueOf(char[]) 重载
        when(keySerializer.serialize(any())).thenAnswer(inv -> markerBytes(
                String.valueOf((Object) inv.getArgument(0))));
        cache = new CommonCacheImpl(redisTemplate);
    }

    @Test
    void zUnionStore_passesWeightsAndAllSourceKeys() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(3L);

        Long result = cache.zUnionStore("hot:7d", List.of("hot:d1", "hot:d2"),
                new int[]{18, -1});

        assertEquals(3L, result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisCallback<Long>> captor = ArgumentCaptor.forClass(RedisCallback.class);
        verify(redisTemplate).execute(captor.capture());

        RedisConnection conn = mock(RedisConnection.class);
        when(conn.zUnionStore(any(byte[].class), any(Aggregate.class), any(Weights.class),
                any(byte[][].class))).thenReturn(3L);
        captor.getValue().doInRedis(conn);

        ArgumentCaptor<Weights> weightsCaptor = ArgumentCaptor.forClass(Weights.class);
        ArgumentCaptor<byte[][]> setsCaptor = ArgumentCaptor.forClass(byte[][].class);
        ArgumentCaptor<byte[]> destCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(conn).zUnionStore(destCaptor.capture(), eq(Aggregate.SUM),
                weightsCaptor.capture(), setsCaptor.capture());

        assertArrayEquals(new double[]{18.0, -1.0}, weightsCaptor.getValue().toArray(), 0.001);
        assertEquals(2, setsCaptor.getValue().length);
        assertArrayEquals(markerBytes("hot:7d"), destCaptor.getValue(),
                "目标 key 也必须走 keySerializer");
        // 两个源 key 都要查：只查 [0] 的话，"循环只写了下标 0"这种错会漏过去，
        // 而 7 天榜正是一个 7 元素循环写出来的。
        assertArrayEquals(markerBytes("hot:d1"), setsCaptor.getValue()[0],
                "源 key 必须来自 keySerializer，而不是手写的 key.getBytes(UTF_8)");
        assertArrayEquals(markerBytes("hot:d2"), setsCaptor.getValue()[1],
                "第二个源 key 也必须序列化后写入（只写了下标 0 的循环要在这里失败）");
    }

    @Test
    void expire_delegatesToTemplate() {
        when(redisTemplate.expire(eq("hot:d1"), any(java.time.Duration.class))).thenReturn(true);

        assertTrue(cache.expire("hot:d1", 691200L));

        verify(redisTemplate).expire("hot:d1", java.time.Duration.ofSeconds(691200L));
    }
}
