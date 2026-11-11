---
title: 脚手架快速启动指南
sidebar_label: 脚手架快速启动指南
---

# 脚手架快速启动指南

本指南说明如何基于 `geelato-app-scaffold-starter` 快速初始化一个可投入生产开发的后端工程。脚手架在 starter 内对平台控制器做了白名单裁剪：默认仅保留内置能力集对应的运行时接口，平台设计期配置接口不暴露；同时开箱即用提供一套企业级后端基础设施。实际生效的能力清单以 `GET /api/scaffold/ready` 返回的 `capabilities` 为准。

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

## 前置条件

| 项 | 要求 |
| --- | --- |
| JDK | 17（`java -version` 确认） |
| Maven | 3.8+（`mvn -v` 确认） |
| MySQL | 8.x 已安装且服务运行中，`localhost:3306` 可连通；执行建库脚本需使用具有 `CREATE USER` 权限的账号 |
| 端口 | `8088` 未被占用（可在 `application.properties` 调整） |
| 上传目录 | `/upload`、`/upload/convert`、`/upload/config` 需预先创建且应用有写权限，应用不会自动创建（Windows 见下方说明） |
| Maven 仓库 | 已配置可解析 `cn.geelato` 制品的仓库与凭证，见下节[制品仓库与凭证](#制品仓库与凭证) |

Windows 下上传目录示例（PowerShell）：

```powershell
New-Item -ItemType Directory -Force -Path D:\upload, D:\upload\convert, D:\upload\config
```

对应配置改为本地绝对路径：

```properties
geelato.upload.root-directory=D:/upload
geelato.upload.convert-directory=D:/upload/convert
geelato.upload.config-directory=D:/upload/config
```

另需注意：部分环境会注入 `SERVER__PORT` 之类的环境变量，Spring Boot 宽松绑定会使其覆盖配置文件中的 `server.port`，表现为应用监听在一个配置里从未出现的端口甚至启动报端口占用。启动前可用 `env | grep -i SERVER` 自查，或启动时显式追加 `--server.port=8088`。

## 制品仓库与凭证

框架当前以 `1.0.0-SNAPSHOT` 形式通过私有 Maven 仓库分发，Maven Central 上暂无可消费的 Geelato 制品。**未配置仓库与凭证时，构建会在解析 BOM 时直接失败**：

```
[ERROR] Non-resolvable import POM: The following artifacts could not be resolved:
        cn.geelato:geelato-framework-bom:pom:1.0.0-SNAPSHOT (absent):
        Could not find artifact cn.geelato:geelato-framework-bom:pom:1.0.0-SNAPSHOT
```

### 配置步骤

1. 向框架组申请云效制品仓库（packages.aliyun.com）的访问账号与密码。
2. 在 `~/.m2/settings.xml` 中配置仓库与凭证：

```xml
<settings>
  <servers>
    <server>
      <id>geelato-snapshots</id>
      <username>＜向框架组申请的账号＞</username>
      <password>＜向框架组申请的密码＞</password>
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
        <!-- 正式版发布仓库的 URL 与凭证由框架组提供后配置在此 -->
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>geelato</activeProfile>
  </activeProfiles>
</settings>
```

3. 预检能否解析框架 BOM：

```bash
mvn -B dependency:get -Dartifact=cn.geelato:geelato-framework-bom:pom:1.0.0-SNAPSHOT
```

### 常见问题

- `Could not find artifact cn.geelato:...`：未配置仓库/凭证，或 settings.xml 未生效（检查 `mvn -v` 输出的 user settings 路径）。
- `401 Unauthorized`：账号或密码错误、账号无该仓库权限。
- 公司网络配置了 `<mirrorOf>*</mirrorOf>` 全量镜像：需将 Geelato 仓库排除，如 `<mirrorOf>*,!geelato-snapshots</mirrorOf>`。
- 构建期出现 `The POM for com.aspose:aspose-words:jar:jdk17:21.1 is missing`：该商业依赖同样只存在于私有仓库，脚手架链路（上传转换等能力）需要它，确保凭证账号对 release 仓库同样有权限。
- 无私服访问权限时，`geelato-framework-bom`、`geelato-framework-starter` 及其核心链可从源码构建（见[新项目最小接入](minimal-integration.md)的"从源码构建"一节）；`geelato-app-scaffold-starter` 依赖仓库外的 `geelato-package`、`plugin-all`，目前无公开源码构建路径，请申请制品仓库凭证。

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
        <!-- 版本需可通过上节「制品仓库与凭证」解析，否则构建失败 -->
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
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
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

产物说明：`geelato-app-scaffold-starter` 传递依赖完整平台运行时（GraalVM JS、Seata、Atomikos、Redis 客户端、OpenCV/onnxruntime、aspose-words 等），`mvn package` 产出的 fat jar 约 450 MB，冷启动约 50–60 秒。这是"开箱即用全能力"的体积代价；仅需验证框架底座时请使用[新项目最小接入](minimal-integration.md)链路，产物约 156 MB。

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

# ORM 绑定与事务：三项均与框架默认值一致，显式声明便于阅读，可省略
geelato.orm.dao-bean-name=dynamicDao
geelato.datasource.dynamic.enable-jta-transaction=false
geelato.datasource.dynamic.enable-seata-proxy=false

geelato.upload.root-directory=/upload
geelato.upload.convert-directory=/upload/convert
geelato.upload.config-directory=/upload/config

geelato.app.scaffold.auto-init-tables=true

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

能力裁剪说明：当前 starter 版本（`1.0.0-SNAPSHOT`）内置能力裁剪器按内置能力集（15 项，即 `/api/scaffold/ready` 返回的 `capabilities`）裁剪平台控制器，实际暴露面以 ready 接口为准。`geelato.app.scaffold.strict`、`geelato.app.scaffold.capabilities` 为规划中的配置键，当前版本配置后**不会生效**；`geelato.app.scaffold.enabled` 不是有效配置键，均无需配置。如需在裁剪白名单外追加自有控制器，使用 `geelato.app.scaffold.extra-controllers`（控制器全限定类名列表）。

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
| 口令 | 初始脚本未预设可用明文，首次登录前请先执行[口令重置 SQL](#首次登录最小闭环) |

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
java -jar target/acme-order-center-1.0.0-SNAPSHOT.jar --server.port=8088
```

启动成功的标志（控制台）：`Started AcmeOrderApplication in X.XXX seconds` 与 `Tomcat started on port(s): 8088 (http)`。首次启动含自动建表与依赖初始化，冷启动通常 50–60 秒，属正常耗时。

### 启动日志判读

首次启动日志中出现的以下 ERROR 属预期行为，不代表启动失败，无需处理：

| 启动期日志 | 说明 |
| --- | --- |
| `ERROR ... For dynamic data sources has been search failed` + `Table 'xxx.platform_dev_db_connect' doesn't exist` | 自动建表完成前查询动态数据源配置表的预期顺序问题，表建好后重启不再出现 |
| `ERROR ... Cannot find the manifest path` / `Cannot load plugin 'plugins\repository'` | 未部署插件目录时的预期提示，脚手架默认不带插件 |
| OpenCV 相关 ERROR | OCR 原生库未就绪的预期提示，不影响脚手架基础能力 |

### 就绪检查

Starter 提供通用就绪检查接口，无需鉴权：

```bash
curl -sS http://localhost:8088/api/scaffold/ready
```

期望响应（成功码为 `20000`，`database` 为字符串 `"ready"` 表示主库连通）：

```json
{
  "msg": "app scaffold starter is ready",
  "code": 20000,
  "status": "ok",
  "data": {
    "app": "acme-order-center",
    "starter": "geelato-app-scaffold-starter",
    "capabilities": ["login","mql","organization","user","dictionary","notice","upload","app","schedule","sql","script","site","export","model","ddl"],
    "database": "ready"
  },
  "cache": null
}
```

`database` 非 `ready` 时先解决数据库连通问题，不要继续后续验证。

### 首次登录最小闭环

`gl_user` 的初始脚本未预设可用明文口令，首次登录前先执行重置 SQL（重置后口令为 `Geelato@2026`）：

```sql
UPDATE platform_user
SET password = 'e7c3f13e711f5a0a95675761b8d9a8e2b97e8e43',
    salt = 'def8ff5614e0892f'
WHERE login_name = 'gl_user' AND tenant_code = 'geelato';
```

口令存储算法为 `sha1` 链式迭代：`password = hex(sha1(salt + 明文)) 后再对结果迭代 sha1 共 1024 次`。如需生成自定义口令，用该算法以随机 8 字节盐计算后写入 `password`、`salt` 两列。

登录（注意请求体字段为 `username` / `tenant`，与 `LoginParams` 定义一致，**不是** `loginName` / `tenantCode`）：

```bash
curl -sS -X POST http://localhost:8088/api/user/login \
  -H "Content-Type: application/json" \
  --data '{"username":"gl_user","password":"Geelato@2026","tenant":"geelato"}'
```

期望响应（`code=20000`，`data.token` 为后续所有业务接口的鉴权凭证）：

```json
{
  "msg": "认证成功!",
  "code": 20000,
  "status": "ok",
  "data": { "token": "eyJhbGciOi...", "...": "登录用户信息" },
  "cache": null
}
```

失败排查：返回 `code=-2` 且 `msg=账号或密码不正确` → 先确认重置 SQL 已执行、口令输入无误，再确认请求体字段名是 `username`/`tenant`。

拿到 `token` 后，后续接口统一携带：

```
Authorization: JWTBearer {token}
```

### 内置能力可访问性验证

以下 5 个入口需携带 `Authorization`，逐一验证返回 `code=20000` 即表明脚手架内置的运行时能力已被业务工程成功消费：

```bash
TOKEN="eyJhbGciOi..."

# 1) MQL 列表
curl -sS -X POST http://localhost:8088/api/meta/list \
  -H "Authorization: JWTBearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"platform_user":{"@fs":"id,loginName,name","@p":"1,10"}}'

# 2) 字典（注意实际路径为 /api/system/dictionary/{code}）
curl -sS http://localhost:8088/api/system/dictionary/demo_status \
  -H "Authorization: JWTBearer $TOKEN"

# 3) 字典分页
curl -sS -X POST http://localhost:8088/api/dict/pageQuery \
  -H "Authorization: JWTBearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"@p":"1,10"}'

# 4) 组织分页
curl -sS -X POST http://localhost:8088/api/security/org/pageQuery \
  -H "Authorization: JWTBearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"@p":"1,10"}'

# 5) 用户分页
curl -sS -X POST http://localhost:8088/api/security/user/pageQuery \
  -H "Authorization: JWTBearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"@p":"1,10"}'
```

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

- 登录：`POST /api/oauth2/login?code={授权码}`（授权码模式，入参为授权码，不是账号口令）
- 刷新：`POST /api/oauth2/refreshToken?oldToken={旧token}`
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

常用入口（均需 `Authorization`）：

- 按编码获取字典项：`GET /api/system/dictionary/{code}`
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

使用方式：将该目录作为自定义 Skill 导入 AI 工具（TRAE Skills），在对话中输入 `Use Skill: geelato-app-scaffold-starter-guide`，并补充项目目录、`groupId/artifactId`、包名、数据库连接信息、希望开启的能力（login/mql/dictionary/organization/user/upload 等）。该 Skill 内容提炼自本指南，可引导完成最小可运行工程创建、制品仓库与凭证配置，以及字典、组织用户、上传等可验证的最小闭环。

## 继续阅读

- [新项目最小接入](minimal-integration.md)
- [MQL 使用指南](../mql/usage.md)
- [Fluent DSL 指引](../orm/fluent-dsl.md)
