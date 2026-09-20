package cn.geelato.mqltest.mql;

import cn.geelato.core.meta.model.parser.FuzzymatchSupport;
import cn.geelato.mqltest.support.MysqlContainerITSupport;
import cn.geelato.search.api.SearchDocument;
import cn.geelato.search.api.SearchEngine;
import cn.geelato.search.api.SearchQuery;
import cn.geelato.search.api.SearchResult;
import cn.geelato.search.lucene.engine.LuceneSearchEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * fuzzymatch 三形态等价性 IT（需 Docker，MySQL 8 容器）：
 * <ol>
 *   <li>原函数：gfn_fuzzymatch(order_no,'kw') > '0'（基准，函数体与生产一致）</li>
 *   <li>REGEXP 等价改写：order_no &lt;&gt; '' AND order_no REGEXP ?（pattern 由 FuzzymatchSupport 生成）</li>
 *   <li>Lucene 路由：字面量 contains（仅无正则元字符且词长 ≥2 的关键字参与，与路由判定一致）</li>
 * </ol>
 * 同一数据集与关键字矩阵下三形态命中集合必须完全一致，任一不一致即失败（等价性硬验证）。
 * Docker 不可用时跳过。
 */
@DisplayName("fuzzymatch：原函数/REGEXP改写/Lucene 三形态等价性")
class FuzzymatchEquivalenceIT extends MysqlContainerITSupport {

    private static final String[] ORDER_NOS = {
            "SO274935136",          // 1 精确命中
            "mbl-181BY26O0407",     // 2 分隔符+大小写
            "HBL000001",            // 3 前缀
            "123456789",            // 4 纯数字
            "",                     // 5 空串（短路场景）
            null,                   // 6 NULL（短路场景）
            "三星集装箱SO999",       // 7 中文混合
            "SO111",                // 8 多词
            "SO222",                // 9 多词
            "PE264799-OOCL"         // 10 混合
    };

    private static SearchEngine engine;

    @BeforeAll
    static void prepare() {
        startMysql();
        engine = new LuceneSearchEngine("target/search-equiv-it", 50000);
        jdbcTemplate.update("DELETE FROM mql_test_order");
        List<SearchDocument> docs = new ArrayList<>();
        for (int i = 0; i < ORDER_NOS.length; i++) {
            String orderNo = ORDER_NOS[i];
            jdbcTemplate.update("INSERT INTO mql_test_order (id, order_no, status) VALUES (?, ?, 'done')",
                    i + 1, orderNo);
            if (orderNo != null) {
                SearchDocument doc = new SearchDocument(String.valueOf(i + 1));
                if (!orderNo.isEmpty()) {
                    doc.addFieldValue("orderNo", orderNo);
                }
                docs.add(doc);
            }
        }
        engine.upsert("order", docs);
    }

    @AfterAll
    static void closeEngine() {
        if (engine instanceof LuceneSearchEngine l) {
            l.close();
        }
    }

    /** 形态一：原函数（基准）。 */
    private Set<String> byFunction(String keyword) {
        String sql = "SELECT id FROM mql_test_order WHERE gfn_fuzzymatch(order_no, ?) > '0'";
        return new HashSet<>(jdbcTemplate.queryForList(sql, String.class, keyword));
    }

    /** 形态二：REGEXP 等价改写。 */
    private Set<String> byRegexp(String keyword) {
        String pattern = FuzzymatchSupport.buildRegexPattern(keyword);
        String sql = "SELECT id FROM mql_test_order WHERE (order_no <> '' AND order_no REGEXP ?)";
        return new HashSet<>(jdbcTemplate.queryForList(sql, String.class, pattern));
    }

    /**
     * 形态三：Lucene 字面量 contains。仅对"路由可接"的关键字断言：
     * 无正则元字符、清洗拆分后各词长度 ≥2、逐词 pattern 重拼与整体 pattern 一致
     *（与 LuceneFuzzymatchRouter 的路由判定完全相同）。
     */
    private Set<String> byLucene(String keyword) {
        SearchQuery q = new SearchQuery();
        q.setTerms(FuzzymatchSupport.splitTerms(keyword));
        q.setFields(List.of("orderNo"));
        SearchResult r = engine.search("order", q);
        return new HashSet<>(r.getIds());
    }

    private static boolean routable(String keyword) {
        if (FuzzymatchSupport.containsRegexMeta(keyword)) {
            return false;
        }
        List<String> split = FuzzymatchSupport.splitTerms(keyword);
        if (split.isEmpty() || split.stream().anyMatch(t -> t.length() < 2)) {
            return false;
        }
        String pattern = FuzzymatchSupport.buildRegexPattern(keyword);
        String rebuilt = String.join("|",
                split.stream().map(FuzzymatchSupport::buildRegexPattern).toList());
        return rebuilt.equals(pattern);
    }

    private void assertThreeFormsConsistent(String keyword) {
        Set<String> fn = byFunction(keyword);
        Set<String> regexp = byRegexp(keyword);
        assertEquals(fn, regexp, "REGEXP 改写与原函数不一致: keyword=" + keyword);
        if (routable(keyword)) {
            Set<String> lucene = byLucene(keyword);
            assertEquals(fn, lucene, "Lucene 路由与原函数不一致: keyword=" + keyword);
        }
    }

    @Test
    @DisplayName("边界关键字矩阵：三形态一致")
    void boundaryMatrix() {
        assumeTrue(mysql != null && mysql.isRunning(), "MySQL 容器未启动");
        String[] keywords = {
                "274935136",              // 命中 1
                "181by26o0407",           // 大小写不敏感命中 2
                "HBL",                    // 前缀命中 3
                "789",                    // 后缀命中 4
                "123456789",              // 全量命中 4
                "so274935136",            // 大小写
                "集装箱",                  // 中文命中 7
                "SO111,SO222",            // 多词 OR 命中 8,9
                "SO111，SO222",           // 全角逗号多词
                " SO111 , SO222 ",        // 词内空格（不可路由，仅对照函数与 REGEXP）
                "SO999",                  // 命中 7
                "ZZZ999",                 // 无命中
                "",                       // 空关键字：函数恒 0
                "   ",                    // 纯空格：函数恒 0
                ",,",                     // 清洗后单逗号：空正则分支恒真（非空行全命中）
                "a,,b",                   // 清洗合并后双词
                "a,,,b",                  // 残留空段：空正则分支恒真
                "PE264799",               // 命中 10
                "pe264799-oocl"           // 命中 10
        };
        for (String keyword : keywords) {
            assertThreeFormsConsistent(keyword);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB+", "a|b", "123.*", "(SO)"})
    @DisplayName("正则元字符关键字：函数与 REGEXP 改写一致（保留正则语义，不路由）")
    void regexMetaKeywords(String keyword) {
        assumeTrue(mysql != null && mysql.isRunning(), "MySQL 容器未启动");
        assertEquals(byFunction(keyword), byRegexp(keyword),
                "元字符关键字 REGEXP 改写与原函数不一致: " + keyword);
    }
}
