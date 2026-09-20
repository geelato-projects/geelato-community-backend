package cn.geelato.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TTL 惰性过期、容量护栏（先清过期再整体清空）、null 哨兵防穿透语义的行为验证。
 * 时间敏感用例的 TTL/等待均在数百毫秒量级，清扫线程（60 秒）不会干扰判定。
 */
class LocalBoundedCacheTest {

    @Test
    void putGetExistsRemoveRoundTrip() {
        LocalBoundedCache<String, String> cache = new LocalBoundedCache<>("test-rt", 60_000L, 100);

        cache.put("k", "v");
        assertTrue(cache.exists("k"));
        assertEquals("v", cache.get("k"));

        cache.remove("k");
        assertFalse(cache.exists("k"));
        assertNull(cache.get("k"));

        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void expiredEntryReturnsNullAndIsRemovedOnRead() throws InterruptedException {
        LocalBoundedCache<String, String> cache = new LocalBoundedCache<>("test-ttl", 150L, 100);

        cache.put("k", "v");
        assertEquals("v", cache.get("k"));
        Thread.sleep(300L);

        assertNull(cache.get("k"));
        assertFalse(cache.exists("k"));
        assertEquals(0, cache.size());
    }

    @Test
    void nullValueIsCacheableForPenetrationGuard() {
        LocalBoundedCache<String, String> cache = new LocalBoundedCache<>("test-null", 60_000L, 100);

        cache.put("k", null);
        assertTrue(cache.exists("k"));
        assertNull(cache.get("k"));

        List<String> values = cache.values();
        assertEquals(1, values.size());
        assertNull(values.get(0));
    }

    @Test
    void capacityGuardClearsAllWhenNoExpiredPurgeable() {
        LocalBoundedCache<Integer, String> cache = new LocalBoundedCache<>("test-cap", 0L, 3);

        for (int i = 1; i <= 5; i++) {
            cache.put(i, "v" + i);
        }

        assertNull(cache.get(1));
        assertNull(cache.get(2));
        assertNull(cache.get(3));
        assertEquals("v4", cache.get(4));
        assertEquals("v5", cache.get(5));
        assertTrue(cache.size() <= 3);
    }

    @Test
    void capacityGuardPurgesExpiredBeforeClearing() throws InterruptedException {
        LocalBoundedCache<String, String> cache = new LocalBoundedCache<>("test-cap-ttl", 100L, 3);

        cache.put("old1", "1");
        cache.put("old2", "2");
        cache.put("old3", "3");
        Thread.sleep(250L);
        // 触发护栏时过期条目先行清扫，腾出空间，不需要整体 clear
        cache.put("new1", "n1");
        cache.put("new2", "n2");

        assertEquals("n1", cache.get("new1"));
        assertEquals("n2", cache.get("new2"));
    }

    @Test
    void noTtlModeNeverExpires() throws InterruptedException {
        LocalBoundedCache<String, String> cache = new LocalBoundedCache<>("test-nottl", 0L, 100);

        cache.put("k", "v");
        Thread.sleep(200L);
        assertEquals("v", cache.get("k"));
        assertEquals(0, cache.purgeExpired());
    }

    @Test
    void removeIfMatchesLiveEntriesOnly() {
        LocalBoundedCache<String, Integer> cache = new LocalBoundedCache<>("test-rmif", 60_000L, 100);

        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        int removed = cache.removeIf((k, v) -> v == 2);

        assertEquals(1, removed);
        assertNull(cache.get("b"));
        assertEquals(1, cache.get("a"));
        assertEquals(3, cache.get("c"));
    }

    @Test
    void liveKeysAndValuesExcludeExpired() throws InterruptedException {
        LocalBoundedCache<String, String> cache = new LocalBoundedCache<>("test-live", 100L, 100);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        Thread.sleep(250L);
        cache.put("k3", "v3");

        assertEquals(List.of("k3"), cache.liveKeys());
        assertEquals(List.of("v3"), cache.values());
    }

    @Test
    void constructorRejectsIllegalArguments() {
        assertThrows(IllegalArgumentException.class, () -> new LocalBoundedCache<>("", 60_000L, 100));
        assertThrows(IllegalArgumentException.class, () -> new LocalBoundedCache<>(null, 60_000L, 100));
        assertThrows(IllegalArgumentException.class, () -> new LocalBoundedCache<>("t", -1L, 100));
        assertThrows(IllegalArgumentException.class, () -> new LocalBoundedCache<>("t", 60_000L, 0));
    }

    @Test
    void concurrentAccessSmoke() throws InterruptedException {
        LocalBoundedCache<Integer, Integer> cache = new LocalBoundedCache<>("test-conc", 0L, 100);
        int threads = 8;
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int seed = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < 2_000; i++) {
                        int key = (seed * 31 + i) % 500;
                        cache.put(key, i);
                        cache.get(key);
                        if (i % 100 == 0) {
                            cache.remove(key);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
        cache.put(1_000, 1);
        assertTrue(cache.size() <= 100);
    }
}
