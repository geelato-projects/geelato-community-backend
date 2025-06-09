//package cn.geelato.orm;
//
//import cn.geelato.orm.*;
//import cn.geelato.orm.query.*;
//import cn.geelato.orm.executor.*;
//import java.util.*;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.AfterEach;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.test.context.TestPropertySource;
//import static org.junit.jupiter.api.Assertions.*;
//
////@SpringBootTest(classes = TestApplication.class)
//@TestPropertySource(properties = {
//    "spring.datasource.url=jdbc:h2:mem:testdb",
//    "spring.datasource.driver-class-name=org.h2.Driver",
//    "spring.datasource.username=sa",
//    "spring.datasource.password="
//})
//public class SimpleQueryTest {
//
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    private QueryExecutor queryExecutor;
//
//    @BeforeEach
//    public void setUp() {
//        // 初始化查询执行器
//        queryExecutor = new JdbcTemplateQueryExecutor(jdbcTemplate);
//
//        // 创建测试表
//        createTestTables();
//
//        // 插入测试数据
//        insertTestData();
//    }
//
//    @AfterEach
//    public void tearDown() {
//        // 清理测试数据
//        cleanupTestData();
//    }
//
//    private void createTestTables() {
//        // 创建用户表
//        jdbcTemplate.execute(
//            "CREATE TABLE IF NOT EXISTS User (" +
//            "id INT PRIMARY KEY AUTO_INCREMENT, " +
//            "name VARCHAR(100), " +
//            "email VARCHAR(100), " +
//            "age INT, " +
//            "status VARCHAR(20)" +
//            ")"
//        );
//
//        // 创建产品表
//        jdbcTemplate.execute(
//            "CREATE TABLE IF NOT EXISTS Product (" +
//            "id INT PRIMARY KEY AUTO_INCREMENT, " +
//            "name VARCHAR(100), " +
//            "price DECIMAL(10,2), " +
//            "category VARCHAR(50), " +
//            "status VARCHAR(20), " +
//            "stock INT" +
//            ")"
//        );
//
//        // 创建订单表
//        jdbcTemplate.execute(
//            "CREATE TABLE IF NOT EXISTS `Order` (" +
//            "id INT PRIMARY KEY AUTO_INCREMENT, " +
//            "status VARCHAR(20), " +
//            "createTime VARCHAR(20)" +
//            ")"
//        );
//    }
//
//    private void insertTestData() {
//        // 插入用户测试数据
//        jdbcTemplate.update(
//            "INSERT INTO User (name, email, age, status) VALUES (?, ?, ?, ?)",
//            "张三", "zhangsan@example.com", 25, "active"
//        );
//        jdbcTemplate.update(
//            "INSERT INTO User (name, email, age, status) VALUES (?, ?, ?, ?)",
//            "李四", "lisi@example.com", 30, "active"
//        );
//        jdbcTemplate.update(
//            "INSERT INTO User (name, email, age, status) VALUES (?, ?, ?, ?)",
//            "王五", "wangwu@example.com", 17, "inactive"
//        );
//
//        // 插入产品测试数据
//        jdbcTemplate.update(
//            "INSERT INTO Product (name, price, category, status, stock) VALUES (?, ?, ?, ?, ?)",
//            "iPhone", 999.99, "electronics", "active", 50
//        );
//        jdbcTemplate.update(
//            "INSERT INTO Product (name, price, category, status, stock) VALUES (?, ?, ?, ?, ?)",
//            "Java编程思想", 89.99, "books", "active", 100
//        );
//        jdbcTemplate.update(
//            "INSERT INTO Product (name, price, category, status, stock) VALUES (?, ?, ?, ?, ?)",
//            "MacBook", 1999.99, "electronics", "active", 20
//        );
//
//        // 插入订单测试数据
//        jdbcTemplate.update(
//            "INSERT INTO `Order` (status, createTime) VALUES (?, ?)",
//            "completed", "2024-01-15"
//        );
//        jdbcTemplate.update(
//            "INSERT INTO `Order` (status, createTime) VALUES (?, ?)",
//            "cancelled", "2023-12-01"
//        );
//    }
//
//    private void cleanupTestData() {
//        jdbcTemplate.execute("DROP TABLE IF EXISTS User");
//        jdbcTemplate.execute("DROP TABLE IF EXISTS Product");
//        jdbcTemplate.execute("DROP TABLE IF EXISTS `Order`");
//    }
//
//    @Test
//    public void testBasicQuery() {
//        System.out.println("\n--- 测试基本查询 ---");
//
//        // 测试基本查询SQL生成
//        String sql1 = MetaFactory.query("User")
//                .select(new String[]{"id", "name", "email"})
//                .where(Filter.eq("status", "active"))
//                .order(Order.asc("name"))
//                .page(1, 10)
//                .toSql();
//
//        System.out.println("基本查询SQL: " + sql1);
//        assertNotNull(sql1);
//        assertTrue(sql1.contains("SELECT"));
//        assertTrue(sql1.contains("FROM User"));
//        assertTrue(sql1.contains("WHERE"));
//
//        // 测试实际数据库查询
//        List<Map<String, Object>> results = queryExecutor.executeQuery(
//            "SELECT id, name, email FROM User WHERE status = ? ORDER BY name LIMIT 10",
//            "active"
//        );
//
//        assertNotNull(results);
//        assertEquals(2, results.size()); // 应该有2个active用户
//
//        // 验证查询结果
//        Map<String, Object> firstUser = results.get(0);
//        assertEquals("李四", firstUser.get("name")); // 按name排序，李四在前
//        assertEquals("lisi@example.com", firstUser.get("email"));
//
//        Map<String, Object> secondUser = results.get(1);
//        assertEquals("张三", secondUser.get("name"));
//        assertEquals("zhangsan@example.com", secondUser.get("email"));
//
//        // 测试使用类的查询SQL生成
//        String sql2 = MetaFactory.query(User.class)
//                .select(new String[]{"id", "name"})
//                .where(Filter.gt("age", 18))
//                .toSql();
//
//        System.out.println("类查询SQL: " + sql2);
//        assertNotNull(sql2);
//
//        // 测试年龄大于18的用户查询
//        List<Map<String, Object>> adultUsers = queryExecutor.executeQuery(
//            "SELECT id, name FROM User WHERE age > ?",
//            18
//        );
//
//        assertNotNull(adultUsers);
//        assertEquals(2, adultUsers.size()); // 张三25岁，李四30岁
//
//        System.out.println("✓ 基本查询测试完成");
//    }
//
//    @Test
//    public void testChainedCalls() {
//        System.out.println("\n--- 测试链式调用 ---");
//
//        // 测试复杂的链式调用SQL生成
//        String complexSql = MetaFactory.query("Product")
//                .select(new String[]{"id", "name", "price", "category"})
//                .where(
//                    Filter.eq("status", "active"),
//                    Filter.gt("price", 100),
//                    Filter.in("category", "electronics", "books")
//                )
//                .order(
//                    Order.desc("price"),
//                    Order.asc("name")
//                )
//                .page(2, 20)
//                .toSql();
//
//        System.out.println("复杂链式调用SQL: " + complexSql);
//        assertNotNull(complexSql);
//
//        // 测试实际的复杂查询
//        List<Map<String, Object>> products = queryExecutor.executeQuery(
//            "SELECT id, name, price, category FROM Product WHERE status = ? AND price > ? AND category IN (?, ?) ORDER BY price DESC, name ASC",
//            "active", 100, "electronics", "books"
//        );
//
//        assertNotNull(products);
//        assertEquals(2, products.size()); // iPhone和MacBook价格都>100
//
//        // 验证排序：价格降序，名称升序
//        Map<String, Object> firstProduct = products.get(0);
//        assertEquals("MacBook", firstProduct.get("name")); // 价格最高
//
//        Map<String, Object> secondProduct = products.get(1);
//        assertEquals("iPhone", secondProduct.get("name"));
//
//        // 测试统计查询SQL生成
//        String countSql = MetaFactory.query("Order")
//                .where(
//                    Filter.eq("status", "completed"),
//                    Filter.ge("createTime", "2024-01-01")
//                )
//                .toCountSql();
//
//        System.out.println("统计查询SQL: " + countSql);
//        assertNotNull(countSql);
//
//        // 测试实际的统计查询
//        long orderCount = queryExecutor.executeCount(
//            "SELECT COUNT(*) FROM `Order` WHERE status = ? AND createTime >= ?",
//            "completed", "2024-01-01"
//        );
//
//        assertEquals(1, orderCount); // 只有一个2024年的completed订单
//
//        System.out.println("✓ 链式调用测试完成");
//    }
//
//    @Test
//    public void testWrapperResult() {
//        System.out.println("\n--- 测试包装结果功能 ---");
//
//        try {
//            // 使用注入的查询执行器
//            QueryExecutor executor = queryExecutor;
//
//            // 测试列表查询包装SQL生成
//             String listSql = MetaFactory.query("User")
//                     .select(new String[]{"id", "name", "email"})
//                     .where(Filter.eq("status", "active"))
//                     .order(Order.asc("name"))
//                     .page(1, 10)
//                     .wrapperResult((Map<String, Object> map) -> {
//                         return new User(
//                             (Integer) map.get("id"),
//                             (String) map.get("name"),
//                             (String) map.get("email")
//                         );
//                     })
//                     .toSql();
//
//            System.out.println("列表查询包装SQL: " + listSql);
//
//            // 测试单对象查询包装SQL生成
//             String singleSql = MetaFactory.query("User")
//                     .select(new String[]{"id", "name", "email"})
//                     .where(Filter.eq("id", 1))
//                     .wrapperResult((Map<String, Object> map) -> {
//                         return new User(
//                             (Integer) map.get("id"),
//                             (String) map.get("name"),
//                             (String) map.get("email")
//                         );
//                     })
//                     .toSql();
//
//            System.out.println("单对象查询包装SQL: " + singleSql);
//
//            // 测试分页查询包装SQL生成
//             String pageSql = MetaFactory.query("User")
//                     .select(new String[]{"id", "name", "email"})
//                     .where(Filter.eq("status", "active"))
//                     .page(1, 10)
//                     .wrapperResult((Map<String, Object> map) -> {
//                         return new User(
//                             (Integer) map.get("id"),
//                             (String) map.get("name"),
//                             (String) map.get("email")
//                         );
//                     })
//                     .toSql();
//
//            System.out.println("分页查询包装SQL: " + pageSql);
//
//            // 测试实际的包装结果查询
//            List<Map<String, Object>> userMaps = queryExecutor.executeQuery(
//                "SELECT id, name, email FROM User WHERE status = ? ORDER BY name LIMIT 10",
//                "active"
//            );
//
//            // 手动包装结果
//            List<User> users = new ArrayList<>();
//            for (Map<String, Object> map : userMaps) {
//                users.add(new User(
//                    (Integer) map.get("id"),
//                    (String) map.get("name"),
//                    (String) map.get("email")
//                ));
//            }
//
//            assertNotNull(users);
//            assertEquals(2, users.size());
//            assertEquals("李四", users.get(0).getName());
//            assertEquals("张三", users.get(1).getName());
//
//            System.out.println("列表查询包装: ✓");
//            System.out.println("单对象查询包装: ✓");
//            System.out.println("分页查询包装: ✓");
//            System.out.println("✓ 包装结果测试通过");
//
//        } catch (Exception e) {
//            System.out.println("✗ WrapperResult测试出现异常: " + e.getMessage());
//            e.printStackTrace();
//        }
//
//        // 测试字符串包装函数
//         String productSql = MetaFactory.query("Product")
//                 .select(new String[]{"name", "price"})
//                 .where(Filter.eq("category", "electronics"))
//                 .wrapperResult((Map<String, Object> map) -> {
//                     return "Product: " + map.get("name") + " - $" + map.get("price");
//                 })
//                 .toSql();
//
//        System.out.println("字符串包装SQL: " + productSql);
//        System.out.println("✓ 包装结果功能测试完成");
//    }
//
//    @Test
//    public void testOptionalParameters() {
//        System.out.println("\n--- 测试可选参数 ---");
//
//        // 测试1: 不传select参数
//        String sql1 = MetaFactory.query("User")
//                .where(Filter.eq("status", "active"))
//                .order(Order.asc("name"))
//                .page(1, 10)
//                .toSql();
//        System.out.println("无select参数SQL: " + sql1);
//
//        // 测试2: 不传where参数
//         String sql2 = MetaFactory.query("User")
//                 .select(new String[]{"id", "name"})
//                 .order(Order.asc("name"))
//                 .page(1, 10)
//                 .toSql();
//         System.out.println("无where参数SQL: " + sql2);
//
//         // 测试3: 不传order参数
//         String sql3 = MetaFactory.query("User")
//                 .select(new String[]{"id", "name"})
//                 .where(Filter.eq("status", "active"))
//                 .page(1, 10)
//                 .toSql();
//         System.out.println("无order参数SQL: " + sql3);
//
//         // 测试4: 不传page参数
//         String sql4 = MetaFactory.query("User")
//                 .select(new String[]{"id", "name"})
//                 .where(Filter.eq("status", "active"))
//                 .order(Order.asc("name"))
//                 .toSql();
//        System.out.println("无page参数SQL: " + sql4);
//
//        // 测试5: 只有基本查询
//        String sql5 = MetaFactory.query("User").toSql();
//        System.out.println("最简查询SQL: " + sql5);
//
//        // 测试6: 统计查询不传where参数
//        String countSql1 = MetaFactory.query("User").toCountSql();
//        System.out.println("无条件统计SQL: " + countSql1);
//
//        // 测试7: 统计查询带where参数
//        String countSql2 = MetaFactory.query("User")
//                .where(Filter.eq("status", "active"))
//                .toCountSql();
//        System.out.println("有条件统计SQL: " + countSql2);
//
//        // 测试8: 不传wrapperResult参数
//        String sql8 = MetaFactory.query("User")
//                .select(new String[]{"id", "name"})
//                .where(Filter.eq("status", "active"))
//                .toSql();
//        System.out.println("无包装函数SQL: " + sql8);
//
//        // 测试9: 组合测试 - 只有select和where
//        String sql9 = MetaFactory.query("User")
//                .select(new String[]{"id", "name"})
//                .where(Filter.eq("status", "active"))
//                .toSql();
//        System.out.println("select+where组合SQL: " + sql9);
//
//        // 测试10: 组合测试 - 只有where和order
//        String sql10 = MetaFactory.query("User")
//                .where(Filter.eq("status", "active"))
//                .order(Order.asc("name"))
//                .toSql();
//        System.out.println("where+order组合SQL: " + sql10);
//
//        // 测试11: 组合测试 - 只有select和page
//        String sql11 = MetaFactory.query("User")
//                .select(new String[]{"id", "name"})
//                .page(1, 10)
//                .toSql();
//        System.out.println("select+page组合SQL: " + sql11);
//
//        // 测试实际的可选参数查询
//        List<Map<String, Object>> allUsers = queryExecutor.executeQuery(
//            "SELECT * FROM User"
//        );
//        assertNotNull(allUsers);
//        assertEquals(3, allUsers.size()); // 总共3个用户
//
//        List<Map<String, Object>> activeUsers = queryExecutor.executeQuery(
//            "SELECT * FROM User WHERE status = ?",
//            "active"
//        );
//        assertNotNull(activeUsers);
//        assertEquals(2, activeUsers.size()); // 2个active用户
//
//        System.out.println("✓ 可选参数测试完成");
//    }
//
//    @Test
//    public void testNewFeatures() {
//        System.out.println("\n--- 测试新增功能 ---");
//
//        // 测试count方法
//        System.out.println("\n1. 测试count方法:");
//
//        // 测试实际的count查询
//        long actualCount = queryExecutor.executeCount(
//            "SELECT COUNT(*) FROM User WHERE status = ?",
//            "active"
//        );
//        assertEquals(2, actualCount);
//        System.out.println("统计结果: " + actualCount);
//
//        // 测试insert方法
//        System.out.println("\n2. 测试insert方法:");
//        String insertSql = MetaFactory.insert("User")
//                .column(new String[]{"name", "email", "status"})
//                .values(new Object[]{"赵六", "zhaoliu@example.com", "active"})
//                .toSql();
//        System.out.println("插入SQL: " + insertSql);
//        assertNotNull(insertSql);
//        assertTrue(insertSql.contains("INSERT INTO"));
//
//        // 测试实际插入
//        int insertResult = jdbcTemplate.update(
//            "INSERT INTO User (name, email, age, status) VALUES (?, ?, ?, ?)",
//            "赵六", "zhaoliu@example.com", 28, "active"
//        );
//        assertEquals(1, insertResult);
//        System.out.println("插入影响行数: " + insertResult);
//
//        // 验证插入结果
//        long newCount = queryExecutor.executeCount(
//            "SELECT COUNT(*) FROM User WHERE status = ?",
//            "active"
//        );
//        assertEquals(3, newCount); // 现在应该有3个active用户
//
//        // 测试update方法
//        System.out.println("\n3. 测试update方法:");
//        String updateSql = MetaFactory.update("User")
//                .column(new String[]{"name", "email"})
//                .values(new Object[]{"王五更新", "wangwu_updated@example.com"})
//                .where(Filter.eq("id", 1))
//                .toSql();
//        System.out.println("更新SQL: " + updateSql);
//        assertNotNull(updateSql);
//        assertTrue(updateSql.contains("UPDATE"));
//        assertTrue(updateSql.contains("SET"));
//
//        // 测试实际更新
//        int updateResult = jdbcTemplate.update(
//            "UPDATE User SET email = ? WHERE name = ?",
//            "zhangsan_updated@example.com", "张三"
//        );
//        assertEquals(1, updateResult);
//        System.out.println("更新影响行数: " + updateResult);
//
//        // 验证更新结果
//        List<Map<String, Object>> updatedUser = queryExecutor.executeQuery(
//            "SELECT email FROM User WHERE name = ?",
//            "张三"
//        );
//        assertEquals("zhangsan_updated@example.com", updatedUser.get(0).get("email"));
//
//        // 测试delete方法
//        System.out.println("\n4. 测试delete方法:");
//        String deleteSql = MetaFactory.delete("User")
//                .where(Filter.eq("status", "inactive"))
//                .toSql();
//        System.out.println("删除SQL: " + deleteSql);
//        assertNotNull(deleteSql);
//        assertTrue(deleteSql.contains("DELETE FROM"));
//
//        // 测试实际删除
//        int deleteResult = jdbcTemplate.update(
//            "DELETE FROM User WHERE status = ?",
//            "inactive"
//        );
//        assertEquals(1, deleteResult); // 删除王五
//        System.out.println("删除影响行数: " + deleteResult);
//
//        // 验证删除结果
//        long remainingCount = queryExecutor.executeCount(
//            "SELECT COUNT(*) FROM User"
//        );
//        assertEquals(3, remainingCount); // 剩余3个用户（张三、李四、赵六）
//
//        // 测试链式调用组合
//        System.out.println("\n5. 测试链式调用组合:");
//
//        // 查询 -> 统计
//        long userCount = MetaFactory.query("User")
//                .where(Filter.eq("status", "active"))
//                .count();
//        System.out.println("活跃用户数量: " + userCount);
//
//        // 复杂更新
//        String complexUpdateSql = MetaFactory.update("Product")
//                .column(new String[]{"price", "updateTime"})
//                .values(new Object[]{99.99, "2024-01-01 12:00:00"})
//                .where(
//                    Filter.eq("category", "electronics"),
//                    Filter.gt("stock", 0)
//                )
//                .toSql();
//        System.out.println("复杂更新SQL: " + complexUpdateSql);
//
//        // 复杂删除
//        String complexDeleteSql = MetaFactory.delete("Order")
//                .where(
//                    Filter.eq("status", "cancelled"),
//                    Filter.lt("createTime", "2023-01-01")
//                )
//                .toSql();
//        System.out.println("复杂删除SQL: " + complexDeleteSql);
//
//        System.out.println("✓ 新增功能测试完成");
//    }
//
//    // 测试用的User类
//    public static class User {
//        private Integer id;
//        private String name;
//        private String email;
//
//        public User(Integer id, String name, String email) {
//            this.id = id;
//            this.name = name;
//            this.email = email;
//        }
//
//        public Integer getId() { return id; }
//        public String getName() { return name; }
//        public String getEmail() { return email; }
//
//        @Override
//        public String toString() {
//            return "User{id=" + id + ", name='" + name + "', email='" + email + "'}";
//        }
//    }
//}