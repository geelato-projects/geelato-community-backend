package cn.geelato.core.meta.model;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import org.h2.Driver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CommonRowMapper 兜底行为：
 * 元数据无 classType（DB 在线源实体）或元数据缺失时，mapRow 必须返回 Map 形式的数据，
 * 不能返回 null，也不能抛异常。
 */
class CommonRowMapperTest {

    private static final String ENTITY = "cm_fallback_test";
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void setUp() {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(
                new Driver(), "jdbc:h2:mem:commonRowMapperTest;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create table \"t_cm_fallback\" (\"user_name\" varchar(64), \"user_age\" int)");
        jdbcTemplate.update("insert into \"t_cm_fallback\" values (?, ?)", "zhangsan", 18);

        // 以 JDBC 驱动实际上报的表名为准注册元数据，避免 H2 大小写行为差异
        String tableName = jdbcTemplate.execute(CommonRowMapperTest::reportTableName);
        MetaManager metaManager = MetaManager.singleInstance();
        metaManager.parseTableEntity(buildTableMeta(tableName), buildColumns(), null, null, null);
    }

    @AfterAll
    static void tearDown() {
        MetaManager.singleInstance().removeOne(ENTITY);
    }

    @Test
    void mapRowReturnsMapWhenClassTypeMissing() {
        Map<String, Object> row = jdbcTemplate.queryForObject(
                "select * from \"t_cm_fallback\"", new CommonRowMapper<>());

        assertNotNull(row);
        assertEquals("zhangsan", row.get("userName"));
        assertEquals(18, row.get("userAge"));
    }

    @Test
    void mapRowReturnsMapWhenMetaMissing() {
        jdbcTemplate.execute("create table \"t_cm_no_meta\" (\"col_a\" varchar(32))");
        jdbcTemplate.update("insert into \"t_cm_no_meta\" values (?)", "v1");

        Map<String, Object> row = jdbcTemplate.queryForObject(
                "select * from \"t_cm_no_meta\"", new CommonRowMapper<>());

        assertNotNull(row);
        assertEquals("v1", row.get("col_a"));
    }

    private static String reportTableName(Connection connection) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery("select * from \"t_cm_fallback\"")) {
            return resultSet.getMetaData().getTableName(1);
        }
    }

    private static TableMeta buildTableMeta(String tableName) {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setEntityName(ENTITY);
        tableMeta.setTableName(tableName);
        return tableMeta;
    }

    private static List<ColumnMeta> buildColumns() {
        ColumnMeta nameColumn = new ColumnMeta();
        nameColumn.setName("user_name");
        nameColumn.setFieldName("userName");
        nameColumn.setDataType("varchar");
        ColumnMeta ageColumn = new ColumnMeta();
        ageColumn.setName("user_age");
        ageColumn.setFieldName("userAge");
        ageColumn.setDataType("int");
        return List.of(nameColumn, ageColumn);
    }
}
