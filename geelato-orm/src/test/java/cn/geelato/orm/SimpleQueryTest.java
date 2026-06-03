package cn.geelato.orm;

import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.TestOrderEntity;
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

    @Test
    public void shouldGenerateForeignJoinSqlFromMetadata() {
        String sql = MetaFactory.query(TestOrderEntity.class)
                .select(new String[]{"id", "code"})
                .selectRef("userId->name", "userName")
                .toSql();

        assertNotNull(sql);
        assertTrue(sql.toLowerCase().contains("left join"));
        assertTrue(sql.toLowerCase().contains("test_user"));
        assertTrue(sql.contains("userName"));
    }

    @Test
    public void shouldGenerateCustomJoinGroupAndHavingSql() {
        String sql = MetaFactory.query(TestOrderEntity.class)
                .as("o")
                .select(new String[]{"id", "code"})
                .selectExpr("u.name", "userName")
                .leftJoin(TestUserEntity.class, "u", on -> on.eqField("userId", "u.id"))
                .groupBy("id", "code", "u.name")
                .havingSql("count(*) > 0")
                .order(Order.desc("code"))
                .toSql();

        assertNotNull(sql);
        assertTrue(sql.toLowerCase().contains("left join test_user u on o.user_id=u.id"));
        assertTrue(sql.contains("u.name userName"));
        assertTrue(sql.toLowerCase().contains("group by o.id,o.code,u.name"));
        assertTrue(sql.toLowerCase().contains("having count(*) > 0"));
        assertTrue(sql.toLowerCase().contains("order by o.code desc"));
    }

    @Test
    public void shouldKeepNativeSqlAsIs() {
        String sql = MetaFactory.sql("select id, name from test_user where id = ?")
                .param("1001")
                .toSql();

        assertNotNull(sql);
        assertTrue(sql.toLowerCase().contains("select id, name from test_user"));
    }
}
