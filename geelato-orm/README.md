# geelato-orm

`geelato-orm` 提供面向后端开发者的 Fluent DSL 入口，底层复用 `geelato-core` 的元数据命令执行能力。

## 快速开始

```java
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name"})
        .page(1, 10)
        .list();
```

```java
String id = MetaFactory.insert("User")
        .value("name", "测试用户")
        .save();
```

```java
String id = MetaFactory.update("User")
        .value("id", "1912345678901234567")
        .value("name", "新名称")
        .save();
```

```java
int affected = MetaFactory.delete("User")
        .where(Filter.eq("id", "1912345678901234567"))
        .delete();
```

## 独立 Spring Boot 工程最小接入
- 引入依赖：

```xml
<dependency>
  <groupId>cn.geelato</groupId>
  <artifactId>geelato-orm</artifactId>
</dependency>
```

- 配置数据源（示例）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: demo
    password: demo
    driver-class-name: com.mysql.cj.jdbc.Driver
```

- 提供 `Dao` Bean（`MetaCommandExecutor` 会在 ORM 自动装配中基于 `Dao` 创建）：

```java
@Configuration
public class OrmDaoConfiguration {
    @Bean
    public Dao primaryDao(JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }
}
```

- 元数据准备：
  - 默认会扫描 `@SpringBootApplication` 所在包及子包下所有 `@Entity` 类并自动 `MetaManager.parseOne(...)`
  - 可通过配置关闭或限定扫描范围：

```yaml
geelato:
  orm:
    entity-auto-scan-enabled: true
    entity-scan-base-packages:
      - com.example.demo.entity
```

## 主要能力
- 支持 `query("Entity")` 与 `query(Entity.class)` 双入口
- 支持分页、单条、单列、包装结果
- 支持基于外键元数据的自动关联查询与自定义 join on
- 支持 `groupBy`、`havingSql`、`selectExpr` 这类 join 场景常用能力
- 支持新增、更新、删除、批量保存
- 支持 MySQL 存储过程 `IN` 参数 + 单结果集 调用
- 支持传入完整 SQL 语句的原生直通执行
- 支持动态数据源、视图模板参数、`ValueRefs.ctx/fn/parent`
- 支持保存前默认字段自动填充，默认行为与当前 MQL 规则对齐

## Join 示例

```java
List<Map<String, Object>> orders = MetaFactory.query(Order.class)
        .select(new String[]{"id", "code"})
        .selectRef("userId->name", "userName")
        .list();
```

```java
List<Map<String, Object>> rows = MetaFactory.query(Order.class)
        .as("o")
        .select(new String[]{"id", "code"})
        .selectExpr("u.name", "userName")
        .leftJoin(User.class, "u", on -> on.eqField("userId", "u.id"))
        .groupBy("id", "code", "u.name")
        .havingSql("count(*) > 0")
        .list();
```

## 存储过程示例

```java
List<Map<String, Object>> rows = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .in("status", 1)
        .useDataSource("portal")
        .list();
```

## 原生 SQL 示例

```java
List<Map<String, Object>> rows = MetaFactory.sql("select id, name from platform_user where del_status = ?")
        .param(0)
        .useDataSource("portal")
        .list();
```

```java
int affected = MetaFactory.sql("update platform_notice set status = ? where id = ?")
        .params("read", "N1001")
        .execute();
```

## 说明
- 调试可使用 `toSql()` / `toCountSql()` 查看内核生成语句
- 原生 SQL 入口适用于业务侧已经持有完整 SQL 的场景；字段映射、别名与 SQL 安全由调用方自行保证
- 默认字段填充通过 `SaveDefaultValueFiller` 扩展点实现，框架内置默认 Bean，业务侧可覆盖
- 更完整的使用说明见 `docs/orm/backend-fluent-dsl-guide.md`
