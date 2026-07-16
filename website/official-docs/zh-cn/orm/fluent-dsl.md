# Fluent DSL 指引

这一页说明 Geelato Framework 在后端 Java 服务中如何使用 `MetaFactory` 提供的 Fluent DSL 访问 ORM 能力。

## 目标

- 面向后端开发者提供一套 Java 风格的元数据 CRUD 入口
- 避免直接手写 MQL JSON，同时继续复用 `MetaQLManager + SqlManager + Dao` 现有内核

## 何时使用

- 需要在 Java 服务代码中按实体名或实体类做元数据查询、保存、更新、删除时使用
- 需要复用现有动态数据源、视图模板参数、`$ctx/$fn/$parent` 这类内核能力时使用

## 何时不使用

- 前端页面直接走平台通用数据接口时，继续使用 `MetaController + MQL`
- 已有服务已经稳定依赖 `BaseService + 实体类` 且没有元数据通用化诉求时，可继续沿用原模式

## 入口

字符串实体名：

```java
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name", "mobilePhone"})
        .where(Filter.eq("delStatus", 0))
        .order(Order.desc("updateAt"))
        .list();
```

实体类：

```java
List<Map<String, Object>> users = MetaFactory.query(User.class)
        .select(new String[]{"id", "name"})
        .page(1, 20)
        .list();
```

## 接入与装配

这一节把“依赖、Bean、元数据准备”串起来，确保你在独立 Spring Boot 工程里可以直接使用 Fluent DSL。

对应示例工程：

- 仓库：[geelato-hello-example](https://github.com/geelato-projects/geelato-hello-example)
- ORM 接入示例：[geelato-sample-orm](https://github.com/geelato-projects/geelato-hello-example/tree/main/geelato-sample-orm)

### 依赖

最小只需要引入：

```xml
<dependency>
  <groupId>cn.geelato</groupId>
  <artifactId>geelato-orm</artifactId>
</dependency>
```

数据库驱动（MySQL / PostgreSQL 等）由业务工程按自身数据库类型自行引入。

### Dao Bean（必须）

ORM 会在 Spring 容器中存在 `Dao` Bean 时，自动装配 `MetaCommandExecutor`，从而让 `MetaFactory.*().list/save/delete` 可执行。

最小示例：

```java
@Configuration
public class OrmDaoConfiguration {
    @Bean
    public Dao primaryDao(JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }
}
```

### 多 Dao 场景如何选

当容器存在多个 `Dao` 时，建议显式指定 ORM 绑定的 Bean 名称：

```yaml
geelato:
  orm:
    dao-bean-name: dynamicDao
```

### 元数据准备（@Entity）

默认会扫描 Spring Boot 启动类所在包及子包内所有 `@Entity` 类并注册元数据。

可通过配置开关与限定范围：

```yaml
geelato:
  orm:
    entity-auto-scan-enabled: true
    entity-scan-base-packages:
      - com.example.demo.entity
```

### 动态数据源

Fluent DSL 支持在链式调用中显式切换数据源：

```java
List<Map<String, Object>> rows = MetaFactory.query("DevDbConnect")
        .useDataSource("portal")
        .page(1, 10)
        .list();
```

当你的工程中存在名为 `primaryJdbcTemplate` 的 Bean 时，ORM 会自动装配动态数据源相关 Bean（`dynamicDataSource`、`dynamicJdbcTemplate`、`dynamicDao`），供框架内动态数据源链路使用。

## 快速开始（从 0 到 CRUD）

一个最短示例链路：

```java
@Entity(name = "TestUser", table = "test_user")
public class TestUserEntity {
    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;

    @Col(name = "name", dataType = "VARCHAR", charMaxlength = 128)
    private String name;
}
```

```java
String id = MetaFactory.insert("TestUser")
        .value("name", "Alice")
        .save();

Map<String, Object> row = MetaFactory.query("TestUser")
        .where(Filter.eq("id", id))
        .one();

MetaFactory.update("TestUser")
        .value("id", id)
        .value("name", "Bob")
        .save();

MetaFactory.delete("TestUser")
        .where(Filter.eq("id", id))
        .delete();
```

## 查询示例

单条查询：

```java
Map<String, Object> user = MetaFactory.query("User")
        .where(Filter.eq("id", "1912345678901234567"))
        .one();
```

分页查询：

```java
PageResult<Map<String, Object>> page = MetaFactory.query("User")
        .select(new String[]{"id", "name", "updateAt"})
        .where(Filter.like("name", "张"))
        .order(Order.desc("updateAt"))
        .page(1, 10)
        .page();
```

单列查询：

```java
List<String> ids = MetaFactory.query("User")
        .select(new String[]{"id"})
        .where(Filter.eq("delStatus", 0))
        .oneColumn(String.class);
```

包装结果：

```java
List<String> names = MetaFactory.query("User")
        .select(new String[]{"name"})
        .wrapperResult(row -> String.valueOf(row.get("name")))
        .list();
```

## 写入示例

新增：

```java
String userId = MetaFactory.insert("User")
        .value("name", "测试用户")
        .value("mobilePhone", "13800000000")
        .save();
```

默认字段会在保存前自动补齐：

- 新增场景默认对齐 MQL 规则，自动补 `createAt / creator / creatorName / tenantCode / buId / deptId / updateAt / updater / updaterName / deleteAt`
- 更新场景默认自动补 `updateAt / updater / updaterName`
- 这些规则已经改为通过 `FluentSaveFieldValueFiller` SPI 注入；平台默认实现位于 `geelato-web-platform`
- 运行时遵循统一规则：`0` 个实现跳过，`1` 个实现按 `isEnabled()` 决定是否执行，多实现直接报错
- 如需扩展宿主项目自己的规则，建议阅读：[查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)

更新：

```java
String userId = MetaFactory.update("User")
        .value("id", "1912345678901234567")
        .value("name", "新名称")
        .save();
```

删除：

```java
int affected = MetaFactory.delete("User")
        .where(Filter.eq("id", "1912345678901234567"))
        .delete();
```

## 高级能力

多表 join：

```java
List<Map<String, Object>> orders = MetaFactory.query("Order")
        .select(new String[]{"id", "code"})
        .selectRef("userId->name", "userName")
        .list();
```

自定义 join on：

```java
List<Map<String, Object>> rows = MetaFactory.query("Order")
        .as("o")
        .select(new String[]{"id", "code"})
        .selectExpr("u.name", "userName")
        .leftJoin("User", "u", on -> on.eqField("userId", "u.id"))
        .groupBy("id", "code", "u.name")
        .havingSql("count(*) > 0")
        .page(1, 20)
        .page();
```

动态数据源：

```java
List<Map<String, Object>> rows = MetaFactory.query("DevDbConnect")
        .useDataSource("portal")
        .page(1, 10)
        .list();
```

视图模板参数：

```java
List<Map<String, Object>> rows = MetaFactory.query("SomeViewEntity")
        .viewParams(Map.of("customerId", "C001"))
        .page(1, 10)
        .list();
```

上下文与函数值引用：

```java
String id = MetaFactory.insert("Notice")
        .value("creator", ValueRefs.ctx("userId"))
        .value("createAt", ValueRefs.fnNowDateTime())
        .save();
```

MySQL 存储过程：

```java
List<Map<String, Object>> rows = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .in("status", 1)
        .useDataSource("portal")
        .list();
```

原生 SQL 直通执行：

```java
List<Map<String, Object>> rows = MetaFactory.sql("select id, name from platform_user where del_status = ?")
        .param(0)
        .useDataSource("portal")
        .list();
```

父子嵌套保存：

```java
String parentId = MetaFactory.insert("App")
        .value("name", "demo-app")
        .child("AppVersion", child -> child
                .value("appId", ValueRefs.parent("id"))
                .value("code", "v1"))
        .save();
```

## 多表 join 补充说明

- 自动关联 join 适合“主实体存在外键元数据”的场景，`selectRef("userId->name", "userName")` 的含义是主表外键字段映射到关联表字段，并输出为结果别名
- 自动关联 join 会基于元数据外键自动补 `left join`，业务侧不需要手写 `on`
- 若只写 `selectRef("userId->name")`，结果列名默认沿用远端字段名；建议在接口对外返回时显式设置别名，避免与主表字段重名

```java
List<Map<String, Object>> orders = MetaFactory.query("Order")
        .select(new String[]{"id", "code", "amount"})
        .selectRef("userId->name", "userName")
        .selectRef("userId->mobilePhone", "userMobile")
        .where(Filter.eq("delStatus", 0))
        .order(Order.desc("updateAt"))
        .list();
```

- 自定义 join 适合“没有外键元数据”或“需要显式控制关联方式”的场景
- 推荐固定一套别名约定：主表先调用 `.as("o")`，关联表使用短别名，如 `"u"`、`"d"`、`"t"`
- `selectExpr(...)`、`groupBy(...)`、`havingSql(...)` 中都使用同一套别名
- `leftJoin/innerJoin/rightJoin` 的 `on` 当前推荐优先使用 `eqField(...)`

```java
List<Map<String, Object>> rows = MetaFactory.query("Order")
        .as("o")
        .select(new String[]{"id", "code"})
        .selectExpr("u.name", "userName")
        .selectExpr("d.name", "deptName")
        .leftJoin("User", "u", on -> on.eqField("o.userId", "u.id"))
        .leftJoin("Dept", "d", on -> on.eqField("u.deptId", "d.id"))
        .groupBy("id", "code", "u.name", "d.name")
        .havingSql("count(*) > 0")
        .page(1, 20)
        .page();
```

## 存储过程补充说明

- 当前存储过程能力定位为 MySQL 场景下的轻量调用封装
- 适用于过程只接收 `IN` 参数、只返回单个结果集、业务侧主要通过 `list()` 或 `one()` 读取结果的场景
- 参数通过 `.in(name, value)` 按调用顺序加入，占位符顺序与 `.in(...)` 的书写顺序一致

```java
Map<String, Object> row = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .in("status", 1)
        .one();
```

如果过程结果需要直接转换成对象，可继续复用 `wrapperResult(...)`：

```java
List<OrderSimpleDto> rows = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .in("status", 1)
        .wrapperResult(row -> {
            OrderSimpleDto dto = new OrderSimpleDto();
            dto.setId(String.valueOf(row.get("id")));
            dto.setCode(String.valueOf(row.get("code")));
            return dto;
        })
        .list();
```

## 原生 SQL 直通补充说明

- 当业务侧已经持有完整 SQL，且不希望再拆分成 `MetaQuery` / `MetaInsert` / `MetaUpdate` 时，可使用 `MetaFactory.sql(...)`
- 该入口属于“直通执行”能力，ORM 只负责动态数据源切换、参数顺序绑定，以及 `list()/one()/queryForObject()/execute()` 终止执行
- 该入口不会为原生 SQL 自动补元数据字段、外键 join、默认审计字段或条件表达式转换

```java
Map<String, Object> row = MetaFactory.sql("select id, name from platform_user where id = ?")
        .param("U1001")
        .one();
```

```java
Long total = MetaFactory.sql("select count(1) from platform_user where del_status = ?")
        .param(0)
        .queryForObject(Long.class);
```

```java
int affected = MetaFactory.sql("update platform_notice set status = ? where id = ?")
        .params("read", "N1001")
        .execute();
```

## 调试与排障

- `toSql()` 用于查看当前查询或存储过程最终生成的 SQL
- `toCountSql()` 只适用于分页查询，常用于排查分页总数不准确的问题
- 排查 join 问题时，优先检查主表别名、join 别名、`selectExpr/groupBy/havingSql` 是否一致
- 排查存储过程问题时，优先检查 `.in(...)` 参数顺序、当前数据源、过程结果集数量
- 排查原生 SQL 问题时，优先检查 SQL 本身是否可直接执行、占位符数量是否与 `.param/.params` 一致、终止方法是否选对

## 推荐使用边界

- 简单单表 CRUD、带少量关联字段的列表页、轻量聚合查询，优先使用 Fluent DSL
- 已存在外键元数据时，优先使用 `selectRef(...)`
- 需要显式控制关联表、别名和 `on` 条件时，再使用 `leftJoin/innerJoin/rightJoin`
- 已经存在成熟 SQL、报表 SQL、临时排障 SQL，且业务方明确接受“自己维护完整 SQL”时，可使用 `MetaFactory.sql(...)`
- 超复杂跨组过滤、递归 CTE、窗口函数、多结果集存储过程，继续保留 MQL / SQL Key / MyBatis

## 推荐继续阅读

- [ORM 总览](overview.md)
- [ORM 注解说明](annotations.md)
- [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)
- [核心模块说明](../reference/core-modules.md)
