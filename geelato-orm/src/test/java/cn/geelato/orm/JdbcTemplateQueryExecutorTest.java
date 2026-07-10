package cn.geelato.orm;

import cn.geelato.orm.executor.JdbcTemplateQueryExecutor;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.TestUserEntity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JdbcTemplateQueryExecutorTest extends OrmTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void shouldBindMetaQueryParamsAndUseColumnLabels() throws Exception {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcTemplateQueryExecutor executor = new JdbcTemplateQueryExecutor(jdbcTemplate);
        Object[][] capturedArgs = new Object[1][];

        ResultSet resultSet = Mockito.mock(ResultSet.class);
        ResultSetMetaData metaData = Mockito.mock(ResultSetMetaData.class);
        Mockito.when(resultSet.getMetaData()).thenReturn(metaData);
        Mockito.when(metaData.getColumnCount()).thenReturn(1);
        Mockito.when(metaData.getColumnName(1)).thenReturn("del_status");
        Mockito.when(metaData.getColumnLabel(1)).thenReturn("delStatus");
        Mockito.when(resultSet.getObject(1)).thenReturn(0);

        Mockito.doAnswer(invocation -> {
                    capturedArgs[0] = invocation.getArgument(1);
                    RowMapper<Map<String, Object>> rowMapper = invocation.getArgument(2);
                    return List.of(rowMapper.mapRow(resultSet, 0));
                })
                .when(jdbcTemplate)
                .query(Mockito.anyString(), Mockito.<Object[]>any(), Mockito.any(RowMapper.class));
        Mockito.doAnswer(invocation -> {
                    capturedArgs[0] = invocation.getArgument(1);
                    RowMapper<Map<String, Object>> rowMapper = invocation.getArgument(3);
                    return List.of(rowMapper.mapRow(resultSet, 0));
                })
                .when(jdbcTemplate)
                .query(Mockito.anyString(), Mockito.<Object[]>any(), Mockito.any(int[].class), Mockito.any(RowMapper.class));

        List<Map<String, Object>> rows = executor.executeQuery(
                MetaFactory.query(TestUserEntity.class)
                        .where(Filter.eq("id", "U1"), Filter.eq("delStatus", 0))
        );

        assertArrayEquals(new Object[]{"U1", 0}, capturedArgs[0]);
        assertEquals(0, rows.get(0).get("delStatus"));
        assertFalse(rows.get(0).containsKey("del_status"));
    }
}
