package cn.geelato.search.api;

import java.util.Collection;
import java.util.List;

/**
 * 搜索引擎端口。平台核心（MQL 路由、索引同步）仅依赖本接口；
 * 实现可插拔：嵌入式 Lucene（默认）或独立 Elasticsearch 等。
 *
 * <p>数据源始终是数据库——引擎只承接检索谓词（fuzzymatch 多编号模糊检索），
 * join、分页、排序、数据权限仍由 SQL 完成。全量重建（reindex）的数据拉取与编排
 * 属于接入层职责，不在本端口内。
 *
 * <p><b>检索语义契约</b>（TCK 契约测试固化，任何实现必须通过）：
 * <ul>
 *   <li>{@link #search} 为多关键词 OR × 多字段任意位置包含（字面量，非正则、不分词归约）；</li>
 *   <li>命中超过 {@link SearchQuery#getMaxIds()} 时返回 {@code truncated=true}，不得静默截断；</li>
 *   <li>terms 为空列表时返回空结果；</li>
 *   <li>过滤字段（tenantCode/delStatus/appId）为 null 表示不过滤，非 null 必须精确匹配；</li>
 *   <li>{@link #upsert} 对同一文档 id 幂等（整文档替换），{@link #delete} 对不存在 id 幂等。</li>
 * </ul>
 */
public interface SearchEngine {

    /** 实现名（如 lucene、elasticsearch），用于日志与诊断。 */
    String name();

    SearchEngineCapabilities capabilities();

    /**
     * 写入或替换文档（按文档 id 整文档替换，幂等）。
     *
     * @param domainId  搜索域 id
     * @param documents 文档列表
     */
    void upsert(String domainId, List<SearchDocument> documents);

    /**
     * 按文档 id 删除（幂等）。
     */
    void delete(String domainId, Collection<String> docIds);

    /**
     * 检索命中文档 id 集合。
     */
    SearchResult search(String domainId, SearchQuery query);

    SearchEngineHealth health();
}
