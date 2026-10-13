package cn.geelato.script.kit;

import cn.geelato.core.orm.Dao;
import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MqlKit 门禁路径单测（不触平台元数据）：
 * query/save/delete 的真实链路（MqlQueryProcessor + daoSupplier + ormWrapper）留待平台侧集成验证。
 */
class MqlKitTest {

    private static DefaultScriptEngine noDaoEngine;
    private static DefaultScriptEngine gatedEngine;

    @BeforeAll
    static void init() {
        noDaoEngine = new ScriptEngineBuilder().build();
        gatedEngine = new ScriptEngineBuilder()
                .daoSupplier(MqlKitTest::stubDao)
                .daoWriteEnabled(false)
                .build();
    }

    @AfterAll
    static void shutdown() {
        noDaoEngine.close();
        gatedEngine.close();
    }

    private static Dao stubDao() {
        return null;
    }

    @Test
    void listWithoutDaoSupplierThrowsPrecisely() {
        ScriptResult result = noDaoEngine.execute(ScriptExecution.builder()
                .content("return $gl.mql.list('{\"platform_user\": {}}')").build());
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("ORM Dao 未配置"), result.getError().getMessage());
    }

    @Test
    void saveRejectedWhenWriteDisabled() {
        ScriptResult result = gatedEngine.execute(ScriptExecution.builder()
                .content("return $gl.mql.save('{\"platform_user\": {\"userName\": \"a\"}}')").build());
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("MQL 写通道未启用"), result.getError().getMessage());
    }

    @Test
    void deleteByGqlRejectedWhenWriteDisabled() {
        ScriptResult result = gatedEngine.execute(ScriptExecution.builder()
                .content("return $gl.mql.deleteByGql('{\"platform_user\": {\"id|eq\": \"1\"}}')").build());
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("MQL 写通道未启用"), result.getError().getMessage());
    }

    @Test
    void deleteByIdRejectedWhenWriteDisabled() {
        ScriptResult result = gatedEngine.execute(ScriptExecution.builder()
                .content("return $gl.mql.delete('platform_user', 'id1,id2')").build());
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("MQL 写通道未启用"), result.getError().getMessage());
    }

    @Test
    void explainRejectsUnknownOp() {
        ScriptResult result = gatedEngine.execute(ScriptExecution.builder()
                .content("return $gl.mql.explain('{\"platform_user\": {}}', 'truncate')").build());
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("不支持的 explain 操作"), result.getError().getMessage());
    }
}
