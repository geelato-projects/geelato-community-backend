# 脚手架快速启动指南

本文档将指导你如何基于 `geelato-app-scaffold-starter` 快速初始化一个可生产开发的后端工程。该脚手架默认只暴露“最小能力集”，剔除了冗余的低代码平台配置接口，让工程保持极简，同时开箱即用地提供了一套企业级后端必备的基础设施。

---

## 一、 脚手架开箱即用能力总览

引入本 Starter 后，你的工程将自动具备以下 7 大核心能力：

1. **🔐 登录与鉴权**：内置 JWT 本地登录与 OAuth2 授权码登录，自动拦截并解析 Token。
2. **📁 文件处理与 OSS**：提供统一的本地/OSS 文件上传、下载、预览接口。
3. **🔍 MQL 动态查询**：通过 `/api/meta/*` 接口，前端可直接发送 JSON 动态查询数据库。
4. **⚙️ ORM Fluent DSL**：后端业务代码可直接使用 `MetaFactory` 进行防 SQL 注入的链式 CRUD 操作。
5. **📖 数据字典**：内置字典项的创建、维护与按 Code 快速查询能力。
6. **👥 组织架构与 RBAC**：内置标准的用户、组织、角色、权限模型及分配接口。
7. **📚 OpenAPI (Swagger)**：默认在 dev/test 环境自动生成并暴露接口文档。

---

## 二、 通用调用约定

在调用以上所有能力之前，请注意基础约定：

- **统一前缀**：所有脚手架接口均以 `/api` 开头。
- **租户隔离**：建议在所有请求头中携带 `Tenant-Code`（如未提供，默认为 `geelato`）。
- **认证凭证**：登录成功后，需在请求头携带 `Authorization`。
  - JWT 登录：`Authorization: JWTBearer {token}`
  - OAuth2 登录：`Authorization: Bearer {accessToken}`

---

## 三、 基于脚手架创建业务项目

这篇文档面向真正要开工做业务的项目，说明如何基于 `geelato-app-scaffold-starter` 从零创建自己的工程，并逐步完成：

1. 新建工程
2. 配置数据库与必要参数
3. 启动程序
4. 验证工程可访问
5. 新增业务实体与建表脚本
6. 通过 MQL 对业务实体做增删查改
7. 调用脚手架已内置的运行时能力
8. 跟随脚手架底层升级你的业务工程

## 可以直接复制 geelato-app-scaffold 吗

可以。

`geelato-app-scaffold` 本来就是官方提供的可运行示例工程，它的价值在于：

- 展示一个官方认可的应用层壳子应该怎么搭
- 证明 `geelato-app-scaffold-starter` 能被正常消费
- 作为排查问题和提炼规范的事实源
- 作为新业务项目的起步模板

因此，如果你想尽快起一个能跑、能验证、能继续做业务开发的项目，完全可以先复制：

- `geelato-app-scaffold`

然后在你自己的仓库里改成自己的工程坐标、包名、数据库配置和业务代码。

但这里要区分两件事：

- “项目起步形态”可以直接参考甚至复制 `geelato-app-scaffold`
- “后续公共能力升级入口”仍然应该优先依赖 `geelato-app-scaffold-starter` 和 `geelato-framework-bom`

原因是：

- 业务工程的 `groupId`、`artifactId`、`version` 应该由你自己管理
- 业务代码、业务实体、业务 SQL 应该留在你的工程里
- 后续框架增强更适合通过升级 starter/BOM 获取，而不是长期和官方示例目录保持手工同步
- `geelato-app-scaffold` 更适合作为“起步模板”和“事实源”，而不是未来长期直接在官方目录里继续开发

## 1. 创建新工程

创建业务工程时，建议有两种方式：

1. 直接复制 `geelato-app-scaffold` 作为起点，再改造成你的业务工程
2. 从空目录新建工程，并引入 `geelato-app-scaffold-starter`

如果你的目标是“先尽快跑起来，再继续改业务”，优先推荐第 1 种。
如果你的目标是“从第一天开始就完全按自己的工程骨架搭建”，可以使用第 2 种。

### 1.0 AI 自动化初始化（Skill）

如果你希望把“初始化业务工程 + 补齐关键配置 + 验证最小闭环”的过程标准化，可以直接复用示例仓库中的 Skill 来驱动 AI 自动生成/修改你的业务工程。

- Skill 地址：[https://github.com/geelato-projects/geelato-hello-example/tree/main/skills/geelato-app-scaffold-starter-guide](https://github.com/geelato-projects/geelato-hello-example/tree/main/skills/geelato-app-scaffold-starter-guide)

推荐用法：

- 将上述目录作为自定义 Skill 导入你的 AI 工具（TRAE Skills）
- 在对话中输入：`Use Skill: geelato-app-scaffold-starter-guide`
- 并补充：项目目录、`groupId/artifactId`、包名、数据库连接信息、你希望开启的能力（login/mql/dictionary/organization/user/upload 等）

该 Skill 的内容提炼自本文档，会引导你完成：

- 最小可运行工程创建（pom / 启动类 / application.properties）
- strict 能力收口配置
- 字典、组织用户、上传等“可验证的最小闭环”

先确定你的业务工程名和包名，例如：

- 工程名：`acme-order-center`
- 包名：`com.acme.order`

推荐的最小目录结构如下：

```text
acme-order-center
├─ pom.xml
└─ src
   └─ main
      ├─ java
      │  └─ com/acme/order
      │     └─ AcmeOrderApplication.java
      └─ resources
         ├─ application.properties
         └─ geelato/app/scaffold/init/
```

### 1.1 如果你选择复制 `geelato-app-scaffold`

建议复制后优先做这些调整：

1. 修改你自己的 `groupId`、`artifactId`、`version`
2. 修改启动类包名和 `scanBasePackages`
3. 修改 `spring.application.name`
4. 修改数据库连接配置
5. 保留对 `geelato-app-scaffold-starter` 的依赖入口
6. 把业务实体、业务 SQL、业务接口逐步替换成你自己的内容

复制示例工程的价值在于：

- 少走一遍从零搭壳、补配置、排查基础依赖的过程
- 可以直接复用现成的目录结构和启动方式
- 更适合业务项目快速落地

### 1.2 如果你选择从零创建

下面给出最小模板。

### 1.3 pom.xml 最小模板

业务工程自己的 `pom.xml` 需要自行管理坐标，不依赖官方示例的上级 `pom`。

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

## 2. 创建启动类与基础配置

### 2.1 启动类模板

新工程的启动类推荐直接继承 `cn.geelato.web.platform.boot.BootApplication`：

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

这里有 3 个容易忽略的点：

- `scanBasePackages` 不能只写你的业务包，否则框架控制器和自动装配相关组件不会被扫到
- 也不能只写 `cn.geelato`，否则你自己的业务实体、Controller、Service 不会被 Spring 扫到
- 推荐同时写：
  - `cn.geelato`
  - 你的业务包

### 2.2 application.properties 最小模板

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

# OSS（可选）
# 配置齐全才会启用 /api/oss/* 路由
# geelato.oss.accessKeyId=***
# geelato.oss.accessKeySecret=***
# geelato.oss.endPoint=oss-cn-xxx.aliyuncs.com
# geelato.oss.bucketName=your-bucket
# geelato.oss.region=cn-xxx

geelato.meta.scan-package-names=cn.geelato,com.acme.order
geelato.graal.scan-package-names=cn.geelato,com.acme.order
```

这两个扫描配置非常重要：

- `geelato.meta.scan-package-names`
- `geelato.graal.scan-package-names`

默认它们只扫描 `cn.geelato`。如果你的业务包不加进去，哪怕实体类已经写好了，MQL 也看不见你的业务实体。

## 3. 创建数据库并初始化基础表

### 3.1 创建数据库

例如：

```sql
create database acme_order_center
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;
```

再准备一个业务工程自己的数据库账号：

```sql
create user 'acme_user'@'%' identified by 'acme@psd';
grant all privileges on acme_order_center.* to 'acme_user'@'%';
flush privileges;
```

### 3.2 第一次启动时会初始化什么

第一次启动时，脚手架会初始化两类资源：

- `geelato-web-runtime` 自带的运行时基础 SQL 资源
- `geelato-app-scaffold-starter` 与业务工程自己提供的 `geelato/app/scaffold/init/*.sql`

其中：

- 平台运行时基础表和平台元数据资源来自框架底层
- 你的业务表应该由你自己的工程提供

此外，`geelato-app-scaffold-starter` 默认会初始化一条平台用户数据，便于你在首次启动后确认登录链路已经具备最基本的用户基础数据：

- 登录名：`gl_user`
- 姓名：`gl_user`
- 工号：`9991`
- 手机号：`+86 13800000099`
- 租户编码：`geelato`

这条数据来自 `platform_user` 的初始化脚本，属于脚手架默认基础数据，而不是你的业务用户主数据。

如果你想看脚手架底座到底要求哪些基础表，以及 starter 是如何在启动时自动判断并创建这些表的，请继续阅读：

- `geelato-app-scaffold-starter` 及其初始建表脚本

## 4. 启动与访问验证

### 4.1 启动命令

在你的业务工程根目录执行：

```bash
mvn spring-boot:run
```

或者：

```bash
mvn package
java -jar target/acme-order-center-1.0.0-SNAPSHOT.jar
```

### 4.2 先验证 starter 是否就绪

starter 已提供通用就绪检查接口：

- `GET /api/scaffold/ready`

如果启动成功，返回值里应至少能看到：

- 应用名
- starter 标识
- starter 能力清单
- 主数据库是否 ready

如果你还需要继续验证登录链路，可以先检查 `platform_user` 表中是否已有默认初始化用户：

- 登录名：`gl_user`

文档这里明确告知的是“初始化用户是谁”。至于是否直接使用这条记录做首次登录，取决于你实际采用的密码初始化策略、登录链路和后续是否替换为你自己的业务账号。

### 4.3 再验证脚手架内置能力是否可访问

建议继续验证以下入口：

- `POST /api/meta/list`
- `GET /api/dictionary/{code}`
- `POST /api/dict/pageQuery`
- `POST /api/security/org/pageQuery`
- `POST /api/security/user/pageQuery`

如果这些入口可访问，说明脚手架内置的运行时能力已经被你的业务工程成功消费。

### 4.4 基础能力如何使用（逐项）

#### 4.4.1 登录鉴权（JWT + OAuth2）
**JWT 登录**
- 接口：`POST /api/user/login`
- 返回：`token`
- 后续调用 Header：
  - `Authorization: JWTBearer {token}`

**OAuth2 登录**
- 接口：`POST /api/oauth2/login`
- 刷新：`POST /api/oauth2/refreshToken`
- 后续调用 Header：
  - `Authorization: Bearer {accessToken}`

#### 4.4.2 文件上传 / 下载（含 OSS）
**上传**
- `POST /api/upload/file`（multipart，参数 `file`）

上传示例（上传后会返回 `Attachment`，其中 `id` 用于后续查看/下载）：

```bash
curl -X POST "http://localhost:8088/api/upload/file" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  -F "file=@D:/tmp/demo.png"
```

上传后如何查看：

- 查看附件元数据：`GET /api/attach/get/{id}`
- 预览/下载文件：`GET /api/resources/file?id={id}&isPreview=true`

**下载文件**
- `GET /api/resources/file`

**下载配置（常用于登录前加载站点配置）**
- `GET /api/resources/json?fileName={hostname}_{locale}.config`

**OSS 管理（配置齐全才启用）**
- `/api/oss/*`

#### 4.4.3 MQL（通用 CRUD）
- 列表：`POST /api/meta/list`
- 保存：`POST /api/meta/save/{biz}`
- 删除：`POST /api/meta/delete/{biz}/{id}`

请求示例（查询）：

```json
{
  "platform_user": {
    "@fs": "id,loginName,name",
    "@p": "1,10",
    "@order": "createAt|-"
  }
}
```

#### 4.4.4 ORM Fluent DSL（后端代码）
该能力是“写 Java 业务代码时的数据库访问入口”，不额外暴露 HTTP 接口。推荐在业务 Service 中使用：

```java
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name"})
        .page(1, 10)
        .list();
```

#### 4.4.5 字典（维护 + 获取）
脚手架会在自动建表时初始化一条示例字典，便于你确认字典链路已具备最小数据：

- dictCode：`demo_status`
- 获取：`GET /api/dictionary/demo_status`

如何添加字典：

- 创建/更新字典：`POST /api/dict/createOrUpdate`

```bash
curl -X POST "http://localhost:8088/api/dict/createOrUpdate" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"dictCode\":\"order_status\",\"dictName\":\"订单状态\",\"enableStatus\":1,\"tenantCode\":\"geelato\"}"
```

如何添加字典项：

- 创建/更新字典项：`POST /api/dict/item/createOrUpdate`

```bash
curl -X POST "http://localhost:8088/api/dict/item/createOrUpdate" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"dictId\":\"{dictId}\",\"itemCode\":\"PAID\",\"itemName\":\"已支付\",\"seqNo\":1,\"enableStatus\":1,\"tenantCode\":\"geelato\"}"
```

如何验证：

- 按字典编码获取树形字典项：`GET /api/dict/item/queryItemByDictCode/{dictCode}`
- 按 code 获取字典项（简化接口）：`GET /api/dictionary/{code}`

#### 4.4.6 组织 / 用户 / RBAC
- 组织：`/api/security/org/*`
- 用户：`/api/security/user/*`
- 角色：`/api/security/role/*`
- 权限：`/api/security/permission/*`

脚手架会在自动建表时初始化一条示例组织，并将初始化用户 `gl_user` 绑定为该组织默认用户，便于你验证组织/用户链路：

- orgId：`9000000000000000101`
- orgName：`示例组织`

如何添加组织：

- 创建组织：`POST /api/security/org/createOrUpdate`

```bash
curl -X POST "http://localhost:8088/api/security/org/createOrUpdate" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"pid\":\"9000000000000000101\",\"code\":\"DEV\",\"name\":\"研发部\",\"type\":\"department\",\"category\":\"inside\",\"tenantCode\":\"geelato\"}"
```

验证组织树：

```bash
curl -X GET "http://localhost:8088/api/security/org/queryTree" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}"
```

如何添加用户：

- 创建用户：`POST /api/security/user/createOrUpdate`（会返回 `plainPassword`，用于首次登录）

```bash
curl -X POST "http://localhost:8088/api/security/user/createOrUpdate" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"loginName\":\"tom\",\"name\":\"Tom\",\"orgId\":\"{orgId}\",\"tenantCode\":\"geelato\",\"enableStatus\":1}"
```

如何给用户分配角色（RBAC）：

- 创建角色：`POST /api/security/role/createOrUpdate`
- 绑定用户与角色：`POST /api/security/role/user/insert`

```bash
curl -X POST "http://localhost:8088/api/security/role/user/insert" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"userId\":\"{userId}\",\"roleId\":\"{roleId}\",\"tenantCode\":\"geelato\"}"
```

#### 4.4.7 Swagger（快速查看服务接口）
- OpenAPI JSON：`GET /v3/api-docs`
- Swagger UI：`/swagger-ui/index.html`
- prod 默认关闭：`geelato.app.scaffold.openapi-expose-in-prod=false`

#### 4.4.8 快速开发 HTTP 服务
- 新增你自己的 Controller/Service 放在业务包下（并确保 `scanBasePackages`、`geelato.meta.scan-package-names` 包含业务包）
- 推荐业务 Controller 使用 `@ApiRestController`，从而自动具备 `/api` 前缀与统一 JSON 约定

## 四、 核心能力使用指南 (CRUD 演示)

### 1. 实体与建表脚本放哪里

这一步是业务开发的核心边界，必须明确：

- 业务实体放在你的业务工程里
- 业务建表脚本放在你的业务工程里
- 不要把业务实体和业务 SQL 下沉到 `geelato-app-scaffold-starter`

### 1.1 实体类放哪里

推荐放在：

- `src/main/java/com/acme/order/entity/`

例如新增一个客户实体：

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

### 1.2 建表脚本放哪里

放在：

- `src/main/resources/geelato/app/scaffold/init/`

例如：

- `src/main/resources/geelato/app/scaffold/init/crm_customer.sql`

注意两点：

- 文件名必须和表名一致，初始化器就是按文件名推导表名的
- 这里放的是“首次建表脚本”，不是通用数据库迁移工具

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

### 2. 放完后重启，如何做 CRUD

是的，首次新增业务实体时，典型流程就是：

1. 写实体类
2. 补同名建表脚本
3. 重启应用
4. 用 MQL 直接做增删查改

#### 2.1 查询列表

接口：

- `POST /api/meta/list`

请求体示例：

```json
{
  "crm_customer": {
    "@fs": "id,name,code,contactName,contactPhone,enableStatus,createAt",
    "@p": "1,10",
    "@order": "createAt|-"
  }
}
```

#### 2.2 新增

接口：

- `POST /api/meta/save/1`

请求体示例：

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

#### 2.3 更新

还是调用：

- `POST /api/meta/save/1`

请求体里带 `id` 即可：

```json
{
  "crm_customer": {
    "id": "你的主键ID",
    "contactPhone": "13900000000"
  }
}
```

#### 2.4 按 ID 删除

接口：

- `POST /api/meta/delete/1/{id}`

例如：

- `POST /api/meta/delete/1/1234567890`

#### 2.5 按条件删除

接口：

- `POST /api/meta/delete2/1`

请求体示例：

```json
{
  "crm_customer": {
    "code|eq": "CUST_001"
  }
}
```

#### 2.6 什么时候需要自己写 Controller

如果你的需求只是：

- 单表增删查改
- 基础分页过滤
- 常见 MQL 查询

那么通常不需要再单独写 Controller，直接用 `/api/meta/*` 即可。

只有在这些场景下，才建议自己补业务接口：

- 复杂事务编排
- 多实体聚合操作
- 特殊权限校验
- 非标准返回结构

#### 2.7 一个重要边界

`geelato.app.scaffold.auto-init-tables=true` 只适合“首次建表”场景。

它不负责：

- 已存在表的字段新增
- 已存在表的字段类型修改
- 自动 `ALTER TABLE`
- 复杂版本迁移

所以后续表结构演进时，你需要使用你自己的数据库变更流程，而不要期待“重启一次应用就自动改表”。

## 五、 脚手架现成能力怎么调用

### 1. MQL

MQL 是最核心的通用 CRUD 能力。

常用入口：

- `POST /api/meta/list`
- `POST /api/meta/multiList`
- `POST /api/meta/save/{biz}`
- `POST /api/meta/batchSave`
- `POST /api/meta/multiSave`
- `POST /api/meta/delete/{biz}/{id}`
- `POST /api/meta/delete2/{biz}`

详细语法请继续阅读：

- [MQL 使用指南](../mql/usage.md)

### 2. 字典

常用入口：

- `GET /api/dictionary/{code}`
- `POST /api/dict/pageQuery`
- `POST /api/dict/item/pageQuery`

适用场景：

- 业务枚举值统一管理
- 页面下拉项动态配置
- 多项目共享字典项

### 3. 组织与用户

常用入口：

- `POST /api/security/org/pageQuery`
- `POST /api/security/user/pageQuery`
- `POST /api/security/org/user/pageQuery`

适用场景：

- 组织树和人员基础数据查询
- 权限模型中的组织/用户关联
- 业务数据和组织用户主数据联动

### 4. 上传

上传能力开箱即用，但要先配好目录：

- `geelato.upload.root-directory`
- `geelato.upload.convert-directory`
- `geelato.upload.config-directory`

上传接口可用于：

- 附件上传
- 图片或文档文件存储
- 后续业务表与文件主数据关联

## 六、 底层模块升级时，如何升级你的业务工程

推荐顺序：

1. 升级 `geelato-framework-bom`
2. 升级 `geelato-app-scaffold-starter`
3. 重新编译业务工程
4. 重启并验证关键入口

### 1. 升级前建议

- 保持业务逻辑主要留在你的业务工程里
- 不要随意把业务代码改到 starter 内
- 只有多个业务项目都会复用的能力，才考虑下沉到公共层

### 2. 升级后检查清单

至少检查这些内容：

- Maven 编译是否通过
- 应用是否能正常启动
- `/api/scaffold/ready` 是否正常
- 关键 MQL 请求是否正常
- 业务实体 CRUD 是否正常
- 已使用的字典、组织、用户、上传接口是否正常

## 七、 给 AI 和开发者的推荐工作流

当你已经基于 starter 起好了业务项目，建议统一遵循下面这条规则：

1. 先判断这次修改属于业务工程、starter、runtime 还是 framework
2. 如果只对当前业务项目生效，就先改业务工程
3. 如果未来多个业务工程都要复用，再评估是否下沉到 starter
4. 每次修改后至少验证：
   - 编译
   - 启动
   - `/api/scaffold/ready`
   - 相关 MQL 请求
   - 本次改动涉及的业务接口

## 八、 继续阅读

- [新项目最小接入](minimal-integration.md)
- [MQL 使用指南](../mql/usage.md)
