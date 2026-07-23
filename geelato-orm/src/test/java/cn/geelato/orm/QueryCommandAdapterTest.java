package cn.geelato.orm;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.adapter.QueryCommandAdapter;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.Order;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.TestOrderEntity;
import cn.geelato.orm.support.TestUserEntity;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.spi.FluentQueryFilterInjector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class QueryCommandAdapterTest extends OrmTestSupport {

    @AfterEach
    void clearContext() {
        new BeansUtils().setApplicationContext(null);
    }

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

    @Test
    public void shouldKeepExplicitFiltersWhenNoInjectorExists() {
        QueryCommand command = QueryCommandAdapter.forList(
                MetaFactory.query(TestUserEntity.class)
                        .where(Filter.eq("tenantCode", "manual"))
        );

        assertEquals(1, command.getWhere().getFilters().size());
        assertEquals("tenantCode", command.getWhere().getFilters().get(0).getField());
        assertEquals("manual", command.getWhere().getFilters().get(0).getValue());
        assertEquals(null, command.getOriginalWhere());
    }

    @Test
    public void shouldInjectDefaultFiltersWhenSingleEnabledInjectorExists() {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        Mockito.when(applicationContext.getBeansOfType(FluentQueryFilterInjector.class))
                .thenReturn(Map.of("injector", new FluentQueryFilterInjector() {
                    @Override
                    public boolean isEnabled() {
                        return true;
                    }

                    @Override
                    public void inject(QueryCommand command, MetaQuery query) {
                        applyDefaultFilters(command);
                    }
                }));
        new BeansUtils().setApplicationContext(applicationContext);

        QueryCommand command = QueryCommandAdapter.forList(MetaFactory.query(TestUserEntity.class));

        assertNotNull(command.getWhere());
        assertEquals("creator='U1001'", command.getOriginalWhere());
        assertEquals(1, command.getWhere().getFilters().stream()
                .filter(filter -> "tenantCode".equals(filter.getField()))
                .count());
        assertEquals("geelato", command.getWhere().getFilters().stream()
                .filter(filter -> "tenantCode".equals(filter.getField()))
                .map(FilterGroup.Filter::getValue)
                .collect(Collectors.toList())
                .get(0));
    }

    @Test
    public void shouldNotDuplicateExplicitTenantCodeWhenInjectorRuns() {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        Mockito.when(applicationContext.getBeansOfType(FluentQueryFilterInjector.class))
                .thenReturn(Map.of("injector", new FluentQueryFilterInjector() {
                    @Override
                    public boolean isEnabled() {
                        return true;
                    }

                    @Override
                    public void inject(QueryCommand command, MetaQuery query) {
                        applyDefaultFilters(command);
                    }
                }));
        new BeansUtils().setApplicationContext(applicationContext);

        QueryCommand command = QueryCommandAdapter.forList(
                MetaFactory.query(TestUserEntity.class).where(Filter.eq("tenantCode", "manual"))
        );

        assertEquals(1, command.getWhere().getFilters().stream()
                .filter(filter -> "tenantCode".equals(filter.getField()))
                .count());
        assertEquals("manual", command.getWhere().getFilters().stream()
                .filter(filter -> "tenantCode".equals(filter.getField()))
                .map(FilterGroup.Filter::getValue)
                .collect(Collectors.toList())
                .get(0));
        assertEquals("creator='U1001'", command.getOriginalWhere());
    }

    private void applyDefaultFilters(QueryCommand command) {
        if (command.getWhere() == null) {
            command.setWhere(new FilterGroup());
        }
        if (!containsField(command.getWhere(), "tenantCode")) {
            command.getWhere().addFilter("tenantCode", "geelato");
        }
        if (!StringUtils.hasText(command.getOriginalWhere())) {
            command.setOriginalWhere("creator='U1001'");
        } else {
            command.setOriginalWhere("(" + command.getOriginalWhere() + ") and (creator='U1001')");
        }
    }

    private boolean containsField(FilterGroup filterGroup, String fieldName) {
        return filterGroup.getFilters().stream()
                .map(FilterGroup.Filter::getField)
                .filter(Objects::nonNull)
                .anyMatch(fieldName::equals);
    }
}
