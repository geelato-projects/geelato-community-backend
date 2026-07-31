package cn.geelato.web.platform.audit.service;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.orm.Dao;
import cn.geelato.meta.Dict;
import cn.geelato.meta.DictItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字典翻译解析器（带本地缓存）。
 *
 * <p>平台字典为两层结构：{@link Dict}（dictCode/dictName）+ {@link DictItem}（dictId/itemCode/itemName）。
 * 字段通过元数据 {@code EntityMeta.dictDataSourceMap}（key=fieldName, value=DictDataSource.group=dictCode）
 * 绑定到字典组。本解析器按 dictCode + itemCode 解析出中文 itemName。
 *
 * <p>因 {@code DictItemService} 无现成按 dictCode+itemCode 查询方法，这里基于 {@code Dao} 自行查询并缓存
 * 整组字典项（key=dictCode），缓存 5 分钟过期，避免逐字段回查库。
 */
@Slf4j
@Component
public class DictDisplayResolver {

    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private final Dao dao;

    /** dictCode -> (加载时间戳, itemCode -> itemName) */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public DictDisplayResolver(@Qualifier("primaryDao") Dao dao) {
        this.dao = dao;
    }

    /**
     * 翻译字典值。
     *
     * @param dictCode 字典组编码
     * @param itemCode 字典项编码（即字段的原始值）
     * @return 对应的 itemName；查不到或入参为空则返回 null
     */
    public String resolve(String dictCode, String itemCode) {
        if (!StringUtils.hasText(dictCode) || !StringUtils.hasText(itemCode)) {
            return null;
        }
        Map<String, String> items = loadGroup(dictCode);
        return items.get(itemCode);
    }

    /** 加载某字典组的 itemCode -> itemName 映射（带缓存）。 */
    @SuppressWarnings("unchecked")
    private Map<String, String> loadGroup(String dictCode) {
        CacheEntry cached = cache.get(dictCode);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt < CACHE_TTL_MS) {
            return cached.items;
        }
        try {
            // 先按 dictCode 查 Dict 拿 dictId
            FilterGroup dictFg = new FilterGroup();
            dictFg.addFilter("dictCode", FilterGroup.Operator.eq, dictCode);
            List<Dict> dicts = dao.queryList(Dict.class, dictFg, "");
            if (dicts == null || dicts.isEmpty()) {
                cache.put(dictCode, new CacheEntry(now, Collections.emptyMap()));
                return Collections.emptyMap();
            }
            String dictId = dicts.get(0).getId();

            // 再按 dictId 查启用的 DictItem
            FilterGroup itemFg = new FilterGroup();
            itemFg.addFilter("dictId", FilterGroup.Operator.eq, dictId);
            itemFg.addFilter("enableStatus", FilterGroup.Operator.eq, "1");
            List<DictItem> items = dao.queryList(DictItem.class, itemFg, "");
            Map<String, String> map = new HashMap<>();
            if (items != null) {
                for (DictItem it : items) {
                    if (StringUtils.hasText(it.getItemCode())) {
                        map.put(it.getItemCode(), it.getItemName());
                    }
                }
            }
            cache.put(dictCode, new CacheEntry(now, map));
            return map;
        } catch (Exception e) {
            log.warn("审计字典翻译加载失败 dictCode={}, 降级为不翻译", dictCode, e);
            return Collections.emptyMap();
        }
    }

    /** 失效缓存（字典维护后可调用）。 */
    public void evict(String dictCode) {
        cache.remove(dictCode);
    }

    private static class CacheEntry {
        final long loadedAt;
        final Map<String, String> items;

        CacheEntry(long loadedAt, Map<String, String> items) {
            this.loadedAt = loadedAt;
            this.items = items;
        }
    }
}
