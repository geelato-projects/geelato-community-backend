package cn.geelato.core.ds;

import cn.geelato.core.ds.spi.DataSourceDefinitionLoader;
import cn.geelato.core.orm.Dao;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DataSourceManagerTest {

    private static final DataSourceManager manager = DataSourceManager.singleInstance();

    @BeforeAll
    static void loadDataSourceConfigs() {
        DataSourceDefinitionLoader loader = dao -> List.of(
                dbConnect("ds-test-mysql", "MySQL"),
                dbConnect("ds-test-oracle", "oracle"),
                dbConnect("ds-test-blank", null));
        manager.setDefinitionLoader(loader);
        manager.parseDataSourceMeta(new Dao(new JdbcTemplate()));
    }

    @Test
    public void getDataSourceDbTypeReturnsConfiguredDbType() {
        assertEquals("MySQL", manager.getDataSourceDbType("ds-test-mysql"));
        assertEquals("oracle", manager.getDataSourceDbType("ds-test-oracle"));
    }

    @Test
    public void getDataSourceDbTypeReturnsNullWhenConfigMissing() {
        assertNull(manager.getDataSourceDbType("ds-test-unknown"));
        assertNull(manager.getDataSourceDbType(null));
    }

    @Test
    public void getDataSourceDbTypeReturnsNullWhenDbTypeAbsent() {
        assertNull(manager.getDataSourceDbType("ds-test-blank"));
    }

    @Test
    public void getDataSourceOnlyReturnsRegisteredDataSource() {
        // 未注册的 connectId 不再懒加载建池，直接返回 null
        assertNull(manager.getDataSource("ds-test-mysql"));

        HikariDataSource dataSource = new HikariDataSource();
        manager.registerDataSource("ds-test-registered", dataSource);
        try {
            assertSame(dataSource, manager.getDataSource("ds-test-registered"));
        } finally {
            dataSource.close();
        }
    }

    @Test
    public void defaultDataSourceKeyIsSettable() {
        manager.setDefaultDataSourceKey("ds-test-mysql");
        assertEquals("ds-test-mysql", manager.getDefaultDataSourceKey());
        manager.setDefaultDataSourceKey(null);
    }

    private static Map<String, Object> dbConnect(String id, String dbType) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("db_type", dbType);
        return map;
    }
}
