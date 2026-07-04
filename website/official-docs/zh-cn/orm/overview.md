# ORM

`geelato-orm` 是 Geelato Framework 在服务端提供的 ORM 模块，核心目标不是做一套重量级实体持久化框架，而是围绕元数据模型提供一条统一的后端数据访问链路。

这条链路主要由两部分组成：

- ORM 注解：负责把 Java 类声明为框架可识别的元数据实体
- Fluent DSL：负责在 Java 服务代码中以链式方式完成查询、保存、更新、删除和轻量高级查询
- ORM 事件：负责在保存和删除链路上提供可扩展的监听器钩子

## ORM 在框架中的角色

`geelato-orm` 主要解决这些问题：

- 让实体类与数据库表、列、中文标题等元数据保持统一声明
- 让后端代码可以通过 `MetaFactory` 直接发起元数据驱动的 CRUD
- 复用动态数据源、视图参数、默认审计字段填充、上下文值引用等框架能力
- 在保存和删除链路上挂接审计、镜像、校验、缓存等定制逻辑
- 避免在 Java 服务层频繁手写 MQL JSON 或重复 DAO 样板代码

## 推荐使用边界

推荐优先使用 ORM 的场景：

- 后端服务中需要按实体名或实体类做标准 CRUD
- 需要少量关联字段、分页、排序、轻量聚合查询
- 需要复用 `useDataSource(...)`、`viewParams(...)`、`ValueRefs` 等框架能力

不建议强行使用 ORM 的场景：

- 前端直连平台通用数据接口时，仍以 `MetaController + MQL` 为主
- 超复杂跨组过滤、递归 CTE、窗口函数等 SQL-first 场景
- 多结果集存储过程或高度依赖手写 `resultMap` 的 MyBatis 场景

## 三部分内容如何理解

### ORM 注解

ORM 注解负责描述“实体是什么”，例如：

- 这个类映射到哪张表
- 某个字段对应哪一列
- 哪些属性只参与内存计算、不参与落库
- 某些实体和字段在人机界面上的中文标题是什么

对应文档见 [ORM 注解说明](annotations.md)。

### Fluent DSL

Fluent DSL 负责描述“如何访问数据”，例如：

- 如何查询一条记录或一页记录
- 如何插入、更新、删除
- 如何 selectRef、join、切换数据源、调用存储过程
- 如何在写入时使用 `ValueRefs.ctx/fn/parent`

对应文档见 [Fluent DSL 指引](fluent-dsl.md)。

### ORM 事件

ORM 事件负责描述“在保存或删除发生前后，还要附加做什么”，例如：

- 保存前做领域校验
- 保存后做审计、通知、镜像表同步
- 删除前做约束校验
- 删除后做缓存和旁路索引清理

对应文档见 [ORM 事件特性](event-features.md)。

## 推荐阅读顺序

1. 先看 [ORM 注解说明](annotations.md)，理解实体元数据如何声明
2. 再看 [Fluent DSL 指引](fluent-dsl.md)，理解服务端如何访问数据
3. 再看 [ORM 事件特性](event-features.md)，理解保存和删除链路如何扩展
4. 最后结合 [核心模块说明](../reference/core-modules.md) 理解 `geelato-orm` 在整个框架中的位置

## 最小接入（独立 Spring Boot）

这部分给出最短接入路径：让一个普通 Spring Boot 工程可以直接使用 `MetaFactory` 完成 CRUD。

最小依赖：

```xml
<dependency>
  <groupId>cn.geelato</groupId>
  <artifactId>geelato-orm</artifactId>
</dependency>
```

最小 Bean（必须提供 `Dao`，ORM 才会自动装配 `MetaCommandExecutor`）：

```java
@Configuration
public class OrmDaoConfiguration {
    @Bean
    public Dao primaryDao(JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }
}
```

元数据准备：

- 默认会扫描 Spring Boot 启动类所在包及子包内的 `@Entity` 并注册到元数据管理器
- 可通过配置关闭或限定扫描范围：

```yaml
geelato:
  orm:
    entity-auto-scan-enabled: true
    entity-scan-base-packages:
      - com.example.demo.entity
```

## 自动装配包含什么

`geelato-orm` 在 Spring Boot 环境中会自动装配这些能力：

- `MetaCommandExecutor`：用于承接 Fluent DSL 的执行（前提：容器内存在 `Dao` Bean）
- `SaveDefaultValueFiller`：默认字段填充扩展点（业务侧可覆盖）
- `BeansUtils`：用于在 Fluent DSL 执行路径中获取 Spring Bean
- 实体元数据自动注册：扫描 `@Entity` 并注册到元数据管理器
- 动态数据源：当容器内存在 `primaryJdbcTemplate` 时，自动装配
  - `dynamicDataSource`
  - `dynamicJdbcTemplate`
  - `dynamicDao`

## 与其他数据访问方式的关系

- `MetaFactory + Fluent DSL`：优先用于后端 Java 服务中的标准元数据 CRUD
- `MetaController + MQL`：优先用于平台通用数据接口和前端协议侧
- `MetaFactory.sql(...)`：用于业务方已经持有完整 SQL、只想复用执行链路的场景
- MyBatis / 原生 SQL：保留给超复杂查询、复杂结果映射和数据库特性较强的场景
