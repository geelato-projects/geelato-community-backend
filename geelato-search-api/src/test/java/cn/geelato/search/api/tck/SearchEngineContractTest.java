package cn.geelato.search.api.tck;

import cn.geelato.search.api.SearchDocument;
import cn.geelato.search.api.SearchEngine;
import cn.geelato.search.api.SearchQuery;
import cn.geelato.search.api.SearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchEngine 实现契约测试套件（TCK）。
 *
 * <p>任何实现（嵌入式 Lucene、独立 ES 等）必须继承本类并全部通过，
 * 保证实现演进时检索语义不漂移。语义契约与 {@code geelato.gfn_fuzzymatch}
 * 的多词 OR 字面量 contains 对齐：
 * <ul>
 *   <li>任意位置包含（前缀/中缀/后缀），大小写不敏感（对齐平台 MySQL 默认 *_ci collation 下
 *       REGEXP 的大小写不敏感行为）；</li>
 *   <li>字面量匹配而非正则（正则元字符关键字由路由侧回退 SQL REGEXP 改写，不会进入引擎）；</li>
 *   <li>term 长度约定为 ≥ 2（长度 1 的关键词由路由侧回退，引擎行为不在此契约内）。</li>
 * </ul>
 */
public abstract class SearchEngineContractTest {

    protected static final String DOMAIN = "tck-domain";

    private SearchEngine engine;

    protected abstract SearchEngine createEngine() throws Exception;

    protected abstract void destroyEngine(SearchEngine engine) throws Exception;

    @BeforeEach
    public void setUpEngine() throws Exception {
        engine = createEngine();
        engine.delete(DOMAIN, List.of("d1", "d2", "d3", "d4", "d5", "d6"));
    }

    @AfterEach
    public void tearDownEngine() throws Exception {
        if (engine != null) {
            destroyEngine(engine);
        }
    }

    private SearchQuery query(List<String> terms, List<String> fields) {
        SearchQuery q = new SearchQuery();
        q.setTerms(terms);
        q.setFields(fields);
        return q;
    }

    private List<String> searchIds(List<String> terms, List<String> fields) {
        return engine.search(DOMAIN, query(terms, fields)).getIds();
    }

    @Test
    public void containsAnyPosition() {
        SearchDocument doc = new SearchDocument("d1");
        doc.addFieldValue("soNo", "SO123456");
        engine.upsert(DOMAIN, List.of(doc));

        assertThat(searchIds(List.of("123"), List.of("soNo"))).containsExactly("d1");
        assertThat(searchIds(List.of("SO1"), List.of("soNo"))).containsExactly("d1");
        assertThat(searchIds(List.of("456"), List.of("soNo"))).containsExactly("d1");
        assertThat(searchIds(List.of("SO123456"), List.of("soNo"))).containsExactly("d1");
        assertThat(searchIds(List.of("S099"), List.of("soNo"))).isEmpty();
    }

    @Test
    public void caseInsensitiveAlignsWithCiCollation() {
        SearchDocument doc = new SearchDocument("d1");
        doc.addFieldValue("soNo", "ABC123");
        engine.upsert(DOMAIN, List.of(doc));

        assertThat(searchIds(List.of("abc123"), List.of("soNo"))).containsExactly("d1");
        assertThat(searchIds(List.of("AbC"), List.of("soNo"))).containsExactly("d1");
    }

    @Test
    public void multiFieldOr() {
        SearchDocument doc = new SearchDocument("d1");
        doc.addFieldValue("soNo", "SO000001");
        doc.addFieldValue("mblNo", "MBL000002");
        engine.upsert(DOMAIN, List.of(doc));

        assertThat(searchIds(List.of("SO000001"), List.of("soNo", "mblNo"))).containsExactly("d1");
        assertThat(searchIds(List.of("MBL000002"), List.of("soNo", "mblNo"))).containsExactly("d1");
        assertThat(searchIds(List.of("000002"), List.of("soNo", "mblNo"))).containsExactly("d1");
        assertThat(searchIds(List.of("HBL"), List.of("soNo", "mblNo"))).isEmpty();
    }

    @Test
    public void multiValuedFieldFromChildAggregation() {
        SearchDocument doc = new SearchDocument("d1");
        doc.addFieldValue("containerNo", "CONT0001");
        doc.addFieldValue("containerNo", "CONT0002");
        engine.upsert(DOMAIN, List.of(doc));

        assertThat(searchIds(List.of("CONT0001"), List.of("containerNo"))).containsExactly("d1");
        assertThat(searchIds(List.of("CONT0002"), List.of("containerNo"))).containsExactly("d1");
        assertThat(searchIds(List.of("CONT0003"), List.of("containerNo"))).isEmpty();
    }

    @Test
    public void multiTermsOr() {
        SearchDocument d1 = new SearchDocument("d1");
        d1.addFieldValue("soNo", "SO111111");
        SearchDocument d2 = new SearchDocument("d2");
        d2.addFieldValue("soNo", "SO222222");
        engine.upsert(DOMAIN, List.of(d1, d2));

        List<String> ids = searchIds(List.of("SO111111", "SO222222"), List.of("soNo"));
        assertThat(ids).containsExactlyInAnyOrder("d1", "d2");
        assertThat(searchIds(List.of("SO111111", "333333"), List.of("soNo"))).containsExactly("d1");
    }

    @Test
    public void literalMatchNotRegex() {
        SearchDocument d1 = new SearchDocument("d1");
        d1.addFieldValue("title", "aab");
        SearchDocument d2 = new SearchDocument("d2");
        d2.addFieldValue("title", "a+b");
        engine.upsert(DOMAIN, List.of(d1, d2));

        // 字面量语义：a+b 不作为正则（不命中 aab），按字面命中 a+b
        assertThat(searchIds(List.of("a+b"), List.of("title"))).containsExactly("d2");
        assertThat(searchIds(List.of("aab"), List.of("title"))).containsExactly("d1");
    }

    @Test
    public void cjkContains() {
        SearchDocument doc = new SearchDocument("d1");
        doc.addFieldValue("title", "三星集装箱货运代理");
        engine.upsert(DOMAIN, List.of(doc));

        assertThat(searchIds(List.of("集装箱"), List.of("title"))).containsExactly("d1");
        assertThat(searchIds(List.of("货运代理"), List.of("title"))).containsExactly("d1");
        assertThat(searchIds(List.of("散货"), List.of("title"))).isEmpty();
    }

    @Test
    public void filterTenantDelStatusAppId() {
        SearchDocument d1 = new SearchDocument("d1");
        d1.addFieldValue("soNo", "SO000001");
        d1.setTenantCode("geelato");
        d1.setDelStatus("0");
        d1.setAppId("app1");
        SearchDocument d2 = new SearchDocument("d2");
        d2.addFieldValue("soNo", "SO000002");
        d2.setTenantCode("other");
        d2.setDelStatus("1");
        engine.upsert(DOMAIN, List.of(d1, d2));

        SearchQuery q = new SearchQuery();
        q.setTerms(List.of("SO0000"));
        q.setFields(List.of("soNo"));
        // 无过滤：全部命中
        assertThat(engine.search(DOMAIN, q).getIds()).containsExactlyInAnyOrder("d1", "d2");

        q.setTenantCode("geelato");
        assertThat(engine.search(DOMAIN, q).getIds()).containsExactly("d1");

        q.setTenantCode(null);
        q.setDelStatus("0");
        assertThat(engine.search(DOMAIN, q).getIds()).containsExactly("d1");

        q.setDelStatus(null);
        q.setAppId("app1");
        assertThat(engine.search(DOMAIN, q).getIds()).containsExactly("d1");
    }

    @Test
    public void upsertReplacesWholeDocumentIdempotently() {
        SearchDocument doc = new SearchDocument("d1");
        doc.addFieldValue("soNo", "SO000001");
        engine.upsert(DOMAIN, List.of(doc));
        assertThat(searchIds(List.of("SO000001"), List.of("soNo"))).containsExactly("d1");

        SearchDocument replaced = new SearchDocument("d1");
        replaced.addFieldValue("soNo", "SO999999");
        engine.upsert(DOMAIN, List.of(replaced));

        assertThat(searchIds(List.of("SO000001"), List.of("soNo"))).isEmpty();
        assertThat(searchIds(List.of("SO999999"), List.of("soNo"))).containsExactly("d1");
    }

    @Test
    public void deleteIsIdempotent() {
        SearchDocument doc = new SearchDocument("d1");
        doc.addFieldValue("soNo", "SO000001");
        engine.upsert(DOMAIN, List.of(doc));

        engine.delete(DOMAIN, List.of("d1"));
        engine.delete(DOMAIN, List.of("d1"));

        assertThat(searchIds(List.of("SO000001"), List.of("soNo"))).isEmpty();
    }

    @Test
    public void emptyTermsReturnEmpty() {
        SearchDocument doc = new SearchDocument("d1");
        doc.addFieldValue("soNo", "SO000001");
        engine.upsert(DOMAIN, List.of(doc));

        assertThat(searchIds(List.of(), List.of("soNo"))).isEmpty();
    }

    @Test
    public void truncatedFlagWhenExceedsMaxIds() {
        for (int i = 0; i < 3; i++) {
            SearchDocument doc = new SearchDocument("d" + i);
            doc.addFieldValue("soNo", "SO00000" + i);
            engine.upsert(DOMAIN, List.of(doc));
        }

        SearchQuery q = new SearchQuery();
        q.setTerms(List.of("SO0000"));
        q.setFields(List.of("soNo"));
        q.setMaxIds(2);

        SearchResult result = engine.search(DOMAIN, q);
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getIds()).hasSize(2);
    }

    @Test
    public void documentWithoutSearchedFieldNeverMatches() {
        SearchDocument doc = new SearchDocument("d1");
        doc.setTenantCode("geelato");
        engine.upsert(DOMAIN, List.of(doc));

        assertThat(searchIds(List.of("anything"), List.of("soNo"))).isEmpty();
    }
}
