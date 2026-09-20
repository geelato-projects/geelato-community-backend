package cn.geelato.search.lucene.web;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.search.api.SearchEngineHealth;
import cn.geelato.search.api.SearchQuery;
import cn.geelato.search.api.SearchResult;
import cn.geelato.search.lucene.compensate.SearchSyncFailStore;
import cn.geelato.search.lucene.config.GeelatoSearchProperties;
import cn.geelato.search.lucene.config.GeelatoSearchProperties.DomainConfig;
import cn.geelato.search.lucene.engine.LuceneSearchEngine;
import cn.geelato.search.lucene.reindex.SearchReindexService;
import cn.geelato.search.lucene.reindex.SearchReindexService.ReindexProgress;
import cn.geelato.search.lucene.sync.SearchDomainRegistry;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索域管理端点：存量重建（reindex）、进度、域状态与补偿队列。
 *
 * <p>新启用域或索引数据可疑时，先 POST /api/search/reindex/{domainId} 触发存量重建，
 * 轮询 GET /api/search/reindex/{domainId}/progress 至 finished=true 后，
 * 该域的 fuzzymatch 条件才开始路由到索引（reindexed 水位门控）。
 */
@Slf4j
@ApiRuntimeRestController("/search")
public class SearchManageController {

    private final SearchDomainRegistry registry;
    private final SearchReindexService reindexService;
    private final LuceneSearchEngine engine;
    private final SearchSyncFailStore failStore;
    private final GeelatoSearchProperties properties;

    public SearchManageController(SearchDomainRegistry registry,
                                  SearchReindexService reindexService,
                                  LuceneSearchEngine engine,
                                  SearchSyncFailStore failStore,
                                  GeelatoSearchProperties properties) {
        this.registry = registry;
        this.reindexService = reindexService;
        this.engine = engine;
        this.failStore = failStore;
        this.properties = properties;
    }

    /**
     * 触发存量重建（异步，立即返回进度对象引用，轮询 progress 观察推进）。
     * 同域重复触发幂等：进行中直接返回当前进度，不重复执行。
     */
    @RequestMapping(value = "/reindex/{domainId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<ReindexProgress> reindex(@PathVariable String domainId) {
        try {
            return ApiResult.success(reindexService.reindexAsync(domainId));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    /** 重建进度（done 已写入文档数 / skipped 主行不可读跳过数 / finished / error）。 */
    @GetMapping("/reindex/{domainId}/progress")
    public ApiResult<ReindexProgress> progress(@PathVariable String domainId) {
        ReindexProgress progress = reindexService.getProgress(domainId);
        if (progress == null) {
            return ApiResult.fail("该域从未执行过重建: " + domainId);
        }
        return ApiResult.success(progress);
    }

    /** 域清单与状态：是否已 reindex（未 reindex 的域不参与路由）、补偿队列计数。 */
    @GetMapping("/domains")
    public ApiResult<List<Map<String, Object>>> domains() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DomainConfig domain : registry.allDomains()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", domain.getId());
            item.put("mainEntity", domain.getMainEntity());
            item.put("fields", domain.getFields());
            item.put("children", domain.getChildren().size());
            item.put("routeEntities", domain.getRouteEntities().keySet());
            item.put("reindexed", engine.hasReindexed(domain.getId()));
            item.put("reindexRunning", reindexService.isRunning(domain.getId()));
            item.put("indexedDocs", engine.hasReindexed(domain.getId())
                    ? engine.countDocuments(domain.getId()) : 0);
            list.add(item);
        }
        return ApiResult.success(list);
    }

    /** 引擎健康与补偿队列（DEAD > 0 需人工介入）。 */
    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        SearchEngineHealth health = engine.health();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", health.getStatus().name());
        data.put("detail", health.getDetail());
        data.put("compensationPending", failStore.countByStatus("PENDING"));
        data.put("compensationDead", failStore.countByStatus("DEAD"));
        return ApiResult.success(data);
    }

    /**
     * 检索分层调试：同一关键字在「无过滤 / 仅租户 / 仅软删 / 路由真实组合」四层的命中数与样例 id，
     * 并窥视前几个文档的实际存储值（tenantCode/delStatus/各编号字段）——
     * 用于定位"路由替换了但结果为空"属于文档数据缺失还是过滤值不匹配。
     * 注意：文档值窥视需要索引以 Store.YES 构建（2026-09-14 起），旧索引需重新 reindex。
     */
    @GetMapping("/debug/{domainId}")
    public ApiResult<Map<String, Object>> debug(@PathVariable String domainId,
                                                @RequestParam String keyword,
                                                @RequestParam String field,
                                                @RequestParam(required = false) String tenant,
                                                @RequestParam(required = false) String delStatus) {
        DomainConfig domain = registry.byId(domainId);
        if (domain == null) {
            return ApiResult.fail("搜索域不存在: " + domainId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("domain", domainId);
        data.put("keyword", keyword);
        data.put("field", field);

        SearchResult noFilter = engine.search(domainId,
                debugQuery(keyword, field, null, null));
        data.put("noFilter", debugHit(noFilter));
        SearchResult withTenant = engine.search(domainId,
                debugQuery(keyword, field, tenant, null));
        data.put("withTenant(" + tenant + ")", debugHit(withTenant));
        SearchResult withDel = engine.search(domainId,
                debugQuery(keyword, field, null, delStatus));
        data.put("withDelStatus(" + delStatus + ")", debugHit(withDel));
        SearchResult routed = engine.search(domainId,
                debugQuery(keyword, field, tenant, delStatus));
        data.put("routedLike", debugHit(routed));

        // 窥视无过滤命中的前 3 个文档实际值
        List<Map<String, List<String>>> samples = new ArrayList<>();
        for (String id : noFilter.getIds().stream().limit(3).toList()) {
            Map<String, List<String>> peek = engine.peekDocument(domainId, id);
            if (peek != null) {
                samples.add(peek);
            }
        }
        data.put("sampleDocs", samples);
        return ApiResult.success(data);
    }

    private SearchQuery debugQuery(String keyword, String field, String tenant, String delStatus) {
        SearchQuery q = new SearchQuery();
        q.setTerms(List.of(keyword));
        q.setFields(List.of(field));
        q.setTenantCode(tenant);
        q.setDelStatus(delStatus);
        q.setMaxIds(properties.getMaxIds());
        return q;
    }

    private Map<String, Object> debugHit(SearchResult result) {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("count", result.getIds().size());
        hit.put("truncated", result.isTruncated());
        hit.put("sampleIds", result.getIds().stream().limit(5).toList());
        return hit;
    }
}
