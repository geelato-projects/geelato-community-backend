package cn.geelato.orm;

import cn.geelato.core.orm.Dao;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.query.MetaNativeSql;
import cn.geelato.orm.support.OrmTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MetaNativeSqlTest extends OrmTestSupport {

    @Test
    public void shouldKeepOriginalSqlAndParams() {
        MetaNativeSql nativeSql = MetaFactory.sql("select id, name from test_user where id = ? and del_status = ?")
                .param("1001")
                .param(0);

        assertEquals("select id, name from test_user where id = ? and del_status = ?", nativeSql.toSql());
        assertArrayEquals(new Object[]{"1001", 0}, nativeSql.resolveParams());
    }

    @Test
    public void shouldDelegateNativeQueryAndClearOverrideDataSource() {
        Dao dao = Mockito.mock(Dao.class);
        DefaultMetaCommandExecutor executor = new DefaultMetaCommandExecutor(dao);
        List<Map<String, Object>> rows = List.of(Map.of("id", "1001", "name", "Alice"));
        Mockito.when(dao.nativeQueryForMapList(Mockito.eq("select id, name from test_user where id = ?"), Mockito.any(Object[].class)))
                .thenReturn(rows);

        List<Map<String, Object>> result = executor.nativeQueryForMapList(
                "select id, name from test_user where id = ?",
                new Object[]{"1001"},
                "portal"
        );

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).get("name"));
        assertNull(DynamicDataSourceHolder.getDataSourceKey());
    }

    @Test
    public void shouldDelegateNativeExecute() {
        Dao dao = Mockito.mock(Dao.class);
        DefaultMetaCommandExecutor executor = new DefaultMetaCommandExecutor(dao);
        Mockito.when(dao.nativeExecute(Mockito.eq("update test_user set name = ? where id = ?"), Mockito.any(Object[].class)))
                .thenReturn(1);

        int affected = executor.nativeExecute(
                "update test_user set name = ? where id = ?",
                new Object[]{"Bob", "1001"},
                "portal"
        );

        assertEquals(1, affected);
        assertNull(DynamicDataSourceHolder.getDataSourceKey());
    }
}
