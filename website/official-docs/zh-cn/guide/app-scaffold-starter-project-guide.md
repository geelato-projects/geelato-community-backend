---
title: 脚手架快速启动指南
sidebar_label: 脚手架快速启动指南
---

# 脚手架快速启动指南

本指南说明如何基于 `geelato-app-scaffold-starter` 快速初始化一个可投入生产开发的后端工程。该脚手架默认只暴露"最小能力集"，剔除了冗余的低代码平台配置接口，保持工程极简，同时开箱即用提供一套企业级后端基础设施。

## 脚手架开箱即用能力总览

引入本 Starter 后，工程将自动具备以下 7 项核心能力：

1. **登录与鉴权**：内置 JWT 本地登录与 OAuth2 授权码登录，自动拦截并解析 Token。
2. **文件处理与 OSS**：提供统一的本地/OSS 文件上传、下载、预览接口。
3. **MQL 动态查询**：通过 `/api/meta/*` 接口，前端可直接发送 JSON 动态查询数据库。
4. **ORM Fluent DSL**：后端业务代码可直接使用 `MetaFactory` 进行防 SQL 注入的链式 CRUD 操作。
5. **数据字典**：内置字典项的创建、维护与按 Code 快速查询能力。
6. **组织架构与 RBAC**：内置标准的用户、组织、角色、权限模型及分配接口。
7. **OpenAPI (Swagger)**：默认在 dev/test 环境自动生成并暴露接口文档。

## 通用调用约定

调用上述能力前，需注意以下基础约定：

- **统一前缀**：所有脚手架接口均以 `/api` 开头。
- **租户隔离**：建议在所有请求头中携带 `Tenant-Code`（如未提供，默认为 `geelato`）。
- **认证凭证**：登录成功后，需在请求头携带 `Authorization`。
  - JWT 登录：`Authorization: JWTBearer {token}`
  - OAuth2 登录：`Authorization: Bearer {accessToken}`

## 创建业务项目

### 基于示例工程创建

`geelato-app-scaffold` 是官方提供的可运行示例工程，可作为业务项目的起步模板，价值在于：展示官方认可的应用层工程骨架、验证 `geelato-app-scaffold-starter` 的可消费性、沉淀排查问题与规范的事实源。

复制该示例工程作为起点是推荐做法，复制后需调整：

1. 修改 `groupId`、`artifactId`、`version`。
2. 修改启动类包名和 `scanBasePackages`。
3. 修改 `spring.application.name`。
4. 修改数据库连接配置。
5. 保留对 `geelato-app-scaffold-starter` 的依赖入口。
6. 将业务实体、业务 SQL、业务接口逐步替换为自有内容。

需区分两件事："项目起步形态"可直接参考或复制 `geelato-app-scaffold`；"后续公共能力升级入口"仍应依赖 `geelato-app-scaffold-starter` 和 `geelato-framework-bom`。原因是业务工程的坐标、包名、版本应由项目自行管理，业务代码应留在业务工程内，框架增强通过升级 Starter/BOM 获取，而非与官方示例目录长期手工同步。

### 从零创建工程

如需完全按自有工程骨架搭建，可从空目录新建并引入 `geelato-app-scaffold-starter`。

#### pom.xml 最小模板

业务工程的 `pom.xml` 需自行管理坐标，不依赖官方示例的上级 `pom`：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.acme</groupId>
    <artifactId>acme-order-center</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
        <spring-boot.version>3.0.0</spring-boot.version>
        <geelato.version>1.0.0-SNAPSHOT</geelato.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    </properties>

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
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
                <configuration>
                    <mainClass>com.acme.order.AcmeOrderApplication</mainClass>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

#### 启动类模板

启动类推荐直接继承 `cn.geelato.web.platform.boot.BootApplication`：

```java
package com.acme.order;

import cn.geelato.web.platform.boot.BootApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"cn.geelato", "com.acme.order"})
@EnableConfigurationProperties
@EnableCaching
@EnableAsync(proxyTargetClass = true)
@Slf4j
public class AcmeOrderApplication extends BootApplication {

    public static void main(String[] args) {
        log.info("Starting AcmeOrderApplication");
        SpringApplication.run(AcmeOrderApplication.class, args);
    }
}
```

`scanBasePackages` 需同时包含 `cn.geelato` 与业务包：只写业务包会导致框架控制器与自动装配组件不被扫描；只写 `cn.geelato` 会导致业务实体、Controller、Service 不被 Spring 扫描到。

#### application.properties 最小模板

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

geelato.upload.root-directory=/upload
geelato.upload.convert-directory=/upload/convert
geelato.upload.config-directory=/upload/config

geelato.app.scaffold.enabled=true
geelato.app.scaffold.auto-init-tables=true
geelato.app.scaffold.strict=true
geelato.app.scaffold.capabilities=login,mql,organization,user,dictionary,upload

springdoc.api-docs.enabled=true
springdoc.api-docs.path=/v3/api-docs
geelato.app.scaffold.openapi-enabled=true
geelato.app.scaffold.openapi-expose-in-prod=false

# OSS（可选，配置齐全才会启用 /api/oss/* 路由）
# geelato.oss.accessKeyId=***
# geelato.oss.accessKeySecret=***
# geelato.oss.endPoint=oss-cn-xxx.aliyuncs.com
# geelato.oss.bucketName=your-bucket
# geelato.oss.region=cn-xxx

geelato.meta.scan-package-names=cn.geelato,com.acme.order
geelato.graal.scan-package-names=cn.geelato,com.acme.order
```

`geelato.meta.scan-package-names` 与 `geelato.graal.scan-package-names` 默认只扫描 `cn.geelato`，业务包未加入时 MQL 将无法识别业务实体。

### 创建数据库并初始化基础表

创建数据库：

```sql
create database acme_order_center
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;
```

创建业务工程专用的数据库账号：

```sql
create user 'acme_user'@'%' identified by 'acme@psd';
grant all privileges on acme_order_center.* to 'acme_user'@'%';
flush privileges;
```

首次启动时，脚手架会初始化两类资源：`geelato-web-runtime` 自带的运行时基础 SQL 资源，以及 `geelato-app-scaffold-starter` 与业务工程自身提供的 `geelato/app/scaffold/init/*.sql`。平台运行时基础表与元数据资源来自框架底层，业务表应由业务工程自行提供。

此外，`geelato-app-scaffold-starter` 默认初始化一条平台用户数据，便于首次启动后确认登录链路已具备基础数据：

| 字段 | 值 |
| --- | --- |
| 登录名 | `gl_user` |
| 姓名 | `gl_user` |
| 工号 | `9991` |
| 手机号 | `+86 13800000099` |
| 租户编码 | `geelato` |

该数据来自 `platform_user` 的初始化脚本，属脚手架默认基础数据，并非业务用户主数据。

## 启动与验证

### 启动

在业务工程根目录执行：

```bash
mvn spring-boot:run
```

或打包后运行：

```bash
mvn package
java -jar target/acme-order-center-1.0.0-SNAPSHOT.jar
```

### 就绪检查

Starter 提供通用就绪检查接口：

- `GET /api/scaffold/ready`

启动成功后，返回值应包含应用名、starter 标识、starter 能力清单、主数据库是否 ready。

如需进一步验证登录链路，可检查 `platform_user` 表中是否已有默认初始化用户 `gl_user`。是否直接使用该记录做首次登录，取决于实际的密码初始化策略、登录链路及后续是否替换为业务账号。

### 内置能力可访问性验证

建议验证以下入口可正常访问：

- `POST /api/meta/list`
- `GET /api/dictionary/{code}`
- `POST /api/dict/pageQuery`
- `POST /api/security/org/pageQuery`
- `POST /api/security/user/pageQuery`

这些入口可访问即表明脚手架内置的运行时能力已被业务工程成功消费。

## 业务实体与建表

业务开发的核心边界：业务实体与业务建表脚本均放在业务工程内，不要下沉到 `geelato-app-scaffold-starter`。

### 实体类

实体类推荐放在 `src/main/java/com/acme/order/entity/`。以客户实体为例：

```java
package com.acme.order.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "crm_customer")
@Title(title = "客户")
public class Customer extends BaseSortableEntity implements EntityEnableAble {

    @Title(title = "客户名称")
    private String name;

    @Title(title = "客户编码")
    private String code;

    @Title(title = "联系人")
    @Col(name = "contact_name")
    private String contactName;

    @Title(title = "联系电话")
    @Col(name = "contact_phone")
    private String contactPhone;

    @Title(title = "启用状态")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
}
```

### 建表脚本

建表脚本放在 `src/main/resources/geelato/app/scaffold/init/`，例如 `src/main/resources/geelato/app/scaffold/init/crm_customer.sql`。

注意事项：

- 文件名必须与表名一致，初始化器按文件名推导表名。
- 该目录用于"首次建表脚本"，不替代通用数据库迁移工具。

示例：

```sql
CREATE TABLE IF NOT EXISTS `crm_customer` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `name` varchar(128) NOT NULL COMMENT '客户名称',
  `code` varchar(64) NOT NULL COMMENT '客户编码',
  `contact_name` varchar(64) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(32) DEFAULT NULL COMMENT '联系电话',
  `enable_status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态',
  `seq_no` int DEFAULT 0 COMMENT '排序',
  `dept_id` varchar(32) DEFAULT NULL COMMENT '部门',
  `bu_id` varchar(32) DEFAULT NULL COMMENT '单位',
  `tenant_code` varchar(32) DEFAULT NULL COMMENT '租户编码',
  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) NOT NULL COMMENT '更新者',
  `updater_name` varchar(64) DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) NOT NULL COMMENT '创建者',
  `creator_name` varchar(64) DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crm_customer_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户';
```

### 自动建表的边界

`geelato.app.scaffold.auto-init-tables=true` 仅适用于"首次建表"场景，不负责：

- 已存在表的字段新增
- 已存在表的字段类型修改
- 自动 `ALTER TABLE`
- 复杂版本迁移

表结构演进需使用项目自有的数据库变更流程，不应依赖"重启应用自动改表"。

## 通用数据访问（MQL CRUD）

新增业务实体后，典型流程为：编写实体类 → 补充同名建表脚本 → 重启应用 → 通过 MQL 进行增删查改。

### 查询列表

接口：`POST /api/meta/list`

```json
{
  "crm_customer": {
    "@fs": "id,name,code,contactName,contactPhone,enableStatus,createAt",
    "@p": "1,10",
    "@order": "createAt|-"
  }
}
```

### 新增与更新

接口：`POST /api/meta/save/1`

新增请求体：

```json
{
  "crm_customer": {
    "name": "上海某客户",
    "code": "CUST_001",
    "contactName": "张三",
    "contactPhone": "13800000000",
    "enableStatus": 1
  }
}
```

更新同样调用 `POST /api/meta/save/1`，请求体携带 `id` 即可：

```json
{
  "crm_customer": {
    "id": "你的主键ID",
    "contactPhone": "13900000000"
  }
}
```

### 删除

按 ID 删除：`POST /api/meta/delete/1/{id}`，例如 `POST /api/meta/delete/1/1234567890`。

按条件删除：`POST /api/meta/delete2/1`

```json
{
  "crm_customer": {
    "code|eq": "CUST_001"
  }
}
```

### 何时需要自定义 Controller

若需求仅为单表增删查改、基础分页过滤、常见 MQL 查询，通常无需单独编写 Controller，直接使用 `/api/meta/*` 即可。以下场景建议补充业务接口：

- 复杂事务编排
- 多实体聚合操作
- 特殊权限校验
- 非标准返回结构

完整 MQL 语法与更多入口（`/api/meta/multiList`、`/api/meta/batchSave`、`/api/meta/multiSave` 等）参见 [MQL 使用指南](../mql/usage.md)。

## 内置能力接口

### 登录鉴权（JWT + OAuth2）

**JWT 登录**

- 登录：`POST /api/user/login`，返回 `token`。
- 后续调用 Header：`Authorization: JWTBearer {token}`

**OAuth2 登录**

- 登录：`POST /api/oauth2/login`
- 刷新：`POST /api/oauth2/refreshToken`
- 后续调用 Header：`Authorization: Bearer {accessToken}`

### 文件上传与下载

**上传**：`POST /api/upload/file`（multipart，参数 `file`）。上传成功返回 `Attachment`，其中 `id` 用于后续查看/下载。

```bash
curl -X POST "http://localhost:8088/api/upload/file" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  -F "file=@D:/tmp/demo.png"
```

**查看与下载**

- 附件元数据：`GET /api/attach/get/{id}`
- 预览/下载文件：`GET /api/resources/file?id={id}&isPreview=true`
- 下载文件：`GET /api/resources/file`
- 下载配置（常用于登录前加载站点配置）：`GET /api/resources/json?fileName={hostname}_{locale}.config`

**OSS 管理**：配置齐全时启用 `/api/oss/*` 路由。

### 字典

适用场景：业务枚举值统一管理、页面下拉项动态配置、多项目共享字典项。

脚手架在自动建表时初始化一条示例字典 `demo_status`，用于确认字典链路已具备最小数据。

常用入口：

- 按编码获取字典项：`GET /api/dictionary/{code}`
- 按 code 获取树形字典项：`GET /api/dict/item/queryItemByDictCode/{dictCode}`
- 字典分页查询：`POST /api/dict/pageQuery`
- 字典项分页查询：`POST /api/dict/item/pageQuery`
- 创建/更新字典：`POST /api/dict/createOrUpdate`
- 创建/更新字典项：`POST /api/dict/item/createOrUpdate`

创建字典：

```bash
curl -X POST "http://localhost:8088/api/dict/createOrUpdate" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"dictCode\":\"order_status\",\"dictName\":\"订单状态\",\"enableStatus\":1,\"tenantCode\":\"geelato\"}"
```

创建字典项：

```bash
curl -X POST "http://localhost:8088/api/dict/item/createOrUpdate" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"dictId\":\"{dictId}\",\"itemCode\":\"PAID\",\"itemName\":\"已支付\",\"seqNo\":1,\"enableStatus\":1,\"tenantCode\":\"geelato\"}"
```

### 组织 / 用户 / RBAC

脚手架在自动建表时初始化一条示例组织（`orgId`：`9000000000000000101`，`orgName`：`示例组织`），并将初始化用户 `gl_user` 绑定为该组织默认用户，便于验证组织/用户链路。

常用入口：

- 组织：`/api/security/org/*`（如 `POST /api/security/org/pageQuery`、`POST /api/security/org/queryTree`）
- 用户：`/api/security/user/*`（如 `POST /api/security/user/pageQuery`）
- 角色：`/api/security/role/*`
- 权限：`/api/security/permission/*`
- 组织用户关联查询：`POST /api/security/org/user/pageQuery`

创建组织：

```bash
curl -X POST "http://localhost:8088/api/security/org/createOrUpdate" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"pid\":\"9000000000000000101\",\"code\":\"DEV\",\"name\":\"研发部\",\"type\":\"department\",\"category\":\"inside\",\"tenantCode\":\"geelato\"}"
```

创建用户（返回 `plainPassword`，用于首次登录）：

```bash
curl -X POST "http://localhost:8088/api/security/user/createOrUpdate" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"loginName\":\"tom\",\"name\":\"Tom\",\"orgId\":\"{orgId}\",\"tenantCode\":\"geelato\",\"enableStatus\":1}"
```

绑定用户与角色：

```bash
curl -X POST "http://localhost:8088/api/security/role/user/insert" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"userId\":\"{userId}\",\"roleId\":\"{roleId}\",\"tenantCode\":\"geelato\"}"
```

### ORM Fluent DSL

该能力为后端 Java 业务代码的数据库访问入口，不额外暴露 HTTP 接口。推荐在业务 Service 中使用：

```java
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name"})
        .page(1, 10)
        .list();
```

详见 [Fluent DSL 指引](../orm/fluent-dsl.md)。

### 接口文档（Swagger）

- OpenAPI JSON：`GET /v3/api-docs`
- Swagger UI：`/swagger-ui/index.html`
- prod 默认关闭：`geelato.app.scaffold.openapi-expose-in-prod=false`

### 自定义 HTTP 服务

业务 Controller/Service 放在业务包下，并确保 `scanBasePackages`、`geelato.meta.scan-package-names` 包含业务包。推荐业务 Controller 使用 `@ApiRestController`，从而自动具备 `/api` 前缀与统一 JSON 约定。

## 框架升级

升级顺序：

1. 升级 `geelato-framework-bom`
2. 升级 `geelato-app-scaffold-starter`
3. 重新编译业务工程
4. 重启并验证关键入口

**升级前建议**：业务逻辑主要保留在业务工程内；不将业务代码改入 starter；仅当多个业务项目都会复用的能力，才考虑下沉到公共层。

**升级后检查清单**：

- Maven 编译是否通过
- 应用是否能正常启动
- `/api/scaffold/ready` 是否正常
- 关键 MQL 请求是否正常
- 业务实体 CRUD 是否正常
- 已使用的字典、组织、用户、上传接口是否正常

## 推荐工作流

基于 starter 起好业务项目后，建议遵循以下规则：

1. 先判断本次修改归属业务工程、starter、runtime 还是 framework。
2. 仅对当前业务项目生效的修改，先改业务工程。
3. 未来多个业务工程都要复用的能力，再评估是否下沉到 starter。
4. 每次修改后至少验证：编译、启动、`/api/scaffold/ready`、相关 MQL 请求、本次改动涉及的业务接口。

## AI 自动化初始化（Skill）

如需将"初始化业务工程 + 补齐关键配置 + 验证最小闭环"的过程标准化，可复用示例仓库中的 Skill 驱动 AI 自动生成或修改业务工程：

- Skill 地址：[geelato-app-scaffold-starter-guide](https://github.com/geelato-projects/geelato-hello-example/tree/main/skills/geelato-app-scaffold-starter-guide)

使用方式：将该目录作为自定义 Skill 导入 AI 工具（TRAE Skills），在对话中输入 `Use Skill: geelato-app-scaffold-starter-guide`，并补充项目目录、`groupId/artifactId`、包名、数据库连接信息、希望开启的能力（login/mql/dictionary/organization/user/upload 等）。该 Skill 内容提炼自本指南，可引导完成最小可运行工程创建、strict 能力收口配置，以及字典、组织用户、上传等可验证的最小闭环。

## 继续阅读

- [新项目最小接入](minimal-integration.md)
- [MQL 使用指南](../mql/usage.md)
- [Fluent DSL 指引](../orm/fluent-dsl.md)
