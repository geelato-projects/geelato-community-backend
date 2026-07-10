package cn.geelato.orm;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.QueryCommandAdapter;
import cn.geelato.orm.support.TestOrderEntity;
import cn.geelato.orm.support.TestUserEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

        assertEquals("TestUser", command.getEntityName());
        assertArrayEquals(new String[]{"id", "name"}, command.getFields());
        assertEquals("updateAt DESC", command.getOrderBy());
        assertEquals(1, command.getPageNum());
        assertEquals(20, command.getPageSize());
        assertNotNull(command.getWhere());
        assertEquals("geelato", command.getViewTemplateParams().get("tenantCode"));
    }

    @Test
    public void shouldAdaptJoinAndProcedureRelatedQueryOptions() {
        QueryCommand command = QueryCommandAdapter.forList(
                MetaFactory.query(TestOrderEntity.class)
                        .useDataSource("crm")
                        .as("o")
                        .select(new String[]{"id", "code"})
                        .selectRef("userId->name", "userName")
                        .selectExpr("count(*)", "totalCount")
                        .leftJoin(TestUserEntity.class, "u", on -> on.eqField("userId", "u.id"))
                        .groupBy("id", "code", "u.name")
                        .havingSql("count(*) > 0")
        );

        assertEquals("o", command.getTableAlias());
        assertArrayEquals(new String[]{"id", "code", "userId->name"}, command.getFields());
        assertArrayEquals(new String[]{"userId->name"}, command.getForeignFields());
        assertEquals("userName", command.getAlias().get("userId->name"));
        assertEquals(1, command.getSelectExprs().size());
        assertEquals("count(*)", command.getSelectExprs().get(0).getExpression());
        assertEquals("totalCount", command.getSelectExprs().get(0).getAlias());
        assertEquals("id,code,u.name", command.getGroupBy());
        assertEquals("count(*) > 0", command.getHavingSql());
        assertEquals(1, command.getJoins().size());
        assertEquals("left join", command.getJoins().get(0).getJoinType());
        assertEquals("u", command.getJoins().get(0).getAlias());
        assertEquals("TestUser", command.getJoins().get(0).getEntityName());
        assertEquals("userId", command.getJoins().get(0).getConditions().get(0).getLeftField());
        assertEquals("u.id", command.getJoins().get(0).getConditions().get(0).getRightField());
        assertEquals("crm", command.getConnectId());
    }
}
