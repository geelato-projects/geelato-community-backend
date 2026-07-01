# 基于 app-scaffold-starter 创建业务项目

这篇文档面向真正要开工做业务的项目，说明如何基于 `geelato-app-scaffold-starter` 从零创建自己的工程，并逐步完成：

1. 新建工程
2. 配置数据库与必要参数
3. 启动程序
4. 验证工程可访问
5. 新增业务实体与建表脚本
6. 通过 MQL 对业务实体做增删查改
7. 调用脚手架已内置的运行时能力
8. 跟随脚手架底层升级你的业务工程

## 为什么不是直接复制 app-scaffold

`geelato-app-scaffold` 是官方可运行示例工程，它的价值在于：

- 展示一个官方认可的应用层壳子应该怎么搭
- 证明 `geelato-app-scaffold-starter` 能被正常消费
- 作为排查问题和提炼规范的事实源

但真正推荐给业务项目长期依赖的入口是：

- `geelato-app-scaffold-starter`

原因是：

- 业务工程的 `groupId`、`artifactId`、`version` 应该由你自己管理
- 业务代码、业务实体、业务 SQL 应该留在你的工程里
- 后续框架增强应该通过升级 starter/BOM 获取，而不是长期复制官方示例目录

## 1. 创建新工程

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

### 1.1 pom.xml 最小模板

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

geelato.web.platform.design-time.enabled=false

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

springdoc.api-docs.enabled=true
springdoc.api-docs.path=/v3/api-docs

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

## 5. 开始做业务：新增实体与建表脚本放哪里

这一步是业务开发的核心边界，必须明确：

- 业务实体放在你的业务工程里
- 业务建表脚本放在你的业务工程里
- 不要把业务实体和业务 SQL 下沉到 `geelato-app-scaffold-starter`

### 5.1 实体类放哪里

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

### 5.2 建表脚本放哪里

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

## 6. 放完后重启，然后怎么做 CRUD

是的，首次新增业务实体时，典型流程就是：

1. 写实体类
2. 补同名建表脚本
3. 重启应用
4. 用 MQL 直接做增删查改

### 6.1 查询列表

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

### 6.2 新增

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

### 6.3 更新

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

### 6.4 按 ID 删除

接口：

- `POST /api/meta/delete/1/{id}`

例如：

- `POST /api/meta/delete/1/1234567890`

### 6.5 按条件删除

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

### 6.6 什么时候需要自己写 Controller

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

### 6.7 一个重要边界

`geelato.app.scaffold.auto-init-tables=true` 只适合“首次建表”场景。

它不负责：

- 已存在表的字段新增
- 已存在表的字段类型修改
- 自动 `ALTER TABLE`
- 复杂版本迁移

所以后续表结构演进时，你需要使用你自己的数据库变更流程，而不要期待“重启一次应用就自动改表”。

## 7. 脚手架现成能力怎么调用

### 7.1 MQL

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

### 7.2 字典

常用入口：

- `GET /api/dictionary/{code}`
- `POST /api/dict/pageQuery`
- `POST /api/dict/item/pageQuery`

适用场景：

- 业务枚举值统一管理
- 页面下拉项动态配置
- 多项目共享字典项

### 7.3 组织与用户

常用入口：

- `POST /api/security/org/pageQuery`
- `POST /api/security/user/pageQuery`
- `POST /api/security/org/user/pageQuery`

适用场景：

- 组织树和人员基础数据查询
- 权限模型中的组织/用户关联
- 业务数据和组织用户主数据联动

### 7.4 上传

上传能力开箱即用，但要先配好目录：

- `geelato.upload.root-directory`
- `geelato.upload.convert-directory`
- `geelato.upload.config-directory`

上传接口可用于：

- 附件上传
- 图片或文档文件存储
- 后续业务表与文件主数据关联

## 8. 底层模块升级时，如何升级你的业务工程

推荐顺序：

1. 升级 `geelato-framework-bom`
2. 升级 `geelato-app-scaffold-starter`
3. 重新编译业务工程
4. 重启并验证关键入口

### 8.1 升级前建议

- 保持业务逻辑主要留在你的业务工程里
- 不要随意把业务代码改到 starter 内
- 只有多个业务项目都会复用的能力，才考虑下沉到公共层

### 8.2 升级后检查清单

至少检查这些内容：

- Maven 编译是否通过
- 应用是否能正常启动
- `/api/scaffold/ready` 是否正常
- 关键 MQL 请求是否正常
- 业务实体 CRUD 是否正常
- 已使用的字典、组织、用户、上传接口是否正常

## 给 AI 和开发者的推荐工作流

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

## 继续阅读

- [App Scaffold 概览](app-scaffold.md)
- [新项目最小接入](minimal-integration.md)
- [Sample Quickstart](sample-quickstart.md)
- [MQL 使用指南](../mql/usage.md)
