package cn.geelato.orm;

import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.TestUserEntity;
import org.junit.Assert;
import org.junit.Test;

public class SimpleQueryTest extends OrmTestSupport {

    @Test
    public void shouldGenerateQuerySqlFromMetadata() {
        String sql = MetaFactory.query(TestUserEntity.class)
                .select(new String[]{"id", "name"})
                .where(Filter.eq("delStatus", 0))
                .order(Order.desc("updateAt"))
                .page(1, 10)
                .toSql();

        Assert.assertNotNull(sql);
        Assert.assertTrue(sql.toLowerCase().contains("select"));
        Assert.assertTrue(sql.toLowerCase().contains("test_user"));
        Assert.assertTrue(sql.toLowerCase().contains("order by"));
    }

    @Test
    public void shouldGenerateInsertAndDeleteSqlFromMetadata() {
        String insertSql = MetaFactory.insert("TestUser")
                .value("name", "tester")
                .toSql();
        String deleteSql = MetaFactory.delete("TestUser")
                .where(Filter.eq("id", "1001"))
                .toSql();

        Assert.assertNotNull(insertSql);
        Assert.assertTrue(insertSql.toLowerCase().contains("insert into"));
        Assert.assertTrue(insertSql.toLowerCase().contains("test_user"));
        Assert.assertNotNull(deleteSql);
        Assert.assertTrue(deleteSql.toLowerCase().contains("update"));
    }
}
