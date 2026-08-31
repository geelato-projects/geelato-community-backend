---
title: ORM / 数据源扩展
sidebar_label: ORM / 数据源扩展
---

# ORM / 数据源扩展

本页说明 ORM 的数据源绑定、运行时切换数据源，以及查询过滤与字段填充的 SPI 扩展入口，涵盖三类常见任务：

- 让 ORM 正常绑定到正确的 `Dao`。
- 在运行时切换数据源。
- 通过 SPI 扩展查询过滤和字段填充规则。

若需先理解动态数据源能力本身的工作机制，建议先阅读 [动态数据源](../dynamic-datasource/overview.md)。

## 明确场景

修改配置前，先明确要处理的场景，避免按错入口：

- 仅让 Fluent DSL 某条查询走指定数据源：使用 `.useDataSource(...)`。
- 让一个 Service 或方法整体切到某个动态数据源：使用 `@UseDynamicDataSource`。
- 替换动态数据源定义来源：实现 `DynamicDataSourceDefinitionLoader`。
- 给查询自动加租户/权限条件，或给保存自动补默认字段：实现对应 SPI。

这四类能力相关但并非一回事，选错入口易导致配置混乱。

## ORM 当前入口

当前 ORM 自动装配入口是 `OrmAutoConfiguration`，核心关注点有两个：

- 创建 `MetaCommandExecutor`
- 创建 `SaveDefaultValueFiller`

其中 `MetaCommandExecutor` 的前提是宿主工程中存在 `Dao` Bean。

## 最短接入路径

如果你正在新接一个宿主工程，建议按下面顺序做。

### 第 1 步：先让宿主工程里存在 `Dao`

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

没有 `Dao`，ORM 的主要执行链路就没有稳定落点。

### 第 2 步：确认 ORM 绑定的是哪一个 `Dao`

ORM 自动解析优先绑定 `dynamicDao`（只有它能支撑 `.useDataSource(...)` 切库），不存在时回退 `primaryDao`，再回退唯一的 Dao Bean——多 `Dao` 场景无需显式配置。仅当需要绑定其他自定义 `Dao` 时才配置：

```properties
geelato.orm.dao-bean-name=myDao
```

### 第 3 步：再决定是否需要动态数据源

不是所有项目都需要动态数据源。

只有当你存在下面这些需求时，才继续往下做：

- 同一套服务代码要访问多个库
- 某些查询/存储过程要按 key 切换到其他库
- 宿主工程需要自己管理动态库清单

## ORM 绑定哪个 Dao

未配置时，ORM 自动解析优先级是：

1. `dynamicDao`（基于路由数据源，唯一能支撑 `.useDataSource(...)` / 实体 `connectId` 切库的 Dao）
2. `primaryDao`
3. 唯一的 Dao Bean

即只要容器中存在 `dynamicDao`，ORM 必绑定它；多 `Dao` 场景无需显式配置。若宿主工程配置了 `geelato.orm.dao-bean-name`，则显式配置优先，用于绑定其他自定义 `Dao` Bean。

## Starter 默认创建哪些 JDBC Bean

在存在 `spring.datasource.primary.jdbc-url` 时，Starter 默认创建：

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao`
- `dbGenerateDao`

如果还配置了 `spring.datasource.secondary.jdbc-url`，则继续创建：

- `secondaryDataSource`
- `secondaryJdbcTemplate`
- `secondaryDao`

## 动态数据源扩展点

动态数据源当前的显式配置前缀是：

```properties
geelato.datasource.dynamic.*
```

## 最小启用动态数据源（宿主工程）

动态数据源相关 Bean 的命名约定如下（用于排障与覆盖）：

- 前置：`primaryJdbcTemplate`
- 自动装配的动态数据源 Bean：
  - `dynamicDataSource`
  - `dynamicJdbcTemplate`
  - `dynamicDao`

典型工程中，`primaryJdbcTemplate` 由 Starter 提供；如果你没有使用 Starter，也可以在业务工程里自行提供 `primaryJdbcTemplate`（例如基于 Spring Boot 的主数据源构建 `JdbcTemplate`），从而触发动态数据源自动装配。

默认重要属性包括：

- `delay-load-data-source=true`
- `enable-jta-transaction=false`
- `enable-seata-proxy=false`
- 默认连接池参数与 `connection-test-query=SELECT 1`

## 如何切换数据源

这是最常见的部分。建议先从最轻量的用法开始。

### 方式 1：在 Fluent DSL 中显式切换

这适合“只想让某一条 ORM 查询或过程调用切到指定源”的场景。

查询示例：

```java
List<Map<String, Object>> rows = MetaFactory.query("DevDbConnect")
        .useDataSource("portal")
        .page(1, 10)
        .list();
```

存储过程示例：

```java
List<Map<String, Object>> rows = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .useDataSource("portal")
        .list();
```

SQL 直通示例：

```java
List<Map<String, Object>> rows = MetaFactory.sql("select id, name from platform_user where del_status = ?")
        .param(0)
        .useDataSource("portal")
        .list();
```

如果你的诉求只是“这条链路切源”，优先用这种方式，最直接。

### 方式 2：用 `@UseDynamicDataSource` 控制类、方法或字段

`@UseDynamicDataSource` 适合更偏组件级的场景。

它可以标在：

- 类
- 方法
- 字段

最简单的类级示例：

```java
@Service
@UseDynamicDataSource("portal")
public class PortalUserService {
}
```

方法级示例：

```java
@Service
public class PortalUserService {

    @UseDynamicDataSource("portal")
    public void syncUsers() {
    }
}
```

它还支持实体与数据源的映射配置：

```java
@UseDynamicDataSource(
        value = "primary",
        mappings = {
                @UseDynamicDataSource.EntitySourceMapping(entityName = "Order", dataSource = "portal"),
                @UseDynamicDataSource.EntitySourceMapping(entityName = "Customer", dataSource = "crm")
        }
)
public class SyncService {
}
```

如果你希望某类方法整体使用同一数据源，或者希望注入 `dynamicDao` 体系，`@UseDynamicDataSource` 会比每条查询都写 `.useDataSource(...)` 更省事。

### 方式 3：让 ORM 默认绑定动态源的 `Dao`

如果你的项目里大多数 ORM 操作都应落到动态源链路，无需任何配置：只要容器中存在 `dynamicDao`，ORM 会自动绑定它，Fluent DSL 默认就会走动态源能力（`useDataSource` 切库生效），而不是每次都手工指定。

### 方式 4：用 `@Entity` 注解声明实体所属数据源

前三种方式都是在“调用时”或“组件级”决定走哪个数据源。如果你希望一个实体的数据源归属是**固定的、写在实体声明里**的，就用 `@Entity` 注解来声明。

它适合：

- 某个实体天然属于另一个库（如订单实体固定在订单库）
- 一批实体按模块归到不同库（如 `email` 分组统一走邮件库）
- 不想在每个 Service 或每条查询上重复指定数据源

这种方式与前三种属于不同层级，可以叠加：注解声明的是实体“默认归属”，`.useDataSource(...)` 仍可在单次调用时覆盖。

#### 路径 A：用 `connectId` 显式指定

直接在实体上声明它走哪个数据源，优先级最高：

```java
@Entity(name = "demo_order", table = "t_demo_order", connectId = "order_db")
public class DemoOrder {
    // ...
}
```

此后对该实体的所有 ORM 操作都会自动路由到 `order_db` 数据源，无需在调用处再切源。

#### 路径 B：用 `catalog` 分组 + 配置映射

如果一批实体同属一个库，逐个标 `connectId` 较繁琐。可以给它们标同一个 `catalog`，再通过配置把 `catalog` 映射到数据源：

```java
// 一组实体都标同一个 catalog
@Entity(name = "demo_order", catalog = "business")
public class DemoOrder { }

@Entity(name = "demo_order_item", catalog = "business")
public class DemoOrderItem { }
```

然后在配置里建立 `catalog` 到数据源的映射：

```properties
geelato.datasource.dynamic.catalog-mapping.platform=primary
geelato.datasource.dynamic.catalog-mapping.business=order_db
```

等价的 YAML 写法：

```yaml
geelato:
  datasource:
    dynamic:
      catalog-mapping:
        platform: primary
        business: order_db
```

这样的好处是：换库时只改配置，不改代码。`business` 分组下的所有实体一起切到新的数据源。

#### 解析优先级

实体最终走哪个数据源，按以下顺序确定（高 → 低）：

1. `@Entity(connectId)` 显式指定
2. `@Entity(catalog)` 在 `catalog-mapping` 中的映射值
3. 数据库元数据表 `platform_dev_table.connect_id` 登记值
4. 默认数据源 `primary`

优先级在运行期由 `MetaManager.resolveConnectId` 即时解析，因此配置何时注入都不影响结果。

#### 注意事项

- 映射指向的数据源 key 必须是已注册的动态数据源（即在 `platform_dev_db_connect` 中登记，或通过自定义加载器提供）。
- `catalog` 值为 `platform` 的系统实体，其既有保护逻辑不变（强制以 Java 类为准、禁止刷新、禁止移交）；建议在 `catalog-mapping` 中将 `platform` 映射到 `primary`，保持与系统库一致。
- 解析在查询期即时完成，与实体扫描时序无关，无需关心框架启动阶段的先后顺序。

## 为什么要抽象数据源来源

和元数据来源（`MetaStore`）一样，动态数据源的**定义来源**也被抽象成了 SPI。理解它的设计动机，能帮你判断是否需要替换。

### 之前：数据源定义硬绑平台表

早期版本里，动态数据源的连接信息写死在 `platform_dev_db_connect` 表里——默认加载器直接 `select * from platform_dev_db_connect`，把每行转成一个数据源定义。这把框架层（`geelato-orm`）与具体表结构耦合在一起，带来三个真实痛点：

| 痛点 | 场景 |
| --- | --- |
| **多环境难统一管控** | 开发 / 测试 / 生产的数据源配置散落在各自数据库里，改一个连接要登库改 SQL，没有审计、没有版本。 |
| **配置中心用不上** | 想用 Nacos / Apollo 集中管理、动态推送数据源清单，得绕开框架自己造一套。 |
| **轻量项目被迫上平台表** | 一个只想用多数据源能力的小服务，却要先引入一整套平台表，代价过高。 |

### 设计决策：把“数据源从哪来”抽象成 SPI

我们把加载逻辑抽象成 `DynamicDataSourceDefinitionLoader` SPI（位于 `geelato-orm` 的 `cn.geelato.datasource.spi` 包），“从平台表读”降级为默认实现 `PlatformDynamicDataSourceDefinitionLoader`（迁移到业务层 `geelato-web-platform`）。

关键设计：`DynamicDataSourceRegistry` 对 loader 用 `@Nullable` 注入——**不提供任何 loader 时，框架只走 `primary` / `secondary`，仍可独立运行**。这与 `MetaStore` 的“可无 DB 运行”理念一致。

## 对使用者有什么好处

1. **配置中心统一管理**：Nacos / Apollo 管多环境数据源，改配置即生效（配合下面的热刷新），不用登库改 SQL。
2. **零改框架**：实现接口 + `@Primary`，不动框架一行代码。
3. **不必引入平台表**：文件 / 内存起步，按需升级。
4. **运维友好**：换库、扩库不改代码、不重启（host 映射 + 两步热刷新）。
5. **能力全继承**：懒加载建池、连接池调优、Seata / JTA、实体→数据源自动路由，换来源后**全部自动复用**。

## 默认数据源定义来源

当前默认动态数据源定义加载器是 `PlatformDynamicDataSourceDefinitionLoader`（业务层 `geelato-web-platform`），它从 `platform_dev_db_connect` 表读取连接信息。

因为加载器是可选注入的，**不提供自定义实现时默认走它；提供了就替换它**。

## 如何覆盖动态数据源定义来源

宿主工程只要提供自己的 `DynamicDataSourceDefinitionLoader`，就可以替换默认加载逻辑。

### 让自定义实现生效：必须加 `@Primary`

这与 [MetaStore 扩展](../reference/metastore-extension.md) 里的覆盖机制**完全同构**，也是最容易踩的坑：

- 默认 `PlatformDynamicDataSourceDefinitionLoader` 是 `@Component`，且**没有** `@Primary`；
- `DynamicDataSourceRegistry` 的注入点是构造器参数 `@Nullable DynamicDataSourceDefinitionLoader definitionLoader`，**无 `@Qualifier`**；
- 直接写 `@Component implements DynamicDataSourceDefinitionLoader` → 容器里两个候选 → 启动报 `NoUniqueBeanDefinitionException`。

正确写法是在你的实现上加 `@Primary`：

```java
@Component
@Primary
public class MyDataSourceDefinitionLoader implements DynamicDataSourceDefinitionLoader {
    // ...
}
```

:::warning
不要靠 Bean 同名覆盖（`spring.main.allow-bean-definition-overriding` 默认 `false`）。开全局覆盖有副作用，不推荐。
:::

### 契约：接口与 Map 字段约定

```java
public interface DynamicDataSourceDefinitionLoader {
    List<Map<String, Object>> loadAll();   // 返回所有数据源定义；每个 Map 的 "id" 即路由 key
    Map<String, Object> loadOne(String key); // 按路由 key 加载单个（用于热刷新）
}
```

`loadAll()` 返回的每个 `Map<String, Object>` 必须包含以下字段（snake_case，与 `platform_dev_db_connect` 表字段一致）：

| 字段 | 必需 | 说明 |
| --- | --- | --- |
| `id` | **必需** | 数据源路由 key（`.useDataSource(id)`、`@Entity(connectId)` 用的就是它） |
| `db_type` | **必需** | 数据库类型，见下表 |
| `db_hostname_ip` | **必需** | 主机地址（可被 [host 映射](../dynamic-datasource/host-mapping.md) 重定向） |
| `db_port` | **必需** | 端口 |
| `db_user_name` | **必需** | 用户名 |
| `db_name` | **必需** | 库名 / 模式名 |
| `db_password` | 可选 | 密码，见下方加密约定 |

**`db_type` 支持的值**：

| db_type | 驱动 | 说明 |
| --- | --- | --- |
| `mysql` | `com.mysql.cj.jdbc.Driver` | 大小写不敏感 |
| `postgresql` / `postgres` | `org.postgresql.Driver` | 两种写法等价 |
| 其他 | — | 抛 `UnsupportedOperationException: 不支持的数据库类型` |

**密码加密约定**：明文可以直接写；若要加密，格式必须是 `算法:密文`，算法取值 `aes` / `rsa` / `sm2` / `sm4`（框架按前缀自动选择解密算法）。不带 `算法:` 前缀的值一律按明文透传。

### 示例 A：内存构造（最小演示）

> **解决场景**：快速验证“自定义来源是否被装配”，或做最小可运行 demo。

```java
package com.acme.platform.ds;

import cn.geelato.datasource.spi.DynamicDataSourceDefinitionLoader;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Primary
public class InMemoryDataSourceLoader implements DynamicDataSourceDefinitionLoader {

    @Override
    public List<Map<String, Object>> loadAll() {
        Map<String, Object> ds = new HashMap<>();
        ds.put("id", "biz-mysql");            // 路由 key（必需）
        ds.put("db_type", "mysql");           // 必需
        ds.put("db_hostname_ip", "10.0.0.10");// 必需
        ds.put("db_port", 3306);              // 必需
        ds.put("db_user_name", "biz_user");   // 必需
        ds.put("db_name", "biz_db");          // 必需
        ds.put("db_password", "biz@psd");     // 可选，这里用明文
        return List.of(ds);
    }

    @Override
    public Map<String, Object> loadOne(String key) {
        return loadAll().stream()
                .filter(m -> key.equals(String.valueOf(m.get("id"))))
                .findFirst().orElse(null);
    }
}
```

**预期结果**：启动后 `.useDataSource("biz-mysql")` 能路由到一个 HikariCP 连接池，日志可见 `HikariPool-... poolName=biz-mysql`。

### 示例 B：从 JSON / YAML 文件加载（最常用）

> **解决场景**：用文件管理数据源清单，随应用打包；多环境用不同 profile 的文件。

先准备 `src/main/resources/datasource/biz-mysql.json`：

```json
{
  "id": "biz-mysql",
  "dbType": "mysql",
  "host": "10.0.0.10",
  "port": 3306,
  "userName": "biz_user",
  "dbName": "biz_db",
  "password": "sm4:Y3yK9+...加密串..."
}
```

再实现加载器，启动时把 `classpath:datasource/*.json` 全部读入：

```java
package com.acme.platform.ds;

import cn.geelato.datasource.spi.DynamicDataSourceDefinitionLoader;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Primary
public class JsonFileDataSourceLoader implements DynamicDataSourceDefinitionLoader {

    private final List<Map<String, Object>> definitions = new ArrayList<>();

    public JsonFileDataSourceLoader() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        for (Resource res : resolver.getResources("classpath:datasource/*.json")) {
            try (InputStream in = res.getInputStream()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject json = JSON.parseObject(text);

                // 注意：Map 的 key 必须是框架约定的 snake_case
                Map<String, Object> ds = new HashMap<>();
                ds.put("id", json.getString("id"));
                ds.put("db_type", json.getString("dbType"));
                ds.put("db_hostname_ip", json.getString("host"));
                ds.put("db_port", json.getIntValue("port"));
                ds.put("db_user_name", json.getString("userName"));
                ds.put("db_name", json.getString("dbName"));
                ds.put("db_password", json.getString("password")); // 支持 sm4:xxx 加密写法
                definitions.add(ds);
            }
        }
    }

    @Override
    public List<Map<String, Object>> loadAll() {
        return definitions;
    }

    @Override
    public Map<String, Object> loadOne(String key) {
        return definitions.stream()
                .filter(m -> key.equals(String.valueOf(m.get("id"))))
                .findFirst().orElse(null);
    }
}
```

**预期结果**：把新的 `*.json` 丢进 `datasource/` 目录即自动成为可用数据源。

### 示例 C：从外部 HTTP API 加载

> **解决场景**：企业内部有统一的“数据库连接注册中心”，应用启动时拉取清单，保持单一事实源。

```java
package com.acme.platform.ds;

import cn.geelato.datasource.spi.DynamicDataSourceDefinitionLoader;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 约定接口直接返回符合字段约定的数组：
 * [{ "id": "biz-mysql", "db_type": "mysql", "db_hostname_ip": "...", ... }]
 */
@Component
@Primary
public class HttpApiDataSourceLoader implements DynamicDataSourceDefinitionLoader {

    private final RestClient client = RestClient.builder()
            .baseUrl("http://ds-registry.internal.svc")
            .build();

    @Override
    public List<Map<String, Object>> loadAll() {
        return client.get()
                .uri("/api/datasources")
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    @Override
    public Map<String, Object> loadOne(String key) {
        return loadAll().stream()
                .filter(m -> key.equals(String.valueOf(m.get("id"))))
                .findFirst().orElse(null);
    }
}
```

**预期结果**：注册中心新增一个数据源并发布后，应用重启或触发热刷新即可使用。

### 验证：触发一次真实查询

实现加载器后，不要只看 Spring 启动成功，建议立刻跑一条真实查询：

```java
List<Map<String, Object>> rows = MetaFactory.query("DevDbConnect")
        .useDataSource("biz-mysql")
        .page(1, 1)
        .list();
```

如果这里能成功执行，再说明“定义加载 + 数据源切换 + ORM 执行链路”是通的。

## 运行时热刷新

数据源定义变更后，**不必重启应用**。框架提供了两个刷新入口，**两步缺一不可**：

```java
@Autowired(required = false)
private cn.geelato.datasource.DynamicDataSourceRegistry dynamicDataSourceRegistry;

@Autowired(required = false)
@org.springframework.beans.factory.annotation.Qualifier("dynamicDataSource")
private cn.geelato.datasource.DynamicRoutingDataSource dynamicRoutingDataSource;

public void refresh(String key) {
    // 第 1 步：重建该 key 的配置与连接池
    boolean ok = dynamicDataSourceRegistry.refreshDataSource(key);
    // 第 2 步：刷新路由表，让 DynamicRoutingDataSource 感知到变化
    if (ok) {
        dynamicRoutingDataSource.refreshDataSource(key);
    }
}
```

- `DynamicDataSourceRegistry.refreshDataSource(key)` / `refreshAllDataSources()`：重建配置（必要时重建连接池）。
- `DynamicRoutingDataSource.refreshDataSource(key)` / `refreshAllDataSources()`：重新组装路由表并让 Spring 重算。

:::warning
只调第一步、不调第二步，是“改了来源但切源没生效”的最常见原因——配置变了，路由表还指向旧池子。
:::

### 不想替换 loader？直接注册已构建的 DataSource

如果你只是想在代码里临时加一个数据源（例如基于 `application.properties` 配置、或从连接池工厂直接拿到一个 `DataSource`），可以绕过 loader，直接注册：

```java
dynamicDataSourceRegistry.registerDataSource("my-key", alreadyBuiltDataSource);
dynamicRoutingDataSource.refreshAllDataSources();
```

这种方式不依赖任何 loader 实现，适合“少量、静态、启动期可知”的数据源。

## 如何实现 SPI

数据源扩展经常和平台规则扩展一起出现。

例如：

- 切到某个租户数据源后，还希望自动加租户过滤
- 某些保存链路希望自动补齐租户编码、创建人、更新时间

这类不要写死在业务 Service 里，更推荐实现 SPI。

### 先决定该实现哪一个 SPI

按入口选择：

- 想影响 Fluent DSL 查询：实现 `FluentQueryFilterInjector`
- 想影响 Fluent DSL 保存：实现 `FluentSaveFieldValueFiller`
- 想影响 MQL 查询：实现 `MqlQueryFilterInjector`
- 想影响 MQL 保存：实现 `MqlSaveFieldValueFiller`

### 示例 1：实现 Fluent DSL 查询过滤 SPI

```java
@Component
public class DemoFluentQueryFilterInjector implements FluentQueryFilterInjector {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void inject(QueryCommand command, MetaQuery query) {
        // 这里向 Fluent DSL 查询命令注入平台级过滤条件
    }
}
```

### 示例 2：实现 Fluent DSL 保存字段填充 SPI

```java
@Component
public class DemoFluentSaveFieldValueFiller implements FluentSaveFieldValueFiller {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(FluentSaveFieldValueFillContext context) {
        // 这里补齐默认保存字段
    }
}
```

### SPI 的运行时规则一定要记住

每一类 SPI 都遵循同一条关键规则：

1. `0` 个实现：跳过
2. `1` 个实现：按 `isEnabled()` 决定是否执行
3. 多个实现：直接抛异常

这意味着：

- 不要同时启用两个 `FluentQueryFilterInjector`
- 不要同时启用两个 `FluentSaveFieldValueFiller`
- 如果项目里有多个候选实现，必须在上层收敛成唯一启用实现

更完整的 SPI 说明见 [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)。

## JTA / Seata 何时开启

当前 JTA 和 Seata 都不是默认开启能力。

只有显式配置后才进入相应能力链路，例如：

```properties
geelato.datasource.dynamic.enable-jta-transaction=true
```

因此最小骨架和普通业务工程默认不会被重事务依赖污染。

## 推荐的实操顺序

如果你要在一个新项目里同时接 ORM、动态数据源和平台规则，推荐顺序是：

1. 先把 `primaryDao` 或其他基础 `Dao` 跑通
2. 再确认 ORM 自动绑定到了 `dynamicDao`（存在时必绑定；绑定 `primaryDao` 时切库不生效）
3. 再启用动态数据源并验证 `.useDataSource(...)`
4. 再视需要补 `@UseDynamicDataSource`
5. 最后再实现查询过滤和字段填充 SPI

这样做的好处是，一旦出问题，你能快速判断到底是：

- `Dao` 没配好
- 动态源没生效
- 数据源定义没加载到
- SPI 扩展写错了

## 一步一步排障

如果你感觉“切源没生效”或“SPI 没生效”，建议按这个顺序查：

1. 看容器里是否真的存在 `primaryDao` / `dynamicDao`
2. 看 ORM 绑定的是否是 `dynamicDao`（未显式配置 `geelato.orm.dao-bean-name` 时应自动绑定它）
3. 看动态源定义是否真的能加载出目标 key
4. 看代码里是否真的调用了 `.useDataSource("...")` 或命中了 `@UseDynamicDataSource`
5. 看同类 SPI 是否注册了多个实现
6. 看 SPI 的 `isEnabled()` 是否返回 `true`
7. 自定义 loader 启动报 `NoUniqueBeanDefinitionException` → 实现类漏了 `@Primary`
8. 报 `UnsupportedOperationException: 不支持的数据库类型` → Map 的 `db_type` 不是 `mysql` / `postgresql`
9. 改了来源但切源没生效 → 只刷了 `DynamicDataSourceRegistry`，没刷 `DynamicRoutingDataSource`（两步缺一不可）

## 推荐使用建议

- 常规后端 CRUD 优先使用 ORM
- 动态源定义来源优先通过扩展点覆盖，不要硬改默认实现
- 多 `Dao` 场景无需配置，ORM 自动优先绑定 `dynamicDao`；仅绑定其他自定义 `Dao` 时配置 `geelato.orm.dao-bean-name`
- 除非明确需要，否则不要默认开启 JTA / Seata
- 单条链路切源优先用 `.useDataSource(...)`
- 组件级切源再考虑 `@UseDynamicDataSource`
- 实体数据源归属固定时，优先用 `@Entity(connectId/catalog)` 在实体上声明，而非每次调用切源
- 平台级查询规则和字段规则优先用 SPI，不要散落在业务代码里

## 推荐继续阅读

- [动态数据源](../dynamic-datasource/overview.md)
- [ORM 总览](overview.md)
- [MetaStore 扩展](../reference/metastore-extension.md)
- [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)
- [新项目最小接入](../guide/minimal-integration.md)
