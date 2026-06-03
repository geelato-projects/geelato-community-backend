package cn.geelato.orm;

import cn.geelato.core.orm.Dao;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.support.OrmTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MetaProcedureTest extends OrmTestSupport {

    @Test
    public void shouldGenerateProcedureCallSql() {
        String sql = MetaFactory.procedure("proc_query_user_orders")
                .in("userId", "U1001")
                .in("status", 1)
                .toSql();

        assertEquals("call proc_query_user_orders(?, ?)", sql);
    }

    @Test
    public void shouldCallProcedureAndClearOverrideDataSource() {
        Dao dao = Mockito.mock(Dao.class);
        DefaultMetaCommandExecutor executor = new DefaultMetaCommandExecutor(dao);
        List<Map<String, Object>> rows = List.of(Map.of("id", "1001", "name", "Alice"));
        Mockito.when(dao.callForMapList(Mockito.eq("call proc_query_user_orders(?, ?)"), Mockito.any(Object[].class)))
                .thenReturn(rows);

        List<Map<String, Object>> result = executor.callForMapList(
                "call proc_query_user_orders(?, ?)",
                new Object[]{"U1001", 1},
                "portal"
        );

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).get("name"));
        assertNull(DynamicDataSourceHolder.getDataSourceKey());
    }
}
