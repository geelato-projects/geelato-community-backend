package cn.geelato.mqltest.mql;

import cn.geelato.core.experiment.ExperimentContext;
import cn.geelato.core.experiment.ExperimentFeatures;
import cn.geelato.core.mql.MqlQueryProcessor;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.mqltest.support.MqlTestSupport;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * fuzzymatch 函数条件的 SQL 等价改写测试：
 * gfn_fuzzymatch(col,'kw') > 0 → (col <> '' AND col REGEXP ?)，pattern 复刻原函数清洗；
 * 非 gt:0 场景保留 geelato.gfn_fuzzymatch 原函数。
 * <p>
 * 实验开关（{@code ExperimentFeatures.SEARCH}）未开启时改写整体不生效，
 * 回到原 geelato.gfn_fuzzymatch 存储函数路径——默认关场景见 disabled* 用例。
 */
@DisplayName("fuzzymatch：REGEXP 等价改写")
class FuzzymatchRewriteTest extends MqlTestSupport {

    @BeforeEach
    void enableSearchExperiment() {
        ExperimentContext.enable(java.util.List.of(ExperimentFeatures.SEARCH));
    }

    @AfterEach
    void clearExperiment() {
        ExperimentContext.clear();
    }

    private BoundPageSql process(String mql) {
        return MqlQueryProcessor.getInstance().process(mql).getBoundPageSql();
    }

    private static String mql(String body) {
        JSONObject root = new JSONObject();
        root.put(ENTITY_ORDER, JSON.parse(body));
        return root.toJSONString();
    }

    @Test
    @DisplayName("单条件：改写为 REGEXP，参数绑定 pattern")
    void rewriteSingleConditionToRegexp() {
        BoundPageSql sql = process(mql("{\"fuzzymatch($mql_test_order.orderNo,'ABC123')|gt\":\"0\",\"@p\":\"1,15\"}"));
        String main = sql.getBoundSql().getSql();
        assertTrue(main.contains("order_no <> ''"), main);
        assertTrue(main.contains("order_no REGEXP ?"), main);
        assertFalse(main.contains("gfn_fuzzymatch"), main);
        // count 与主查询同步改写
        assertTrue(sql.getCountSql().contains("REGEXP ?"), sql.getCountSql());
        // pattern 作为绑定参数替换原比较值 0
        assertTrue(Arrays.asList(sql.getBoundSql().getParams()).contains("ABC123"),
                Arrays.toString(sql.getBoundSql().getParams()));
    }

    @Test
    @DisplayName("多词/全角逗号：pattern 为 OR")
    void multiTermAndFullWidthComma() {
        BoundPageSql sql = process(mql("{\"fuzzymatch($mql_test_order.orderNo,'SO111,SO222')|gt\":\"0\"}"));
        assertTrue(Arrays.asList(sql.getBoundSql().getParams()).contains("SO111|SO222"),
                Arrays.toString(sql.getBoundSql().getParams()));

        sql = process(mql("{\"fuzzymatch($mql_test_order.orderNo,'SO111，SO222')|gt\":\"0\"}"));
        assertTrue(Arrays.asList(sql.getBoundSql().getParams()).contains("SO111|SO222"),
                Arrays.toString(sql.getBoundSql().getParams()));
    }

    @Test
    @DisplayName("空关键字：绑定永不匹配 pattern")
    void emptyKeywordBindsNeverMatch() {
        BoundPageSql sql = process(mql("{\"fuzzymatch($mql_test_order.orderNo,'   ')|gt\":\"0\"}"));
        assertTrue(Arrays.asList(sql.getBoundSql().getParams()).contains("a^"),
                Arrays.toString(sql.getBoundSql().getParams()));
    }

    @Test
    @DisplayName("正则元字符：pattern 原样保留（与原函数正则语义一致）")
    void regexMetaCharsPreserved() {
        BoundPageSql sql = process(mql("{\"fuzzymatch($mql_test_order.orderNo,'AB+')|gt\":\"0\"}"));
        assertTrue(Arrays.asList(sql.getBoundSql().getParams()).contains("AB+"),
                Arrays.toString(sql.getBoundSql().getParams()));
    }

    @Test
    @DisplayName("or 组：全部 fuzzymatch 条件各自改写")
    void rewriteOrGroupConditions() {
        String body = "{\"@b\":[{\"or\":[" +
                "{\"fuzzymatch($mql_test_order.orderNo,'K1')|gt\":\"0\"}," +
                "{\"fuzzymatch($mql_test_order.status,'K2')|gt\":\"0\"}]}]," +
                "\"status|eq\":\"done\",\"@p\":\"1,15\"}";
        BoundPageSql sql = process(mql(body));
        String main = sql.getBoundSql().getSql();
        assertTrue(main.contains("order_no REGEXP ?"), main);
        assertTrue(main.contains("status REGEXP ?"), main);
        assertFalse(main.contains("gfn_fuzzymatch"), main);
    }

    @Test
    @DisplayName("or 组 $self 前缀：括号组内不经归一，按当前实体解析列名")
    void rewriteOrGroupWithSelfPrefix() {
        // 业务前端实际写法：$self.field（@b 组内保留原始形态，resolveColumn 需按当前实体解析）
        String body = "{\"status|eq\":\"done\"," +
                "\"@b\":[{\"or\":[" +
                "{\"fuzzymatch($self.orderNo,'OBLMOBSZ')|gt\":0}," +
                "{\"fuzzymatch($self.quantity,'OBLMOBSZ')|gt\":0}]}]," +
                "\"@p\":\"1,15\"}";
        BoundPageSql sql = process(mql(body));
        String main = sql.getBoundSql().getSql();
        assertTrue(main.contains("order_no REGEXP ?"), main);
        assertTrue(main.contains("quantity REGEXP ?"), main);
        assertFalse(main.contains("gfn_fuzzymatch"), main);
        assertFalse(main.contains("$self"), main);
    }

    @Test
    @DisplayName("非 gt:0：保留原函数路径（等价优先）")
    void nonGtOperatorKeepsOriginalFunction() {
        BoundPageSql sql = process(mql("{\"fuzzymatch($mql_test_order.orderNo,'ABC')|gt\":\"1\"}"));
        String main = sql.getBoundSql().getSql();
        assertTrue(main.contains("gfn_fuzzymatch"), main);
        assertFalse(main.contains("REGEXP ?"), main);
    }

    @Test
    @DisplayName("实验开关未开启：回到原存储函数路径（默认关）")
    void experimentDisabledKeepsOriginalFunction() {
        ExperimentContext.clear();
        BoundPageSql sql = process(mql("{\"fuzzymatch($mql_test_order.orderNo,'ABC123')|gt\":\"0\",\"@p\":\"1,15\"}"));
        String main = sql.getBoundSql().getSql();
        assertTrue(main.contains("gfn_fuzzymatch"), main);
        assertFalse(main.contains("REGEXP ?"), main);
        assertTrue(sql.getCountSql().contains("gfn_fuzzymatch"), sql.getCountSql());
    }

    @Test
    @DisplayName("普通 contains 条件不受影响")
    void containsOperatorUnaffected() {
        BoundPageSql sql = process(mql("{\"orderNo|contains\":\"ABC\"}"));
        String main = sql.getBoundSql().getSql();
        assertTrue(main.contains("like CONCAT('%',?,'%')"), main);
        assertEquals("ABC", Arrays.asList(sql.getBoundSql().getParams()).get(0));
    }
}
