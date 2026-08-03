package cn.geelato.web.platform.plugin;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件开关 JSON 文件读写工具。
 * <p>负责单个 JSON 文档（平台级或租户级）的读取（带 mtime 失效的本地缓存）与
 * 读-改-写（跨进程文件锁）。多节点部署共享同一文件时，写靠 {@link FileLock} 互斥，
 * 读靠本地缓存 + 文件 mtime 失效，保证最终一致。</p>
 *
 * @author geelato
 */
@Slf4j
@Component
public class PluginStatusJsonStore {

    /** 缓存条目：文档内容 + 读取时的 mtime。 */
    private final ConcurrentHashMap<Path, CacheEntry> cache = new ConcurrentHashMap<>();

    private final PluginConfigurationProperties properties;

    public PluginStatusJsonStore(PluginConfigurationProperties properties) {
        this.properties = properties;
    }

    /**
     * 读取文档（带本地缓存，mtime 变化或超过 TTL 则重读）。
     * 文件不存在时返回 null（由调用方决定初始化策略）。
     */
    public PluginStatusDoc read(Path file) {
        if (!Files.exists(file)) {
            cache.remove(file);
            return null;
        }
        CacheEntry entry = cache.get(file);
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            if (entry != null && entry.mtime == mtime && !entry.expired(properties.getCacheTtlSeconds())) {
                return entry.doc;
            }
            String json = Files.readString(file);
            PluginStatusDoc doc = parse(json);
            cache.put(file, new CacheEntry(doc, mtime));
            return doc;
        } catch (IOException e) {
            log.warn("读取插件开关文件失败，使用缓存: {}", file, e);
            return entry != null ? entry.doc : null;
        }
    }

    /**
     * 读取已启用插件 id 集合；文件不存在或读取失败返回空集。
     */
    public Set<String> readEnabled(Path file) {
        PluginStatusDoc doc = read(file);
        return doc == null || doc.getEnabled() == null ? Collections.emptySet() : Set.copyOf(doc.getEnabled());
    }

    /**
     * 读-改-写：在文件锁保护下加载文档、应用变更、回写。
     *
     * @param file       目标文件
     * @param mutator    变更函数（接收当前文档——不存在则为 null——返回待持久化的文档）
     * @param updatedBy  操作人（排查用）
     * @return 持久化后的文档
     */
    public PluginStatusDoc write(Path file, java.util.function.Function<PluginStatusDoc, PluginStatusDoc> mutator, String updatedBy) {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("无法创建插件开关目录: " + file.getParent(), e);
        }
        // FileLock 跨进程互斥（共享卷场景下各节点 JVM 互斥）
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            PluginStatusDoc current = null;
            if (Files.exists(file)) {
                String json = readAll(channel);
                if (json != null && !json.isBlank()) {
                    current = parse(json);
                }
            }
            PluginStatusDoc next = mutator.apply(current);
            if (next == null) {
                next = new PluginStatusDoc();
            }
            next.setUpdatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            next.setUpdatedBy(updatedBy);
            String out = JSON.toJSONString(next, JSONWriter.Feature.PrettyFormat);
            // 先清空再写
            channel.truncate(0);
            channel.write(java.nio.ByteBuffer.wrap(out.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            channel.force(false);
            // 更新缓存与 mtime
            try {
                long mtime = Files.getLastModifiedTime(file).toMillis();
                cache.put(file, new CacheEntry(next, mtime));
            } catch (IOException ignored) {
            }
            return next;
        } catch (IOException e) {
            throw new IllegalStateException("写入插件开关文件失败: " + file, e);
        }
    }

    /**
     * 直接覆盖写入（用于初始化场景）。
     */
    public PluginStatusDoc overwrite(Path file, PluginStatusDoc doc, String updatedBy) {
        return write(file, current -> doc, updatedBy);
    }

    /**
     * 失效指定文件（或全部）的本地缓存。
     */
    public void invalidate(Path file) {
        if (file == null) {
            cache.clear();
        } else {
            cache.remove(file);
        }
    }

    private PluginStatusDoc parse(String json) {
        if (json == null || json.isBlank()) {
            return new PluginStatusDoc();
        }
        PluginStatusDoc doc = JSON.parseObject(json, PluginStatusDoc.class);
        return doc != null ? doc : new PluginStatusDoc();
    }

    private String readAll(FileChannel channel) throws IOException {
        if (channel.size() == 0) {
            return "";
        }
        channel.position(0);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) channel.size());
        channel.read(buffer);
        return new String(buffer.array(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static class CacheEntry {
        final PluginStatusDoc doc;
        final long mtime;
        final long loadedAt;

        CacheEntry(PluginStatusDoc doc, long mtime) {
            this.doc = doc;
            this.mtime = mtime;
            this.loadedAt = System.currentTimeMillis();
        }

        boolean expired(long ttlSeconds) {
            return ttlSeconds > 0 && (System.currentTimeMillis() - loadedAt) >= ttlSeconds * 1000L;
        }
    }

    // 供 List 转换的便捷方法
    public List<String> toList(Set<String> set) {
        return set == null ? List.of() : List.copyOf(set);
    }
}
