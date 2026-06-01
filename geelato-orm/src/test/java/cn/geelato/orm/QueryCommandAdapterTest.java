package cn.geelato.orm;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.QueryCommandAdapter;
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
}
