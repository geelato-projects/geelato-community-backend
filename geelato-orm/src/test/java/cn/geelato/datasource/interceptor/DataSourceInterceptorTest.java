package cn.geelato.datasource.interceptor;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.mql.command.BaseCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.datasource.DataSourceOverrideContext;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.datasource.DynamicDataSourceRegistry;
import cn.geelato.datasource.EntityDataSourceResolver;
import cn.geelato.lang.meta.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceInterceptorTest {

    private final DataSourceInterceptor interceptor = new DataSourceInterceptor();

    @org.junit.jupiter.api.BeforeAll
    static void registerEntities() {
        MetaManager manager = MetaManager.singleInstance();
        manager.parseOne(UnclaimedTestEntity.class);
        manager.parseOne(PlatformCatalogTestEntity.class);
        manager.parseOne(ConnectIdTestEntity.class);
    }

    @org.junit.jupiter.api.AfterAll
    static void unregisterEntities() {
        MetaManager manager = MetaManager.singleInstance();
        manager.removeOne("override_unclaimed_entity");
        manager.removeOne("override_platform_entity");
        manager.removeOne("override_connect_entity");
    }

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
        DataSourceOverrideContext.clear();
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
    void overrideRoutesUnclaimedEntityToOverrideKey() {
        DataSourceManager.singleInstance().setDefaultDataSourceKey("primary");
        DataSourceOverrideContext.set("archive", true);
        assertEquals("archive", interceptor.resolveDataSourceKey("override_unclaimed_entity"));
    }

    @Test
    void overrideSkipsPlatformCatalogEntity() {
        DataSourceManager.singleInstance().setDefaultDataSourceKey("primary");
        DataSourceOverrideContext.set("archive", true);
        assertEquals("primary", interceptor.resolveDataSourceKey("override_platform_entity"));
    }

    @Test
    void overrideSkipsConnectIdEntity() {
        DataSourceManager.singleInstance().setDefaultDataSourceKey("primary");
        DataSourceOverrideContext.set("archive", true);
        // 测试环境 resolver 未注入 registry（视为不可校验，跳过校验原样返回实体归属）
        assertEquals("connect-x", interceptor.resolveDataSourceKey("override_connect_entity"));
    }

    @Test
    void unregisteredExplicitConnectIdHardFailsInsteadOfSilentPrimaryFallback() throws Exception {
        EntityDataSourceResolver resolver = (EntityDataSourceResolver)
                interceptorFieldValue("entityDataSourceResolver");
        java.lang.reflect.Field registryField = EntityDataSourceResolver.class.getDeclaredField("dynamicDataSourceRegistry");
        registryField.setAccessible(true);
        registryField.set(resolver, new DynamicDataSourceRegistry(null, null, null, null, null));

        DataSourceManager.singleInstance().setDefaultDataSourceKey("primary");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> interceptor.resolveDataSourceKey("override_connect_entity"));
        assertTrue(ex.getMessage().contains("connect-x"));
        assertTrue(ex.getMessage().contains("拒绝静默回退主库"));
    }

    private Object interceptorFieldValue(String fieldName) throws Exception {
        java.lang.reflect.Field field = DataSourceInterceptor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(interceptor);
    }

    @Test
    void outerScopeKeyBeatsOverride() {
        DataSourceManager.singleInstance().setDefaultDataSourceKey("primary");
        DataSourceOverrideContext.set("archive", true);
        DynamicDataSourceHolder.setDataSourceKey("connect-b");
        assertEquals("connect-b", interceptor.resolveDataSourceKey("override_unclaimed_entity"));
    }

    @Test
    void annotationScopedKeyBeatsOverride() throws Exception {
        DataSourceManager.singleInstance().setDefaultDataSourceKey("primary");
        DataSourceOverrideContext.set("archive", true);
        setAnnotationDefault("annotation-ds");
        assertEquals("annotation-ds", interceptor.resolveDataSourceKey("override_unclaimed_entity"));
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

    @Entity(name = "override_unclaimed_entity")
    public static class UnclaimedTestEntity {
        @cn.geelato.lang.meta.Id
        private String id;
    }

    @Entity(name = "override_platform_entity", catalog = "platform")
    public static class PlatformCatalogTestEntity {
        @cn.geelato.lang.meta.Id
        private String id;
    }

    @Entity(name = "override_connect_entity", connectId = "connect-x")
    public static class ConnectIdTestEntity {
        @cn.geelato.lang.meta.Id
        private String id;
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
