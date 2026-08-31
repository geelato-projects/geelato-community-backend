package cn.geelato.orm;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.executor.spi.DaoMetaExecutionStrategy;
import cn.geelato.orm.page.PageResult;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.runtime.OrmRuntimeProvider;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.TestUserEntity;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单列自动解包与显式类型映射(list(Class)/one(Class))的全链路测试。
 * 通过 H2 内存库 + 模拟 OrmRuntimeProvider 走 MetaFactory DSL 真实执行路径。
 */
class MetaResultMappingTest extends OrmTestSupport {

    @Getter
    @Setter
    public static class EmailAccountDto {
        private Long id;
        private String userId;
        private String emailAddress;
        private Integer delStatus;
    }

    /**
     * 查询经 TestUserEntity 构建元数据,结果按此 DTO 映射(结果类型与元数据实体解耦)。
     */
    @Getter
    @Setter
    public static class TestUserRow {
        private Long id;
        private String name;
        private Integer delStatus;
    }

    @BeforeAll
    static void initDataSourceAndRuntime() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:orm_result_mapping;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table if exists platform_user_email_account");
        jdbcTemplate.execute("""
                create table platform_user_email_account (
                    id bigint auto_increment primary key,
                    user_id varchar(64),
                    email_address varchar(128),
                    del_status int
                )""");
        jdbcTemplate.update("insert into platform_user_email_account(user_id, email_address, del_status) values(?, ?, ?)",
                "U1001", "a@geelato.com", 0);
        jdbcTemplate.update("insert into platform_user_email_account(user_id, email_address, del_status) values(?, ?, ?)",
                "U1001", "b@geelato.com", 0);
        jdbcTemplate.update("insert into platform_user_email_account(user_id, email_address, del_status) values(?, ?, ?)",
                "U1002", null, 0);

        jdbcTemplate.execute("drop table if exists test_user");
        jdbcTemplate.execute("""
                create table test_user (
                    id bigint primary key,
                    name varchar(128),
                    del_status int,
                    create_at datetime,
                    creator varchar(64),
                    creator_name varchar(128),
                    tenant_code varchar(64),
                    bu_id varchar(64),
                    dept_id varchar(64),
                    update_at datetime,
                    updater varchar(64),
                    updater_name varchar(128),
                    delete_at datetime
                )""");
        jdbcTemplate.update("merge into test_user(id, name, del_status) key(id) values(?, ?, ?)", 1L, "Alice", 0);
        jdbcTemplate.update("merge into test_user(id, name, del_status) key(id) values(?, ?, ?)", 2L, "Bob", 0);

        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        OrmRuntimeProvider runtimeProvider = Mockito.mock(OrmRuntimeProvider.class);
        Mockito.when(applicationContext.getBean(OrmRuntimeProvider.class)).thenReturn(runtimeProvider);
        Mockito.when(runtimeProvider.metaCommandExecutor())
                .thenReturn(new DefaultMetaCommandExecutor(new DaoMetaExecutionStrategy(new Dao(jdbcTemplate))));
        new BeansUtils().setApplicationContext(applicationContext);
        // 其他测试类可能在 DataSourceManager 单例中残留 mock primary 数据源（getConnection() 返回 null，
        // 会让 MetaQuery 生成 SQL 解析 dbType 时抛 NPE）。这里覆盖为一个 getConnection() 抛 SQLException 的
        // 数据源：resolvePrimaryDbType 会捕获该异常并缓存 null dbType（即按无方言、不加引号生成 SQL），
        // 使本类的行为与执行顺序无关
        DataSource absentPrimaryDataSource = Mockito.mock(DataSource.class);
        Mockito.when(absentPrimaryDataSource.getConnection()).thenThrow(new SQLException("no primary connection in unit test"));
        DataSourceManager.singleInstance().registerDataSource("primary", absentPrimaryDataSource);
    }

    @AfterEach
    void resetDataSourceContext() {
        DataSourceManager.singleInstance().setDefaultDataSourceKey(null);
        DynamicDataSourceHolder.clearDataSourceKey();
    }

    /**
     * H2 会把未加引号的列标签转成大写(MySQL 按原样返回),断言统一走大小写无关取值。
     */
    private static Object columnValue(Map<String, Object> row, String key) {
        return row.entrySet().stream()
                .filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    // ==================== 原生 SQL:自动解包 ====================

    @Test
    void singleColumnListReturnsBareValues() {
        List<String> emails = MetaFactory.sql(
                        "SELECT email_address FROM platform_user_email_account WHERE user_id = ? AND del_status = 0 ORDER BY id")
                .param("U1001")
                .list();
        assertEquals(List.of("a@geelato.com", "b@geelato.com"), emails);
    }

    @Test
    void singleColumnOneReturnsBareValue() {
        String email = MetaFactory.sql(
                        "SELECT email_address FROM platform_user_email_account WHERE user_id = ? ORDER BY id LIMIT 1")
                .param("U1001")
                .one();
        assertEquals("a@geelato.com", email);
    }

    @Test
    void multiColumnListStillReturnsMaps() {
        List<Map<String, Object>> rows = MetaFactory.sql(
                        "SELECT id, email_address FROM platform_user_email_account WHERE user_id = ? ORDER BY id")
                .param("U1001")
                .list();
        assertEquals(2, rows.size());
        assertEquals("a@geelato.com", columnValue(rows.get(0), "email_address"));
        assertEquals(2, rows.get(0).size());
    }

    @Test
    void emptySingleColumnListReturnsEmptyList() {
        List<String> emails = MetaFactory.sql(
                        "SELECT email_address FROM platform_user_email_account WHERE user_id = ?")
                .param("NOPE")
                .list();
        assertNotNull(emails);
        assertTrue(emails.isEmpty());
    }

    @Test
    void noRowOneReturnsNull() {
        String email = MetaFactory.sql(
                        "SELECT email_address FROM platform_user_email_account WHERE user_id = ?")
                .param("NOPE")
                .one();
        assertNull(email);
    }

    @Test
    void singleColumnNullValueYieldsNullElement() {
        List<String> emails = MetaFactory.sql(
                        "SELECT email_address FROM platform_user_email_account WHERE user_id = ?")
                .param("U1002")
                .list();
        assertEquals(1, emails.size());
        assertNull(emails.get(0));
    }

    @Test
    void wrapperResultTakesPrecedenceOverAutoUnwrap() {
        List<Map<String, Object>> rows = MetaFactory.sql(
                        "SELECT email_address FROM platform_user_email_account WHERE user_id = ?")
                .param("U1001")
                .wrapperResult(row -> row)
                .list();
        assertEquals(2, rows.size());
        assertEquals("a@geelato.com", columnValue(rows.get(0), "email_address"));
    }

    // ==================== 原生 SQL:显式类型 list(Class)/one(Class) ====================

    @Test
    void listWithSimpleTypeConvertsValue() {
        List<String> ids = MetaFactory.sql("SELECT id FROM platform_user_email_account ORDER BY id")
                .list(String.class);
        assertEquals(List.of("1", "2", "3"), ids);
        List<Integer> delStatuses = MetaFactory.sql(
                        "SELECT del_status FROM platform_user_email_account WHERE user_id = ?")
                .param("U1001")
                .list(Integer.class);
        assertEquals(List.of(0, 0), delStatuses);
    }

    @Test
    void listWithSimpleTypeRejectsMultiColumn() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> MetaFactory.sql("SELECT id, email_address FROM platform_user_email_account WHERE user_id = ?")
                        .param("U1001")
                        .list(String.class));
        String message = exception.getMessage().toLowerCase();
        assertTrue(message.contains("id"));
        assertTrue(message.contains("email_address"));
    }

    @Test
    void listWithEmptyResultReturnsEmptyList() {
        List<String> ids = MetaFactory.sql("SELECT id FROM platform_user_email_account WHERE user_id = ?")
                .param("NOPE")
                .list(String.class);
        assertNotNull(ids);
        assertTrue(ids.isEmpty());
    }

    @Test
    void oneWithSimpleTypeConvertsValue() {
        Integer delStatus = MetaFactory.sql(
                        "SELECT del_status FROM platform_user_email_account WHERE user_id = ? ORDER BY id LIMIT 1")
                .param("U1001")
                .one(Integer.class);
        assertEquals(0, delStatus);
        assertNull(MetaFactory.sql("SELECT del_status FROM platform_user_email_account WHERE user_id = ?")
                .param("NOPE")
                .one(Integer.class));
    }

    @Test
    void listWithDtoClassMapsRowsIncludingSnakeCaseColumns() {
        List<EmailAccountDto> accounts = MetaFactory.sql(
                        "SELECT id, user_id, email_address, del_status FROM platform_user_email_account WHERE user_id = ? ORDER BY id")
                .param("U1001")
                .list(EmailAccountDto.class);
        assertEquals(2, accounts.size());
        assertEquals(1L, accounts.get(0).getId());
        assertEquals("U1001", accounts.get(0).getUserId());
        assertEquals("a@geelato.com", accounts.get(0).getEmailAddress());
        assertEquals(0, accounts.get(0).getDelStatus());
    }

    @Test
    void oneWithDtoClassMapsRow() {
        EmailAccountDto account = MetaFactory.sql(
                        "SELECT id, user_id, email_address, del_status FROM platform_user_email_account WHERE email_address = ?")
                .param("b@geelato.com")
                .one(EmailAccountDto.class);
        assertNotNull(account);
        assertEquals("b@geelato.com", account.getEmailAddress());
        assertEquals("U1001", account.getUserId());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listWithMapClassReturnsRowsAsIs() {
        List<Map> rows = MetaFactory.sql(
                        "SELECT email_address FROM platform_user_email_account WHERE user_id = ?")
                .param("U1001")
                .list(Map.class);
        assertEquals(2, rows.size());
        assertInstanceOf(Map.class, rows.get(0));
        assertEquals("a@geelato.com", columnValue(rows.get(0), "email_address"));
    }

    // ==================== 元数据查询 MetaQuery ====================

    @Test
    void metaQuerySingleFieldListReturnsBareValues() {
        List<String> names = MetaFactory.query(TestUserEntity.class)
                .select(new String[]{"name"})
                .where(Filter.eq("delStatus", 0))
                .list();
        assertEquals(2, names.size());
        assertTrue(names.contains("Alice") && names.contains("Bob"));
    }

    @Test
    void metaQuerySingleFieldPageUnwrapsRecords() {
        PageResult<String> page = MetaFactory.query(TestUserEntity.class)
                .select(new String[]{"name"})
                .where(Filter.eq("delStatus", 0))
                .page(1, 10)
                .page();
        assertEquals(2, page.getTotal());
        assertEquals(2, page.getRecords().size());
        assertTrue(page.getRecords().contains("Alice") && page.getRecords().contains("Bob"));
    }

    @Test
    void metaQueryListWithDtoClassMapsRows() {
        List<TestUserRow> users = MetaFactory.query(TestUserEntity.class)
                .where(Filter.eq("delStatus", 0))
                .list(TestUserRow.class);
        assertEquals(2, users.size());
        assertTrue(users.stream().anyMatch(user -> "Alice".equals(user.getName()) && Integer.valueOf(0).equals(user.getDelStatus())));
    }

    @Test
    void metaQueryOneWithDtoClassMapsRow() {
        TestUserRow user = MetaFactory.query(TestUserEntity.class)
                .where(Filter.eq("id", 1))
                .one(TestUserRow.class);
        assertNotNull(user);
        assertEquals("Alice", user.getName());
    }
}
