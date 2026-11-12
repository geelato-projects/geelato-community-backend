package cn.geelato.core.sql.provider;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.ds.spi.DataSourceDefinitionLoader;
import cn.geelato.core.enums.Dialects;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.orm.Dao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 标识符引用符按有效方言输出：mysql 反引号；risingwave 经方言别名归一为 postgresql 家族走双引号；
 * 实体未声明 dbType 时按 connectId 经 DataSourceManager（含快照 miss 自愈）解析。
 */
class MetaQuerySqlProviderDbTypeTest {

    private static final MetaQuerySqlProvider provider = new MetaQuerySqlProvider();

    @BeforeAll
    static void setUp() {
        Dialects.registerAlias("risingwave", Dialects.POSTGRESQL);
        DataSourceManager.singleInstance().setDefinitionLoader(new DataSourceDefinitionLoader() {
            @Override
            public List<Map<String, Object>> load(Dao dao) {
                return List.of();
            }

            @Override
            public Map<String, Object> loadOne(String key) {
                if (!"ds-quote-rw".equals(key)) {
                    return null;
                }
                return Map.of("id", key, "db_type", "risingwave");
            }
        });
    }

    @Test
    void mysqlDbTypeWrapsIdentifierInBackticks() {
        assertEquals("`bizType`", quote(entityWithDbType("mysql")));
    }

    @Test
    void risingwaveDbTypeWrapsIdentifierInDoubleQuotes() {
        assertEquals("\"bizType\"", quote(entityWithDbType("risingwave")));
    }

    @Test
    void nullDbTypeResolvesViaConnectIdLazyConfig() {
        TableMeta tm = new TableMeta();
        tm.setEntityName("quote_probe_entity");
        tm.setTableName("quote_probe");
        tm.setConnectId("ds-quote-rw");
        EntityMeta em = new EntityMeta();
        em.setTableMeta(tm);
        em.setEntityName("quote_probe_entity");

        assertEquals("\"bizType\"", quote(em));
    }

    private EntityMeta entityWithDbType(String dbType) {
        TableMeta tm = new TableMeta();
        tm.setEntityName("quote_probe_entity");
        tm.setTableName("quote_probe");
        tm.setDbType(dbType);
        EntityMeta em = new EntityMeta();
        em.setTableMeta(tm);
        em.setEntityName("quote_probe_entity");
        return em;
    }

    private String quote(EntityMeta em) {
        StringBuilder sb = new StringBuilder();
        provider.appendQuotedIdentifier(sb, em, "bizType");
        return sb.toString();
    }
}
