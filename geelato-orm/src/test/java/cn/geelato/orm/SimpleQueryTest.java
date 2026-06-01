package cn.geelato.orm;

import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.TestUserEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleQueryTest extends OrmTestSupport {

    @Test
    public void shouldGenerateQuerySqlFromMetadata() {
        String sql = MetaFactory.query(TestUserEntity.class)
                .select(new String[]{"id", "name"})
                .where(Filter.eq("delStatus", 0))
                .order(Order.desc("updateAt"))
                .page(1, 10)
                .toSql();

        assertNotNull(sql);
        assertTrue(sql.toLowerCase().contains("select"));
        assertTrue(sql.toLowerCase().contains("test_user"));
        assertTrue(sql.toLowerCase().contains("order by"));
    }

    @Test
    public void shouldGenerateInsertAndDeleteSqlFromMetadata() {
        String insertSql = MetaFactory.insert("TestUser")
                .value("name", "tester")
                .toSql();
        String deleteSql = MetaFactory.delete("TestUser")
                .where(Filter.eq("id", "1001"))
                .toSql();

        assertNotNull(insertSql);
        assertTrue(insertSql.toLowerCase().contains("insert into"));
        assertTrue(insertSql.toLowerCase().contains("test_user"));
        assertNotNull(deleteSql);
        assertTrue(deleteSql.toLowerCase().contains("update"));
    }
}
