package cn.geelato.search.lucene.route;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.search.api.SearchDocument;
import cn.geelato.search.lucene.config.GeelatoSearchProperties;
import cn.geelato.search.lucene.config.GeelatoSearchProperties.DomainConfig;
import cn.geelato.search.lucene.engine.LuceneSearchEngine;
import cn.geelato.search.lucene.sync.SearchDomainRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由器单测：顶层单条件独立路由（含 AND 多条件各自独立集合的等价性回归）、
 * 部分路由（列不在域的条件保留）、or 组整组替换、元字符关键字保留。
 */
class LuceneFuzzymatchRouterTest {

    private static final String ROOT = "target/search-route-test";
    private static final String ENTITY = "srch_route_order";

    private static LuceneSearchEngine engine;
    private static LuceneFuzzymatchRouter router;
    private static SearchDomainRegistry registry;

    @BeforeAll
    static void setUp() {
        MetaManager mm = MetaManager.singleInstance();
        mm.parseOne(TableMeta.class);
        mm.parseOne(SrchRouteOrder.class);

        engine = new LuceneSearchEngine(ROOT, 50000);
        GeelatoSearchProperties properties = new GeelatoSearchProperties();
        DomainConfig domain = new DomainConfig();
        domain.setId("order");
        domain.setMainEntity(ENTITY);
        domain.setPkField("id");
        domain.setFields(List.of("soNo", "mblNo"));
        registry = new SearchDomainRegistry();
        registry.register(domain);
        router = new LuceneFuzzymatchRouter(registry, engine, properties);
        engine.markReindexed("order");

        SearchDocument d1 = new SearchDocument("1");
        d1.addFieldValue("soNo", "SOAAA111");
        SearchDocument d2 = new SearchDocument("2");
        d2.addFieldValue("soNo", "SOBBB222");
        d2.addFieldValue("mblNo", "MBLAAA333");
        SearchDocument d3 = new SearchDocument("3");
        d3.addFieldValue("mblNo", "MBLBBB444");
        engine.upsert("order", List.of(d1, d2, d3));
    }

    @AfterAll
    static void tearDown() {
        if (engine != null) {
            engine.close();
        }
        java.nio.file.Path root = java.nio.file.Path.of(ROOT);
        if (java.nio.file.Files.exists(root)) {
            try (var walk = java.nio.file.Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        java.nio.file.Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    private static QueryCommand command(FilterGroup where) {
        QueryCommand command = new QueryCommand();
        command.setEntityName(ENTITY);
        command.setWhere(where);
        return command;
    }

    private static FilterGroup.Filter fuzzymatchFilter(String field, String keyword) {
        // 顶层归一后的形态（JsonTextQueryParser 经 getMysqlFunction）
        return new FilterGroup.Filter(
                "gfn_fuzzymatch(" + field + ",'" + keyword + "')", FilterGroup.Operator.gt, "0");
    }

    private static List<Object> inValues(FilterGroup.Filter filter) {
        return Arrays.asList(filter.getValueAsArray());
    }

    @Test
    void topLevelSingleConditionReplacedWithItsOwnIdSet() {
        FilterGroup where = new FilterGroup();
        where.addFilter(fuzzymatchFilter("so_no", "SOAAA111"));
        QueryCommand command = command(where);

        assertTrue(router.route(command));
        FilterGroup.Filter replaced = command.getWhere().getFilters().get(0);
        assertEquals(FilterGroup.Operator.in, replaced.getOperator());
        assertEquals(List.of("1"), inValues(replaced));
    }

    @Test
    void multipleTopLevelAndConditionsUseIndependentSets() {
        // 等价性回归：两个 AND 条件必须各自独立 id 集合，不得共用同一集合（否则 AND 被变成 OR）
        FilterGroup where = new FilterGroup();
        where.addFilter(fuzzymatchFilter("so_no", "SOAAA111"));   // 命中 doc1
        where.addFilter(fuzzymatchFilter("mbl_no", "MBLBBB444")); // 命中 doc3
        QueryCommand command = command(where);

        assertTrue(router.route(command));
        List<FilterGroup.Filter> filters = command.getWhere().getFilters();
        assertEquals(FilterGroup.Operator.in, filters.get(0).getOperator());
        assertEquals(FilterGroup.Operator.in, filters.get(1).getOperator());
        assertEquals(List.of("1"), inValues(filters.get(0)));
        assertEquals(List.of("3"), inValues(filters.get(1)));
    }

    @Test
    void columnNotInDomainKeepsConditionOthersStillRouted() {
        // 部分路由：other_no 不属于域字段 → 该条件保留（走 REGEXP 兜底），其余条件照常替换
        FilterGroup where = new FilterGroup();
        where.addFilter(fuzzymatchFilter("other_no", "SOAAA111"));
        where.addFilter(fuzzymatchFilter("so_no", "SOAAA111"));
        QueryCommand command = command(where);

        assertTrue(router.route(command));
        List<FilterGroup.Filter> filters = command.getWhere().getFilters();
        assertTrue(filters.get(0).getField().contains("gfn_fuzzymatch"),
                "不在域字段的条件应保留原样: " + filters.get(0).getField());
        assertEquals(FilterGroup.Operator.in, filters.get(1).getOperator());
        assertEquals(List.of("1"), inValues(filters.get(1)));
    }

    @Test
    void orGroupReplacedAsWholeWithUnifiedKeyword() {
        FilterGroup where = new FilterGroup();
        FilterGroup orGroup = new FilterGroup(FilterGroup.Logic.or);
        // @b 组内未归一形态（$self.field）
        orGroup.addFilter(new FilterGroup.Filter(
                "fuzzymatch($self.soNo,'AAA')", FilterGroup.Operator.gt, "0"));
        orGroup.addFilter(new FilterGroup.Filter(
                "fuzzymatch($self.mblNo,'AAA')", FilterGroup.Operator.gt, "0"));
        where.getChildFilterGroup().add(orGroup);
        QueryCommand command = command(where);

        assertTrue(router.route(command));
        assertEquals(1, orGroup.getFilters().size());
        FilterGroup.Filter replaced = orGroup.getFilters().get(0);
        assertEquals(FilterGroup.Operator.in, replaced.getOperator());
        // so~AAA 命中 doc1、mbl~AAA 命中 doc2
        assertEquals(List.of("1", "2"), inValues(replaced));
    }

    @Test
    void keywordWithRegexMetaIsKeptUnrouted() {
        FilterGroup where = new FilterGroup();
        where.addFilter(fuzzymatchFilter("so_no", "AB+"));
        QueryCommand command = command(where);

        assertFalse(router.route(command));
        FilterGroup.Filter kept = command.getWhere().getFilters().get(0);
        assertTrue(kept.getField().contains("gfn_fuzzymatch"));
    }
}
