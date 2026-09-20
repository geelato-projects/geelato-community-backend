package cn.geelato.utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

/**
 * 进程内有界本地缓存：TTL 过期 + 容量护栏，收编平台内散落的裸 ConcurrentHashMap 本地缓存。
 * <p>
 * 过期采用读时惰性判定（过期即移除并返回 null）+ 全实例共享的单一守护清扫线程（60 秒一次），
 * 容量护栏沿用既有惯例：put 时超限先清过期条目、仍超则整体清空。
 * <p>
 * null 值以哨兵入缓存（对齐 j2cache {@code default_cache_null_object=true} 语义），
 * exists=true 且 get=null 表示"已缓存的空值"，可用于防穿透。
 * <p>
 * 仅适用于单实例进程内缓存；跨实例一致性场景应使用 j2cache 等分布式缓存。
 *
 * @author geelato
 */
public final class LocalBoundedCache<K, V> {

    /** null 值哨兵：ConcurrentHashMap 不接受 null value，防穿透语义需要缓存 null */
    private static final Object NULL_SENTINEL = new Object();

    /** 全部活实例的弱引用注册表：清扫线程遍历用，不阻止实例被 GC */
    private static final List<WeakReference<LocalBoundedCache<?, ?>>> INSTANCES = new CopyOnWriteArrayList<>();

    static {
        Thread reaper = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                for (WeakReference<LocalBoundedCache<?, ?>> ref : INSTANCES) {
                    LocalBoundedCache<?, ?> cache = ref.get();
                    if (cache != null) {
                        cache.purgeExpired();
                    }
                }
                INSTANCES.removeIf(ref -> ref.get() == null);
            }
        }, "LocalBoundedCache-Reaper");
        reaper.setDaemon(true);
        reaper.start();
    }

    private final String name;
    /** <=0 表示永不过期，仅受容量护栏约束 */
    private final long ttlMillis;
    private final int maxEntries;
    private final ConcurrentHashMap<K, Entry> cache = new ConcurrentHashMap<>();

    /**
     * @param name       缓存名（用于诊断）
     * @param ttlMillis  写后过期毫秒数，<=0 表示永不过期
     * @param maxEntries 容量上限，超限先清过期、仍超整体清空
     */
    public LocalBoundedCache(String name, long ttlMillis, int maxEntries) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("LocalBoundedCache name 不能为空");
        }
        if (ttlMillis < 0) {
            throw new IllegalArgumentException("LocalBoundedCache ttlMillis 不能为负数: " + ttlMillis);
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("LocalBoundedCache maxEntries 必须为正数: " + maxEntries);
        }
        this.name = name.trim();
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
        INSTANCES.add(new WeakReference<>(this));
    }

    public String getName() {
        return name;
    }

    public void put(K key, V value) {
        if (cache.size() >= maxEntries) {
            purgeExpired();
            if (cache.size() >= maxEntries) {
                cache.clear();
            }
        }
        long expireAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
        cache.put(key, new Entry(value, expireAt));
    }

    /**
     * 读时惰性过期：命中但已过期的条目会被移除并返回 null。
     */
    @SuppressWarnings("unchecked")
    public V get(K key) {
        Entry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expired(System.currentTimeMillis())) {
            cache.remove(key, entry);
            return null;
        }
        return unwrap(entry);
    }

    public boolean exists(K key) {
        Entry entry = cache.get(key);
        return entry != null && !entry.expired(System.currentTimeMillis());
    }

    /**
     * @return 被移除条目的值；不存在或已过期返回 null（对齐 Map.remove 惯例）
     */
    @SuppressWarnings("unchecked")
    public V remove(K key) {
        Entry entry = cache.remove(key);
        if (entry == null || entry.expired(System.currentTimeMillis())) {
            return null;
        }
        return entry.value == NULL_SENTINEL ? null : (V) entry.value;
    }

    public void clear() {
        cache.clear();
    }

    /**
     * @return 物理条目数（含尚未清扫的过期条目，作为容量护栏的保守上界）
     */
    public int size() {
        return cache.size();
    }

    /**
     * @return 本次移除的过期条目数
     */
    public int purgeExpired() {
        if (ttlMillis <= 0) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int before = cache.size();
        cache.entrySet().removeIf(e -> e.getValue().expired(now));
        return before - cache.size();
    }

    /**
     * 移除未过期且满足条件的条目（过期条目顺路清扫，不计入返回值）。
     *
     * @return 被条件命中的条目数
     */
    public int removeIf(BiPredicate<K, V> predicate) {
        long now = System.currentTimeMillis();
        AtomicInteger removed = new AtomicInteger();
        cache.entrySet().removeIf(e -> {
            Entry entry = e.getValue();
            if (entry.expired(now)) {
                return true;
            }
            if (predicate.test(e.getKey(), unwrap(entry))) {
                removed.incrementAndGet();
                return true;
            }
            return false;
        });
        return removed.get();
    }

    /**
     * @return 未过期 key 的快照
     */
    public List<K> liveKeys() {
        long now = System.currentTimeMillis();
        List<K> keys = new ArrayList<>();
        for (Map.Entry<K, Entry> e : cache.entrySet()) {
            if (!e.getValue().expired(now)) {
                keys.add(e.getKey());
            }
        }
        return keys;
    }

    /**
     * @return 未过期 value 的快照（含缓存的 null 值）
     */
    public List<V> values() {
        long now = System.currentTimeMillis();
        List<V> values = new ArrayList<>();
        for (Map.Entry<K, Entry> e : cache.entrySet()) {
            Entry entry = e.getValue();
            if (!entry.expired(now)) {
                values.add(unwrap(entry));
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private V unwrap(Entry entry) {
        return entry.value == NULL_SENTINEL ? null : (V) entry.value;
    }

    private static final class Entry {
        private final Object value;
        private final long expireAt;

        private Entry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        private boolean expired(long now) {
            return now > expireAt;
        }
    }
}
