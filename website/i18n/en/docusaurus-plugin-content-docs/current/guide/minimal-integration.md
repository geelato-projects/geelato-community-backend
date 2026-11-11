# Minimal Integration

This page explains how to start a new Geelato-based project from scratch and keep the first integration focused on the framework foundation instead of platform-heavy extensions.

## Prerequisites

| Item | Requirement |
| --- | --- |
| JDK | 17 (`java -version`) |
| Maven | 3.8+ (`mvn -v`) |
| Maven repository | A repository with credentials that can resolve `cn.geelato` artifacts, or build from source (see below) |
| Port | `8081` is free |

Note: some environments inject variables such as `SERVER__PORT`; Spring Boot relaxed binding lets them override `server.port` in the config file. Check with `env | grep -i SERVER` or pass `--server.port=8081` explicitly.

## Artifact Repository and Credentials

The framework is currently distributed as `1.0.0-SNAPSHOT` through a private Maven repository; no Geelato artifacts are available on Maven Central yet. Without repository credentials the build fails at BOM import with `Non-resolvable import POM: cn.geelato:geelato-framework-bom:pom:1.0.0-SNAPSHOT`.

The repository URL, `settings.xml` snippet, and credential request process are shared with the scaffold guide — see [Artifact Repository and Credentials](app-scaffold-starter-project-guide.md). Precheck command:

```bash
mvn -B dependency:get -Dartifact=cn.geelato:geelato-framework-bom:pom:1.0.0-SNAPSHOT
```

### Build from Source

Without private-repository access, the minimal chain (BOM + starter and its core dependencies, 10 self-contained core libraries) can be built from the public source:

```bash
git clone --depth 1 https://github.com/geelato-projects/geelato-community-backend.git
cd geelato-community-backend
mvn -B install -DskipTests -pl geelato-framework-bom,geelato-lang,geelato-utils,geelato-security,geelato-meta,geelato-core,geelato-orm,geelato-web-common,geelato-framework-starter -am
```

After the build succeeds, the `1.0.0-SNAPSHOT` artifacts are in your local repository and no private repository is needed.

## Recommended Order

1. Import `geelato-framework-bom`
2. Add `geelato-framework-starter`
3. Start from [`geelato-sample-quickstart`](https://github.com/geelato-projects/geelato-hello-example/tree/main/geelato-sample-quickstart)
4. Verify the primary datasource, `JdbcTemplate`, `Dao`, ORM, and one sample endpoint
5. Move to `geelato-app-scaffold` once the project needs ready-to-use backend services
6. Add runtime, designer, or business extensions only after the minimal path is stable

## Run the Minimal Sample in 5 Minutes

The fastest verification is running the official minimal sample (in-memory H2, no external dependencies):

```bash
# 0. Prerequisites: JDK 17, Maven 3.8+; a repository that resolves Geelato artifacts (or a local source build)
git clone --depth 1 https://github.com/geelato-projects/geelato-hello-example.git
cd geelato-hello-example/geelato-sample-quickstart

# 1. Build
mvn -B clean package

# 2. Start (port 8081)
java -jar target/geelato-sample-quickstart-1.0.0-SNAPSHOT.jar --server.port=8081

# 3. Verify
curl -sS http://localhost:8081/api/sample/ping
```

Expected output:

```json
{"msg":"sample quickstart is ready","code":20000,"status":"ok","data":{"app":"geelato-sample-quickstart","frameworkStarter":true,"database":"h2"},"cache":null}
```

The sample consists of 4 files: a standalone POM (no parent), a bootstrap class, one `/api/sample/ping` controller, and `application.properties`. Copy them into your own project to complete the minimal integration.

### Build a Minimal Project from Scratch

Without cloning the sample repository, create a project with 4 elements:

**POM snippet** (the version must be resolvable through the repository setup above):

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

**application.properties** (`spring.datasource.primary.jdbc-url` is a hard prerequisite for the starter auto-configuration; without it startup fails with `NoSuchBeanDefinitionException(primaryJdbcTemplate)`):

```properties
server.port=8081
spring.application.name=geelato-sample-quickstart

spring.datasource.primary.name=primary
spring.datasource.primary.jdbc-url=jdbc:h2:mem:geelato_sample;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false
spring.datasource.primary.driver-class-name=org.h2.Driver
spring.datasource.primary.username=sa
spring.datasource.primary.password=
```

**Bootstrap class** (any package covered by `@SpringBootApplication` default scanning; no extra configuration needed):

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

**Sample endpoint** (`@ApiRuntimeRestController` adds the `/api` prefix and the unified JSON convention automatically):

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

Use the same build/start/verify commands as above.

## Minimal Dependency Rule

At the beginning, keep only:

- `geelato-framework-bom`
- `geelato-framework-starter`
- one primary datasource driver
- a Spring Boot web application

Do not start with:

- platform-specific upload runtime
- designer-side metadata governance
- `message`, `market`, `schedule`, or similar extensions
- heavy platform capabilities that require extra middleware

Dependency size note: `geelato-framework-starter` transitively brings GraalVM JS (~58 MB), Seata, Atomikos, the Redis client, and the MySQL driver — even if you only use H2. The minimal fat jar measures about 156 MB / 178 jars / 8 Geelato modules. The "do not start with" list means don't add platform modules on top of the starter, not that the current dependency tree is already tiny. A lite starter is planned; be aware of the current footprint.

## Minimal Configuration Surface

The current starter wiring is triggered by `spring.datasource.primary.jdbc-url`, and it creates these default beans:

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao` (of type `cn.geelato.core.orm.Dao`; inject by bean name)
- `dbGenerateDao` (also a proxied `Dao`; note it is not a separate `DbGenerateDao` type)

If `spring.datasource.secondary.jdbc-url` is also provided, the secondary beans (`secondaryDataSource` / `secondaryJdbcTemplate` / `secondaryDao`) are created as well, without bean ambiguity against the primary ones.

## Recommended Starting Point

The official minimal sample is [`geelato-sample-quickstart`](https://github.com/geelato-projects/geelato-hello-example/tree/main/geelato-sample-quickstart), which uses:

- `geelato-framework-starter`
- an in-memory H2 datasource
- JTA and Seata proxy disabled by default (ORM binds `dynamicDao` automatically, no extra configuration)

Its purpose is to prove that the framework foundation can be consumed independently.

Transaction note: JTA and the Seata proxy are disabled at the Geelato layer (the corresponding configuration keys are not loaded unless set to `true`), but Atomikos on the classpath is still auto-configured by Spring Boot — startup logs will contain Atomikos output and a `./atomikos-logs/` directory is created in the working directory. This is expected.

## When to Switch to the Scaffold

If the project already needs these baseline backend capabilities:

- login
- MQL
- organization and user management
- dictionary
- upload

do not keep growing the minimal sample. Move to:

- `geelato-app-scaffold`

Its role is to be the official ready-to-start scaffold, not the minimal verification sample.

## Minimal Success Criteria and Verification

The shortest successful path should verify the following 5 criteria, each directly testable:

| Criterion | Verification |
| --- | --- |
| the application starts | console shows `Started ... in X.XXX seconds` and `Tomcat started on port(s): 8081` |
| the primary datasource is available | `/api/sample/ping` returns `data.database = "h2"` |
| `JdbcTemplate` can be injected | the ping controller injects it via `@Qualifier("primaryJdbcTemplate")` (the app starting at all proves this) |
| `Dao` can be injected | inject `@Qualifier("primaryDao") Dao dao` and run `dao.nativeQueryForObject("select 1", new Object[0], String.class)` which returns `1` |
| one runtime endpoint can be called | `curl -sS http://localhost:8081/api/sample/ping` returns `code: 20000` |

To verify bean wiring and ORM step by step, temporarily add a probe controller to the project:

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

`curl http://localhost:8081/api/probe/orm` returning `orm-ok` proves ORM auto-configuration is active. The minimal starter ships neither actuator nor OpenAPI — 404s on `/actuator/*` and `/v3/api-docs` are expected.

## Suggested Reading

- If you want to build a real business project with ready-to-use backend services, read [App Scaffold Quickstart](app-scaffold-starter-project-guide.md)
- [MetaStore Extension](../reference/metastore-extension.md)
