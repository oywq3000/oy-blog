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

    @BeforeEach
    void setUp() {
        // getKeySerializer() 返回 RedisSerializer<?>，thenReturn 无法绑定，故用 doReturn 形式
        doReturn(keySerializer).when(redisTemplate).getKeySerializer();
        // (Object) 强制 getArgument 的 T 推断为 Object，否则 String.valueOf 会选到 valueOf(char[]) 重载
        when(keySerializer.serialize(any())).thenAnswer(inv ->
                String.valueOf((Object) inv.getArgument(0)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
        verify(conn).zUnionStore(any(byte[].class), eq(Aggregate.SUM),
                weightsCaptor.capture(), setsCaptor.capture());

        assertArrayEquals(new double[]{18.0, -1.0}, weightsCaptor.getValue().toArray(), 0.001);
        assertEquals(2, setsCaptor.getValue().length);
        assertArrayEquals("hot:d1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                setsCaptor.getValue()[0]);
    }

    @Test
    void expire_delegatesToTemplate() {
        when(redisTemplate.expire(eq("hot:d1"), any(java.time.Duration.class))).thenReturn(true);

        assertTrue(cache.expire("hot:d1", 691200L));

        verify(redisTemplate).expire("hot:d1", java.time.Duration.ofSeconds(691200L));
    }
}
