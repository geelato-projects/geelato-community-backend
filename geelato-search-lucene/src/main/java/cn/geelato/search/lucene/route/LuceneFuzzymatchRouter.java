package cn.geelato.search.lucene.route;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.spi.MqlFuzzymatchRouter;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.parser.FuzzymatchSupport;
import cn.geelato.search.api.SearchQuery;
import cn.geelato.search.api.SearchResult;
import cn.geelato.search.lucene.config.GeelatoSearchProperties;
import cn.geelato.search.lucene.config.GeelatoSearchProperties.DomainConfig;
import cn.geelato.search.lucene.engine.LuceneSearchEngine;
import cn.geelato.search.lucene.sync.SearchDomainRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * fuzzymatch → Lucene 路由器。
 *
 * <p><b>路由形态</b>：
 * <ul>
 *   <li>顶层单条件（and 语境的独立 {@code fuzzymatch|gt:0}）：逐条件独立判定与检索，
 *       各条件可使用不同关键字——AND 语义由各条件独立的 id 集合保持（{@code id IN A AND id IN B}）；
 *       不可路由的单个条件保留原样走 SQL REGEXP 兜底（部分路由等价成立）。</li>
 *   <li>or 括号子组：组内全部为 fuzzymatch|gt:0 且关键字（清洗后 pattern）一致时整组替换
 *       （同关键字 × 多字段 OR 检索模型）；混合组或关键字不一致则整组保留。</li>
 * </ul>
 *
 * <p><b>等价性硬约束</b>（不满足即放弃该条件/组，原因记 info 日志）：
 * 域未完成 reindex、关键字含正则元字符（原函数按正则求值）、任一拆分词长度 &lt; 2
 * （ngram 无 1-gram）、拆词与整体 pattern 不一致（空段/词内空格）、函数列无法映射到
 * 域字段、命中超过上限（truncated，截断集合会漏单）。
 *
 * <p>租户/软删过滤：与 SQL 侧注入器语义对齐——租户取
 * {@code SessionCtx.getCurrentTenantCode()}（无会话租户时 SQL 侧同样不加过滤）；
 * delStatus 从查询条件提取（仅收窄 id 集合，最终语义由 SQL 侧既有条件保证）。
 */
@Slf4j
public class LuceneFuzzymatchRouter implements MqlFuzzymatchRouter {

    private final SearchDomainRegistry registry;
    private final LuceneSearchEngine engine;
    private final GeelatoSearchProperties properties;
    private final MetaManager metaManager = MetaManager.singleInstance();

    public LuceneFuzzymatchRouter(SearchDomainRegistry registry, LuceneSearchEngine engine,
                                  GeelatoSearchProperties properties) {
        this.registry = registry;
        this.engine = engine;
        this.properties = properties;
    }

    @Override
    public boolean route(QueryCommand command) {
        if (command == null || command.getWhere() == null || command.getEntityName() == null) {
            return false;
        }
        SearchDomainRegistry.RouteEntityMatch match = registry.resolveRouteEntity(command.getEntityName());
        if (match == null) {
            log.info("fuzzymatch 未走路由: entity={}, 原因=实体未匹配任何搜索域（检查 geelato.search.domains 的" +
                    " main-entity/route-entities 配置与启动日志'注册搜索域'）", command.getEntityName());
            return false;
        }
        DomainConfig domain = match.domain();
        if (!engine.hasReindexed(domain.getId())) {
            log.info("fuzzymatch 未走路由: entity={}, 原因=域[{}]未完成存量重建（执行 POST /api/search/reindex/{}）",
                    command.getEntityName(), domain.getId(), domain.getId());
            return false;
        }

        boolean routedAny = false;
        try {
            // 顶层单条件：逐条件独立替换（AND 语义由各条件独立 id 集合保持）
            List<FilterGroup.Filter> filters = command.getWhere().getFilters();
            for (int i = 0; i < filters.size(); i++) {
                FilterGroup.Filter filter = filters.get(i);
                if (!isFuzzymatchGt0(filter)) {
                    continue;
                }
                List<String> ids = routeSingle(command, match, filter);
                if (ids != null) {
                    filters.set(i, buildInFilter(match, ids));
                    routedAny = true;
                }
            }
            // or 子组：整组判定替换（同关键字 × 多字段 OR）
            for (FilterGroup group : command.getWhere().getChildFilterGroup()) {
                if (group.getLogic() != FilterGroup.Logic.or || group.getFilters().isEmpty()) {
                    continue;
                }
                if (!group.getFilters().stream().allMatch(this::isFuzzymatchGt0)) {
                    continue;
                }
                List<String> ids = routeGroup(command, match, group);
                if (ids != null) {
                    group.getFilters().clear();
                    group.getFilters().add(buildInFilter(match, ids));
                    routedAny = true;
                }
            }
        } catch (Exception ex) {
            // 已完成的替换等价成立予以保留；异常发生在后续条件时，未处理条件走兜底
            log.warn("fuzzymatch 路由判定失败，未处理条件回退 SQL: entity={}", command.getEntityName(), ex);
        }
        return routedAny;
    }

    // ===== 条件判定与检索 =====

    private boolean isFuzzymatchGt0(FilterGroup.Filter filter) {
        return filter.getFilterFieldType() == FilterGroup.FilterFieldType.Function
                && FuzzymatchSupport.isFuzzymatch(filter.getField())
                && filter.getOperator() == FilterGroup.Operator.gt
                && "0".equals(filter.getValue());
    }

    /** 顶层单条件：独立判定与检索，失败返回 null（原因已记日志，条件保留走兜底）。 */
    private List<String> routeSingle(QueryCommand command, SearchDomainRegistry.RouteEntityMatch match,
                                     FilterGroup.Filter filter) {
        ConditionCheck check = checkCondition(command, match, filter);
        if (check == null) {
            return null;
        }
        return searchIds(command, match, List.of(check.field()), check.terms());
    }

    /** or 子组：组内逐条件判定 + 关键字一致 + 多字段 OR 检索，失败返回 null（整组保留走兜底）。 */
    private List<String> routeGroup(QueryCommand command, SearchDomainRegistry.RouteEntityMatch match,
                                    FilterGroup group) {
        Set<String> fields = new LinkedHashSet<>();
        String unifiedPattern = null;
        List<String> terms = null;
        for (FilterGroup.Filter filter : group.getFilters()) {
            ConditionCheck check = checkCondition(command, match, filter);
            if (check == null) {
                return null;
            }
            if (unifiedPattern == null) {
                unifiedPattern = check.pattern();
                terms = check.terms();
            } else if (!unifiedPattern.equals(check.pattern())) {
                // 跨关键字混合：检索模型（同关键字×多字段）无法等价表达
                log.info("fuzzymatch 未走路由: entity={}, 原因=组内关键字不一致（跨关键字混合无法等价表达）",
                        command.getEntityName());
                return null;
            }
            fields.add(check.field());
        }
        return searchIds(command, match, new ArrayList<>(fields), terms);
    }

    /** 单条件的可路由性判定：列映射 + 关键字边界，通过返回字段/拆词/pattern。 */
    private ConditionCheck checkCondition(QueryCommand command, SearchDomainRegistry.RouteEntityMatch match,
                                          FilterGroup.Filter filter) {
        String[] ps = FuzzymatchSupport.parseParams(filter.getField());
        if (ps == null) {
            log.info("fuzzymatch 未走路由: entity={}, 原因=函数参数不可解析（field={}）",
                    command.getEntityName(), filter.getField());
            return null;
        }
        // 括号组内函数条件可能是未归一的 $entity.field / $self.field 形态，先解析为实际列名
        String column = FuzzymatchSupport.resolveColumn(ps[0], match.entityName());
        if (column == null) {
            log.info("fuzzymatch 未走路由: entity={}, 原因=函数列无法解析（param={}，检查实体元数据）",
                    command.getEntityName(), ps[0]);
            return null;
        }
        String field = mapColumnToField(match, column);
        if (field == null) {
            log.info("fuzzymatch 未走路由: entity={}, 原因=函数列[{}]不属于域[{}]字段（检查 geelato.search.domains 的 fields/children 配置）",
                    command.getEntityName(), column, match.domain().getId());
            return null;
        }
        // 关键字可路由性：无正则元字符、拆分词长度 ≥2、拆分与整体 pattern 语义一致
        //（一致性拦截：残留空段/词内空格等拆词检索无法等价表达函数行为的场景）
        if (FuzzymatchSupport.containsRegexMeta(ps[1])) {
            log.info("fuzzymatch 未走路由: entity={}, 原因=关键字含正则元字符（保留 SQL 正则语义）",
                    command.getEntityName());
            return null;
        }
        List<String> split = FuzzymatchSupport.splitTerms(ps[1]);
        if (split.isEmpty() || split.stream().anyMatch(t -> t.length() < 2)) {
            log.info("fuzzymatch 未走路由: entity={}, 原因=关键字拆分词为空或长度<2（ngram 无 1-gram）",
                    command.getEntityName());
            return null;
        }
        String pattern = FuzzymatchSupport.buildRegexPattern(ps[1]);
        String rebuilt = String.join("|",
                split.stream().map(FuzzymatchSupport::buildRegexPattern).toList());
        if (!rebuilt.equals(pattern)) {
            log.info("fuzzymatch 未走路由: entity={}, 原因=关键字拆分与整体 pattern 不一致（空段/词内空格）",
                    command.getEntityName());
            return null;
        }
        return new ConditionCheck(field, split, pattern);
    }

    /** 组装检索并返回 id 集合；命中超上限返回 null（截断集合会漏单，不得使用）。 */
    private List<String> searchIds(QueryCommand command, SearchDomainRegistry.RouteEntityMatch match,
                                   List<String> fields, List<String> terms) {
        SearchQuery query = new SearchQuery();
        query.setTerms(terms);
        query.setFields(fields);
        query.setTenantCode(SessionCtx.getCurrentTenantCode());
        query.setDelStatus(extractEqValue(command.getWhere(), "delStatus"));
        query.setMaxIds(properties.getMaxIds());

        SearchResult result = engine.search(match.domain().getId(), query);
        if (result.isTruncated()) {
            log.info("fuzzymatch 未走路由: entity={}, 原因=命中超过上限({})（截断集合会漏单）",
                    command.getEntityName(), properties.getMaxIds());
            return null;
        }
        return result.getIds();
    }

    /** 构造 id IN 替换条件；空结果用不可命中值（等价恒假，同原语义）。 */
    private FilterGroup.Filter buildInFilter(SearchDomainRegistry.RouteEntityMatch match, List<String> ids) {
        String pkField = resolvePkFieldName(match);
        if (pkField == null) {
            throw new IllegalStateException("路由实体主键不可解析: " + match.entityName());
        }
        List<String> values = ids.isEmpty() ? List.of("__search_no_match__") : ids;
        return new FilterGroup.Filter(pkField, FilterGroup.Operator.in, toJsonArray(values));
    }

    /** 视图列名/主实体列名 → 域字段名。 */
    private String mapColumnToField(SearchDomainRegistry.RouteEntityMatch match, String column) {
        if (match.viewConfig() != null) {
            String field = match.viewConfig().getColumnMap().get(column);
            if (field == null) {
                return null;
            }
            DomainConfig domain = match.domain();
            return domain.getFields().contains(field) || childFieldsContains(domain, field) ? field : null;
        }
        EntityMeta em = metaManager.getByEntityName(match.domain().getMainEntity());
        if (em == null || em.getFieldMetaByColumn(column) == null) {
            return null;
        }
        String field = em.getFieldMetaByColumn(column).getFieldName();
        DomainConfig domain = match.domain();
        return domain.getFields().contains(field) || childFieldsContains(domain, field) ? field : null;
    }

    private boolean childFieldsContains(DomainConfig domain, String field) {
        return domain.getChildren().stream().anyMatch(c -> c.getFields().contains(field));
    }

    /** 路由实体的主键字段名（in 条件用）。 */
    private String resolvePkFieldName(SearchDomainRegistry.RouteEntityMatch match) {
        if (match.viewConfig() != null) {
            String pkColumn = match.viewConfig().getPkColumn();
            if ("id".equals(pkColumn)) {
                return "id";
            }
            EntityMeta em = metaManager.getByEntityName(match.entityName());
            return em != null && em.getFieldMetaByColumn(pkColumn) != null
                    ? em.getFieldMetaByColumn(pkColumn).getFieldName() : null;
        }
        return match.domain().getPkField();
    }

    /** 值数组字符串（["a","b"] 格式，与 Filter#getValueAsArray 的 JSON 解析分支对齐）。 */
    static String toJsonArray(List<String> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(ids.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }

    private String extractEqValue(FilterGroup where, String fieldName) {
        for (FilterGroup.Filter filter : where.getFilters()) {
            if (fieldName.equals(filter.getField())
                    && filter.getOperator() == FilterGroup.Operator.eq
                    && filter.getValue() != null) {
                return filter.getValue();
            }
        }
        return null;
    }

    /** 单条件可路由性判定结果。 */
    private record ConditionCheck(String field, List<String> terms, String pattern) {
    }
}
