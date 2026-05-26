package cn.geelato.orm;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.QueryCommandAdapter;
import cn.geelato.orm.support.TestUserEntity;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class QueryCommandAdapterTest extends OrmTestSupport {

    @Test
    public void shouldAdaptQueryDslToQueryCommand() {
        QueryCommand command = QueryCommandAdapter.forList(
                MetaFactory.query(TestUserEntity.class)
                        .select(new String[]{"id", "name"})
                        .where(Filter.eq("delStatus", 0))
                        .order(Order.desc("updateAt"))
                        .page(1, 20)
                        .viewParams(Map.of("tenantCode", "geelato"))
        );

        Assert.assertEquals("TestUser", command.getEntityName());
        Assert.assertArrayEquals(new String[]{"id", "name"}, command.getFields());
        Assert.assertEquals("updateAt DESC", command.getOrderBy());
        Assert.assertEquals(1, command.getPageNum());
        Assert.assertEquals(20, command.getPageSize());
        Assert.assertNotNull(command.getWhere());
        Assert.assertEquals("geelato", command.getViewTemplateParams().get("tenantCode"));
    }
}
