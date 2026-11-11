# Scaffold Quick Start Guide

This guide explains how to create a production-ready backend project based on `geelato-app-scaffold-starter`, and how to use the built-in baseline capabilities.

---

## 1. Scaffold Baseline Capabilities Overview

By introducing this Starter, your project will automatically have the following 7 core capabilities:

1. **🔐 Login and Authentication**: Built-in JWT local login and OAuth2 authorization code login, automatically intercepting and parsing Tokens.
2. **📁 File Processing and OSS**: Provides unified local/OSS file upload, download, and preview interfaces.
3. **🔍 MQL Dynamic Query**: Through the `/api/meta/*` interface, the frontend can directly send JSON to query the database dynamically.
4. **⚙️ ORM Fluent DSL**: Backend business code can directly use `MetaFactory` for chained CRUD operations that prevent SQL injection.
5. **📖 Data Dictionary**: Built-in creation, maintenance, and fast querying by Code for dictionary items.
6. **👥 Organization and RBAC**: Built-in standard user, organization, role, permission models, and assignment interfaces.
7. **📚 OpenAPI (Swagger)**: Automatically generates and exposes interface documentation in the dev/test environments by default.

---

## 2. Common Invocation Conventions

Before calling any of the above capabilities, please note the basic conventions:

- **Unified Prefix**: All scaffold interfaces start with `/api`.
- **Tenant Isolation**: It is recommended to carry `Tenant-Code` in all request headers (defaults to `geelato` if not provided).
- **Authentication Credentials**: After a successful login, `Authorization` must be carried in the request header.
  - JWT Login: `Authorization: JWTBearer {token}`
  - OAuth2 Login: `Authorization: Bearer {accessToken}`

---

## 2.1 Prerequisites

| Item | Requirement |
| --- | --- |
| JDK | 17 (`java -version`) |
| Maven | 3.8+ (`mvn -v`) |
| MySQL | 8.x installed and running, `localhost:3306` reachable; the DB bootstrap needs an account with `CREATE USER` privileges |
| Port | `8088` is free |
| Upload directories | `/upload`, `/upload/convert`, `/upload/config` must be created manually and be writable (use local absolute paths such as `D:/upload` on Windows) |
| Maven repository | A repository with credentials that can resolve `cn.geelato` artifacts — see the next section |

Note: some environments inject variables such as `SERVER__PORT`; Spring Boot relaxed binding lets them override `server.port`. Check with `env | grep -i SERVER` or pass `--server.port=8088` explicitly.

## 2.2 Artifact Repository and Credentials

The framework is currently distributed as `1.0.0-SNAPSHOT` through a private Maven repository; no Geelato artifacts are available on Maven Central yet. Without credentials the build fails at BOM import with `Non-resolvable import POM: cn.geelato:geelato-framework-bom:pom:1.0.0-SNAPSHOT`.

1. Request a packages.aliyun.com artifact-repository account from the framework team.
2. Configure `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>geelato-snapshots</id>
      <username><!-- requested account --></username>
      <password><!-- requested credential --></password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>geelato</id>
      <repositories>
        <repository>
          <id>geelato-snapshots</id>
          <url>https://packages.aliyun.com/maven/repository/2367868-snapshot-hDbqny</url>
          <releases><enabled>false</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>geelato</activeProfile>
  </activeProfiles>
</settings>
```

3. Precheck:

```bash
mvn -B dependency:get -Dartifact=cn.geelato:geelato-framework-bom:pom:1.0.0-SNAPSHOT
```

Notes: a global `<mirrorOf>*</mirrorOf>` mirror must exclude this repository (`<mirrorOf>*,!geelato-snapshots</mirrorOf>`); the commercial dependency `com.aspose:aspose-words` also lives only in the private repository. `geelato-app-scaffold-starter` has no public source-build path (it depends on `geelato-package` and `plugin-all` outside the public repository), so credentials are required.

---

## 3. Create a Business Project Based on Scaffold

### 3.1 Create a new project

You can either:
- copy the runnable sample `geelato-hello-example/geelato-app-scaffold`, then change Maven coordinates / package name / configs
- or create an empty Spring Boot project and add the starter dependency

### 3.2 AI automation (Skill)

If you want to standardize and automate “create the business project + fill in required configs + verify the minimal working loop”, you can reuse the Skill from the sample repository and let AI guide/modify your project automatically.

- Skill source: [https://github.com/geelato-projects/geelato-hello-example/tree/main/skills/geelato-app-scaffold-starter-guide](https://github.com/geelato-projects/geelato-hello-example/tree/main/skills/geelato-app-scaffold-starter-guide)

Recommended usage:

- import the above folder as a custom Skill in your AI tool (TRAE Skills)
- in chat, invoke: `Use Skill: geelato-app-scaffold-starter-guide`
- provide: project directory, `groupId/artifactId`, base package, database connection, and enabled capabilities (login/mql/dictionary/organization/user/upload)

This Skill is distilled from the starter project guide and will walk you through:

- minimal runnable project setup (pom / bootstrap class / application.properties)
- artifact repository and credential configuration
- verifiable baseline loops (dictionary, org/user, upload)

Minimal `pom.xml` snippet:

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
    <artifactId>geelato-app-scaffold-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

### 3.3 Bootstrap class

Use `cn.geelato.web.platform.boot.BootApplication` as the base class and make sure your business package is included in scanning:

```java
@SpringBootApplication(scanBasePackages = {"cn.geelato", "com.acme.order"})
public class AcmeOrderApplication extends BootApplication {}
```

## 3. application.properties (minimal)

```properties
spring.application.name=acme-order-center
server.port=8088

spring.datasource.primary.name=primary
spring.datasource.primary.jdbc-url=${GEELATO_PRIMARY_JDBCURL:jdbc:mysql://localhost:3306/acme_order_center?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true}
spring.datasource.primary.username=${GEELATO_PRIMARY_JDBCUSER:acme_user}
spring.datasource.primary.password=${GEELATO_PRIMARY_JDBCPASSWORD:acme@psd}
spring.datasource.primary.driver-class-name=com.mysql.cj.jdbc.Driver

geelato.orm.dao-bean-name=dynamicDao
geelato.datasource.dynamic.enable-jta-transaction=false
geelato.datasource.dynamic.enable-seata-proxy=false

geelato.app.scaffold.auto-init-tables=true

springdoc.api-docs.enabled=true
springdoc.api-docs.path=/v3/api-docs
geelato.app.scaffold.openapi-enabled=true
geelato.app.scaffold.openapi-expose-in-prod=false

# OSS (optional)
# enable /api/oss/* only when the config is complete
# geelato.oss.accessKeyId=***
# geelato.oss.accessKeySecret=***
# geelato.oss.endPoint=oss-cn-xxx.aliyuncs.com
# geelato.oss.bucketName=your-bucket
# geelato.oss.region=cn-xxx

geelato.meta.scan-package-names=cn.geelato,com.acme.order
geelato.graal.scan-package-names=cn.geelato,com.acme.order
```

## 4. Core Capabilities Usage Guide

### 4.1 Login and Authentication

The scaffold opens standard login entry points by default.

**Usage: JWT Password Login**

The seeded user `gl_user` ships without a usable plaintext password. Reset it first (after this the password is `Geelato@2026`):

```sql
UPDATE platform_user
SET password = 'e7c3f13e711f5a0a95675761b8d9a8e2b97e8e43',
    salt = 'def8ff5614e0892f'
WHERE login_name = 'gl_user' AND tenant_code = 'geelato';
```

Password storage: `password = hex(sha1(salt + plaintext)) chained-iterated with sha1 up to 1024 rounds`.

Then log in (note the request fields are `username` / `tenant`, matching `LoginParams` — **not** `loginName` / `tenantCode`; no token required):

```bash
curl -X POST "http://localhost:8088/api/user/login" \
  -H "Content-Type: application/json" \
  --data "{\"username\":\"gl_user\",\"password\":\"Geelato@2026\",\"tenant\":\"geelato\"}"
```

*A response with `code: 20000` carries `data.token`, which is used for all subsequent business requests via `Authorization: JWTBearer {token}`.*

### 4.2 MQL Dynamic Query

Allows the frontend to define query structures via JSON to fetch required data directly, without the backend writing extra Controllers/Mappers.

**Usage: Frontend requests MQL interface**
- **List query**: `POST /api/meta/list`
- **Save single record**: `POST /api/meta/save/{biz}`
- **Delete single record**: `POST /api/meta/delete/{biz}/{id}`

*Query example (fetch user list, returning only specified fields and sorting):*
```json
{
  "platform_user": {
    "@fs": "id,loginName,name",
    "@p": "1,10",
    "@order": "createAt|-"
  }
}
```

### 4.3 ORM Fluent DSL

Backend developers no longer need to write tedious SQL or MyBatis XML for custom business logic.

**Usage: Call MetaFactory directly in Service**
```java
// Query example: fetch 10 users on page 1, returning only id and name fields
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name"})
        .page(1, 10)
        .list();
```

### 4.4 Data Dictionary

Provides a standard data dictionary maintenance system, supporting tree or list structures.

**Usage: Maintain and Query Dictionaries**
- **Create dictionary**: `POST /api/dict/createOrUpdate`
- **Create dictionary item**: `POST /api/dict/item/createOrUpdate`
- **Business query**: The frontend can quickly fetch dictionary items for dropdown rendering via `GET /api/system/dictionary/{code}` (e.g., `GET /api/system/dictionary/order_status`; requires `Authorization`).

### 4.5 Organization, User, and RBAC

A complete set of permission management APIs is built-in.

**Usage: User and Role Assignment**
- **Create organization**: `POST /api/security/org/createOrUpdate`
- **Create user**: `POST /api/security/user/createOrUpdate`
- **Assign role**: `POST /api/security/role/user/insert`

### 4.6 File Processing and OSS

Unified handling of frontend file uploads and downloads. If object storage like Aliyun is configured, it automatically switches to OSS mode.

**Usage: File Upload**
- **Interface**: `POST /api/upload/file` (multipart)
```bash
curl -X POST "http://localhost:8088/api/upload/file" \
  -H "Authorization: JWTBearer {token}" \
  -F "file=@D:/tmp/demo.png"
```

### 4.7 OpenAPI (Swagger)

Your custom interfaces and scaffold interfaces are automatically aggregated into a standard OpenAPI specification document.

**Usage: Access Documentation**
- **Swagger UI**: `http://localhost:8088/swagger-ui/index.html`
- **JSON Contract**: `http://localhost:8088/v3/api-docs`

---

## 5. Advanced Configuration (application.properties)

```properties
# Whether to expose Swagger in prod (default false for security)
geelato.app.scaffold.openapi-expose-in-prod=false

# Auto-enable /api/oss/* if OSS parameters are provided
geelato.oss.accessKeyId=***
geelato.oss.accessKeySecret=***
geelato.oss.endPoint=oss-cn-xxx.aliyuncs.com
geelato.oss.bucketName=your-bucket
```

Capability pruning note: the current starter (`1.0.0-SNAPSHOT`) prunes platform controllers against a built-in capability set (15 items, returned as `capabilities` by `/api/scaffold/ready`). `geelato.app.scaffold.strict` and `geelato.app.scaffold.capabilities` are planned keys that have **no effect** in this version; `geelato.app.scaffold.enabled` is not a valid key. Use `geelato.app.scaffold.extra-controllers` (list of controller FQCNs) to extend the whitelist with your own controllers.
