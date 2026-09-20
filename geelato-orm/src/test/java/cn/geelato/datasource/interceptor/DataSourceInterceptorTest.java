package cn.geelato.datasource.interceptor;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.mql.command.BaseCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.datasource.EntityDataSourceResolver;
import cn.geelato.lang.meta.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataSourceInterceptorTest {

    private final DataSourceInterceptor interceptor = new DataSourceInterceptor();

    @BeforeEach
    void injectEntityResolver() throws Exception {
        Field field = DataSourceInterceptor.class.getDeclaredField("entityDataSourceResolver");
        field.setAccessible(true);
        field.set(interceptor, new EntityDataSourceResolver());
    }

    @AfterEach
    void cleanUp() throws Exception {
        DynamicDataSourceHolder.clearDataSourceKey();
        DataSourceManager.singleInstance().setDefaultDataSourceKey(null);
        setAnnotationDefault(null);
    }

    @Test
    void rawSqlCallFallsBackToPlatformDefaultDataSourceKey() {
        DataSourceManager.singleInstance().setDefaultDataSourceKey("primary");
        assertEquals("primary", interceptor.resolveDataSourceKey(null));
    }

    @Test
    void rawSqlCallNeverReturnsNullEvenWithoutPlatformDefault() {
        DataSourceManager.singleInstance().setDefaultDataSourceKey(null);
        assertEquals("primary", interceptor.resolveDataSourceKey(null));
    }

    @Test
    void rawSqlCallKeepsExplicitOuterScopeDataSourceKey() {
        DataSourceManager.singleInstance().setDefaultDataSourceKey("primary");
        DynamicDataSourceHolder.setDataSourceKey("connect-b");
        assertEquals("connect-b", interceptor.resolveDataSourceKey(null));
    }

    @Test
    void annotationScopedDataSourceKeyTakesPrecedence() throws Exception {
        DataSourceManager.singleInstance().setDefaultDataSourceKey("primary");
        setAnnotationDefault("annotation-ds");
        assertEquals("annotation-ds", interceptor.resolveDataSourceKey(null));
    }

    @Test
    void unresolvedEntityFallsBackToPlatformDefaultDataSourceKey() {
        DataSourceManager.singleInstance().setDefaultDataSourceKey("primary");
        assertEquals("primary", interceptor.resolveDataSourceKey("entity-without-connect"));
    }

    @Test
    void entityInstanceArgResolvesEntityName() {
        assertEquals("platform_exception_log",
                interceptor.resolveEntityName(new Object[]{new NamedEntity()}));
    }

    @Test
    void unnamedEntityInstanceUsesSimpleClassName() {
        assertEquals("UnnamedEntity", interceptor.resolveEntityName(new Object[]{new UnnamedEntity()}));
    }

    @Test
    void plainObjectInstanceResolvesToNull() {
        assertNull(interceptor.resolveEntityName(new Object[]{new PlainObject()}));
    }

    @Test
    void entityListArgResolvesEntityNameFromFirstElement() {
        assertEquals("platform_exception_log",
                interceptor.resolveEntityName(new Object[]{List.of(new NamedEntity(), new UnnamedEntity())}));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void boundSqlListArgResolvesEntityNameFromFirstElement() {
        BaseCommand command = new BaseCommand();
        command.setEntityName("platform_exception_log");
        BoundSql bs = new BoundSql();
        bs.setCommand(command);
        assertEquals("platform_exception_log",
                interceptor.resolveEntityName(new Object[]{List.of(bs)}));
    }

    @Test
    void classArgTakesPrecedenceOverEntityInstanceArg() {
        assertEquals("platform_exception_log",
                interceptor.resolveEntityName(new Object[]{NamedEntity.class, new UnnamedEntity()}));
    }

    @Test
    void unrecognizedListDoesNotBlockLaterArgs() {
        assertEquals("platform_exception_log",
                interceptor.resolveEntityName(new Object[]{List.of("param"), NamedEntity.class}));
    }

    @Test
    void emptyOrNullArgsResolveToNull() {
        assertNull(interceptor.resolveEntityName(new Object[]{}));
        assertNull(interceptor.resolveEntityName(new Object[]{null}));
        assertNull(interceptor.resolveEntityName(new Object[]{List.of()}));
    }

    @Entity(name = "platform_exception_log")
    static class NamedEntity {
    }

    @Entity
    static class UnnamedEntity {
    }

    static class PlainObject {
    }

    @SuppressWarnings("unchecked")
    private static void setAnnotationDefault(String key) throws Exception {
        Field field = DataSourceInterceptor.class.getDeclaredField("DEFAULT_DATA_SOURCE");
        field.setAccessible(true);
        ThreadLocal<String> threadLocal = (ThreadLocal<String>) field.get(null);
        if (key == null) {
            threadLocal.remove();
        } else {
            threadLocal.set(key);
        }
    }
}
