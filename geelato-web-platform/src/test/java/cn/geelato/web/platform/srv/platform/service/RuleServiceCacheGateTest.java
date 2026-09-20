package cn.geelato.web.platform.srv.platform.service;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.mql.command.QueryCommand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MQL 查询缓存两级门控:全局开关(GlobalContext.__MetaQueryCache__)且实体 cache_type
 * ∈ {BackEnd, BackEndAndFrontEnd}。
 */
class RuleServiceCacheGateTest {

    private static final String BACKEND = "gate_backend_test";
    private static final String FRONTEND = "gate_frontend_test";
    private static final String NONE = "gate_none_test";

    @BeforeAll
    static void setUp() {
        MetaManager metaManager = MetaManager.singleInstance();
        register(metaManager, BACKEND, "backend");
        register(metaManager, FRONTEND, "frontend");
        register(metaManager, NONE, "none");
    }

    @AfterAll
    static void tearDown() {
        MetaManager metaManager = MetaManager.singleInstance();
        metaManager.removeOne(BACKEND);
        metaManager.removeOne(FRONTEND);
        metaManager.removeOne(NONE);
    }

    @Test
    void backEndCacheEnabledFollowsCacheType() {
        assertTrue(RuleService.backEndCacheEnabled(BACKEND));
        assertFalse(RuleService.backEndCacheEnabled(FRONTEND));
        assertFalse(RuleService.backEndCacheEnabled(NONE));
    }

    @Test
    void backEndCacheEnabledRejectsUnknownOrBlank() {
        assertFalse(RuleService.backEndCacheEnabled("no_such_gate_entity"));
        assertFalse(RuleService.backEndCacheEnabled(null));
        assertFalse(RuleService.backEndCacheEnabled(""));
    }

    @Test
    void cacheEnabledForCombinesBothLevels() {
        assertTrue(RuleService.cacheEnabledFor(command(BACKEND)));
        assertFalse(RuleService.cacheEnabledFor(command(FRONTEND)));
        assertFalse(RuleService.cacheEnabledFor(command(NONE)));
        assertFalse(RuleService.cacheEnabledFor(command("no_such_gate_entity")));
        assertFalse(RuleService.cacheEnabledFor(null));
    }

    @Test
    void admissionBoundedForUnpagedLargeResults() {
        QueryCommand paged = command(BACKEND);
        paged.setPageNum(1);
        paged.setPageSize(10);
        assertTrue(RuleService.isCacheableResult(paged, rows(10_000)));

        QueryCommand unpaged = command(BACKEND);
        assertTrue(RuleService.isCacheableResult(unpaged, rows(100)));
        assertFalse(RuleService.isCacheableResult(unpaged, rows(501)));
        assertTrue(RuleService.isCacheableResult(unpaged, rows(500)));
        assertTrue(RuleService.isCacheableResult(unpaged, null));
    }

    private static QueryCommand command(String entityName) {
        QueryCommand command = new QueryCommand();
        command.setEntityName(entityName);
        return command;
    }

    private static List<Object> rows(int size) {
        List<Object> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new Object());
        }
        return list;
    }

    private static void register(MetaManager metaManager, String entityName, String cacheType) {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setEntityName(entityName);
        tableMeta.setTableName("t_" + entityName);
        tableMeta.setCacheType(cacheType);
        ColumnMeta column = new ColumnMeta();
        column.setName("code");
        column.setFieldName("code");
        column.setDataType("varchar");
        metaManager.parseTableEntity(tableMeta, List.of(column), null, null, null);
    }
}
