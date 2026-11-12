---
title: 新项目最小接入
sidebar_label: 新项目最小接入
---

# 新项目最小接入

本页说明如何从零接入 Geelato Framework，在不引入平台重扩展的前提下搭建并验证一个最小 Spring Boot Web 应用。

## 前置条件

| 项 | 要求 |
| --- | --- |
| JDK | 17（`java -version` 确认） |
| Maven | 3.8+（`mvn -v` 确认） |
| Maven 仓库 | 已配置可解析 `cn.geelato` 制品的仓库与凭证，或改用本页[从源码构建](#从源码构建) |
| 端口 | `8081` 未被占用 |

注意：部分环境会注入 `SERVER__PORT` 之类的环境变量，Spring Boot 宽松绑定会使其覆盖配置文件中的 `server.port`。启动前可用 `env | grep -i SERVER` 自查，或启动命令显式追加 `--server.port=8081`。

## 制品仓库与凭证

框架当前以 `1.0.0-SNAPSHOT` 形式通过私有 Maven 仓库分发，Maven Central 上暂无可消费的 Geelato 制品。未配置仓库与凭证时，构建会在解析 BOM 时报 `Non-resolvable import POM: cn.geelato:geelato-framework-bom:pom:1.0.0-SNAPSHOT`。

仓库地址与 `settings.xml` 配置片段、凭证申请方式见[脚手架快速启动指南的「制品仓库与凭证」](app-scaffold-starter-project-guide.md#制品仓库与凭证)，两页共用同一套配置。预检命令：

```bash
mvn -B dependency:get -Dartifact=cn.geelato:geelato-framework-bom:pom:1.0.0-SNAPSHOT
```

### 从源码构建

无私服访问权限时，最小接入链路（BOM + starter 及其核心依赖，10 个自洽核心库）可从公开源码构建：

```bash
git clone --depth 1 https://github.com/geelato-projects/geelato-community-backend.git
cd geelato-community-backend
mvn -B install -DskipTests -pl geelato-framework-bom,geelato-lang,geelato-utils,geelato-security,geelato-meta,geelato-core,geelato-orm,geelato-web-common,geelato-framework-starter -am
```

构建成功后，`1.0.0-SNAPSHOT` 制品进入本地仓库，后续步骤无需私服。如构建失败请对照仓库内 `RELEASE.md` 核对模块清单。

## 推荐接入顺序

1. 引入 `geelato-framework-bom`
2. 引入 `geelato-framework-starter`
3. 参考 [`geelato-sample-quickstart`](https://github.com/geelato-projects/geelato-hello-example/tree/main/geelato-sample-quickstart) 搭建最小工程
4. 验证主库、`JdbcTemplate`、`Dao`、ORM 与示例接口连通
5. 业务需要基础后台能力时，再切到 `geelato-app-scaffold`
6. 再按需叠加 runtime、designer 或业务扩展模块

## 5 分钟完成最小验证

最快的验证方式是直接运行官方最小样例（H2 内存库，无外部依赖）：

```bash
# 0. 前置：JDK 17、Maven 3.8+；可解析 geelato 制品的仓库已配置（或已从源码构建）
git clone --depth 1 https://github.com/geelato-projects/geelato-hello-example.git
cd geelato-hello-example/geelato-sample-quickstart

# 1. 构建
mvn -B clean package

# 2. 启动（端口 8081）
java -jar target/geelato-sample-quickstart-1.0.0-SNAPSHOT.jar --server.port=8081

# 3. 验证
curl -sS http://localhost:8081/api/sample/ping
```

期望输出：

```json
{"msg":"sample quickstart is ready","code":20000,"status":"ok","data":{"app":"geelato-sample-quickstart","frameworkStarter":true,"database":"h2"},"cache":null}
```

样例共 4 个文件：独立 POM（无 parent）、启动类、一个 `/api/sample/ping` 控制器、`application.properties`。照抄到自有工程即可完成最小接入。

### 从零搭建最小工程

不基于样例仓库时，新建工程补齐 4 个要素：

**POM 片段**（版本需能通过上方仓库配置解析）：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>cn.geelato</groupId>
      <artifactId>geelato-framework-bom</artifactId>
      <version>${geelato.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>cn.geelato</groupId>
    <artifactId>geelato-framework-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

**application.properties**（`spring.datasource.primary.jdbc-url` 是 starter 自动装配的硬前提，缺失时启动报 `NoSuchBeanDefinitionException(primaryJdbcTemplate)`）：

```properties
server.port=8081
spring.application.name=geelato-sample-quickstart

spring.datasource.primary.name=primary
spring.datasource.primary.jdbc-url=jdbc:h2:mem:geelato_sample;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false
spring.datasource.primary.driver-class-name=org.h2.Driver
spring.datasource.primary.username=sa
spring.datasource.primary.password=
```

**启动类**（包在 `@SpringBootApplication` 默认扫描范围内即可，无需额外配置）：

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

**示例接口**（`@ApiRuntimeRestController` 自动具备 `/api` 前缀与统一 JSON 约定）：

```java
package com.example.demo;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.Map;

@ApiRuntimeRestController("/api/sample")
public class SampleHelloController {

    private final JdbcTemplate primaryJdbcTemplate;

    public SampleHelloController(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
    }

    @GetMapping("/ping")
    public ApiResult<Map<String, Object>> ping() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("database", primaryJdbcTemplate.queryForObject("select 'h2'", String.class));
        return ApiResult.success(payload);
    }
}
```

构建、启动、验证命令同上。

## 最小依赖原则

最小接入阶段建议只保留：

- `geelato-framework-bom`
- `geelato-framework-starter`
- 一个主数据源驱动
- Spring Boot Web 启动工程

不建议在最小接入阶段直接引入：

- 平台化上传实现
- 设计时建模治理能力
- `message`、`market`、`schedule` 等扩展模块
- 需要额外中间件的重量级平台能力

依赖体量说明：`geelato-framework-starter` 自身传递依赖了 GraalVM JS（约 58 MB）、Seata、Atomikos、Redis 客户端、MySQL 驱动等，即使只用 H2 也会被打包——最小工程 fat jar 实测约 156 MB / 178 个 jar / 8 个 Geelato 模块。上述"不建议引入"指的是在 starter 之上再叠加平台模块，而非当前依赖树已经极简。轻量 starter 在规划中，当前版本请知悉该体量。

## 最小配置面

当前 starter 自动装配的前提是存在 `spring.datasource.primary.jdbc-url`，并会默认创建：

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao`（类型为 `cn.geelato.core.orm.Dao`，按 Bean 名注入即可）
- `dbGenerateDao`（同为 `Dao` 的代理 Bean，注意它不是名为 `DbGenerateDao` 的独立类型）

如存在 `spring.datasource.secondary.jdbc-url`，还会额外创建 `secondaryDataSource` / `secondaryJdbcTemplate` / `secondaryDao`，且与主数据源不会产生 Bean 歧义。

## 推荐起点

当前官方最小样例是 [`geelato-sample-quickstart`](https://github.com/geelato-projects/geelato-hello-example/tree/main/geelato-sample-quickstart)，它使用：

- `geelato-framework-starter`
- H2 内存数据库
- 默认关闭 JTA / Seata 代理（ORM 自动绑定 `dynamicDao`，无需额外配置）

这条链路的目的不是展示平台全能力，而是验证框架底座可以被独立消费。

事务补充说明：Geelato 层的 JTA 与 Seata 代理默认关闭（对应配置键不置 `true` 即不加载），但 classpath 上的 Atomikos 仍会被 Spring Boot 自装配，启动日志中会出现 Atomikos 输出并在工作目录写出 `./atomikos-logs/`，属预期行为。

## 何时切换到脚手架

当项目明确需要登录、MQL、组织与用户、字典、上传等基础后台能力时，不建议继续在最小 sample 上扩展，而应直接采用 `geelato-app-scaffold`。其定位是可直接开工的官方脚手架，而非最小样例。

若目标不是运行官方示例，而是新建业务工程并长期依赖 starter，请继续阅读 [基于 app-scaffold-starter 创建业务项目](app-scaffold-starter-project-guide.md)。

## 最小成功标准与验证

最短成功路径应至少满足以下 5 条，每条都可直接验证：

| 成功标准 | 验证方式 |
| --- | --- |
| 应用可以正常启动 | 控制台出现 `Started ... in X.XXX seconds`、`Tomcat started on port(s): 8081` |
| 主数据源可用 | `/api/sample/ping` 返回 `data.database = "h2"` |
| `JdbcTemplate` 可注入 | ping 控制器按 `@Qualifier("primaryJdbcTemplate")` 注入成功（应用能启动即证明） |
| `Dao` 可注入 | 注入 `@Qualifier("primaryDao") Dao dao`，执行 `dao.nativeQueryForObject("select 1", String.class)` 返回 `1` |
| 能调用一个运行时接口 | `curl -sS http://localhost:8081/api/sample/ping` 返回 `code: 20000` |

如需逐条验证 Bean 装配与 ORM，可在工程内临时追加一个探针控制器：

```java
package com.example.demo;

import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BeanProbeController {
    private final Dao primaryDao;

    public BeanProbeController(@Qualifier("primaryDao") Dao primaryDao) {
        this.primaryDao = primaryDao;
    }

    @GetMapping("/api/probe/orm")
    public ApiResult<String> probe() {
        return ApiResult.success(primaryDao.nativeQueryForObject("select 'orm-ok'", new Object[0], String.class));
    }
}
```

访问 `curl http://localhost:8081/api/probe/orm` 返回 `orm-ok` 即 ORM 自动装配可用。最小 starter 不带 actuator 与 OpenAPI，`/actuator/*`、`/v3/api-docs` 返回 404 属预期。

## 下一步

- 基于 starter 创建业务工程并需要基础后台能力：[基于 app-scaffold-starter 创建业务项目](app-scaffold-starter-project-guide.md)
- 扩展元数据能力：[MetaStore 扩展](../reference/metastore-extension.md)
