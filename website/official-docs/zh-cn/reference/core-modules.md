---
title: 核心模块说明
sidebar_label: 核心模块说明
---

# 核心模块说明

本页按框架层与平台层给出 geelato-community 仓库的模块地图及职责说明。框架层模块供宿主工程与后端开发者直接复用；平台层模块构成低代码平台的运行时与应用壳，随平台整体引入。

## 框架层

### 基础契约

### `geelato-lang`

承载基础契约、API 结果模型与低层注解。

### `geelato-utils`

承载通用工具能力与跨模块辅助能力，含全系统统一的本地缓存 `LocalBoundedCache`。

### `geelato-security`

承载安全契约与上下文模型（User、Org、Role、Permission、Tenant 等）。

### 构建入口

### `geelato-framework-bom`

面向框架消费方的版本 BOM，统一管理框架模块与第三方依赖版本。

### `geelato-framework-starter`

框架基础模块的统一 Starter，构成最小底座；带强实现偏好的能力以可选依赖提供，宿主按需引入。

### 核心运行

### `geelato-core`

平台核心：元数据模型、MQL 引擎、SQL 构建器与 ORM 运行时管理，含全局上下文（`GlobalContext`/`SessionCtx`）、数据源注册表（`DataSourceManager`）与实验开关门禁。

### `geelato-orm`

对后端开发者暴露 `MetaFactory` 链式 CRUD（Fluent DSL）、命令执行与 ORM 事件钩子，动态数据源能力位于本模块 `cn.geelato.datasource` 包（路由数据源、`DynamicDataSourceScope` 切换、实体归属解析、p6spy SQL 日志缺省装配）。

### `geelato-script`

通用脚本执行引擎 SDK：GraalVM Polyglot 沙箱、`@GraalService` kit 注册表、内置 kit 六件套（`$gl.sql`/`$gl.mql`/`$gl.http`/`$gl.encrypt`/`$gl.ctx`/`$gl.parameter`）、引擎版本快照与场景档案。详见[脚本引擎](/docs/script/overview)。

## 平台层

平台层模块不属于框架最小底座，随平台应用整体引入。

### 平台实体与共享底座

| 模块 | 职责 |
| --- | --- |
| `geelato-meta` | 平台实体与平台元数据定义（应用、页面、字典、消息、附件等） |
| `geelato-web-common` | Web 通用支撑：过滤器与拦截器、Shiro/OAuth2 集成、认证 SPI（`TokenAuthenticator`）、流量标记、在线用户、日志路由 |
| `geelato-web-oss` | 对象存储抽象与 provider（内建阿里云实现） |

### 应用壳与启动宿主

| 模块 | 职责 |
| --- | --- |
| `geelato-web-runtime` | 运行时应用壳（PlatformWebRuntime） |
| `geelato-web-designer` | 设计器应用壳（PlatformDesginer） |
| `geelato-web-platform` | 平台共享基座（controllers + services + boot） |
| `geelato-web-platform-extras` | 平台可选重依赖特性包：OCR、Office 转 PDF、7zip、Elasticsearch、JGit、seata、atomikos、JDBC 驱动等，按需引入 |
| `geelato-app-scaffold-starter` | 面向脚手架应用的运行时基础服务聚合 Starter |
| `geelato-web-quickstart` | 平台启动宿主（全特性），平台默认以本模块启动 |

### 平台能力模块

| 模块 | 职责 |
| --- | --- |
| `geelato-search-api` / `geelato-search-lucene` | 搜索抽象层（`SearchEngine` 端口）与嵌入式 Lucene 实现（NRT + ngram contains、异步事件索引同步、失败补偿、对账、reindex） |
| `geelato-exception` | 业务异常预警中心：异常规则库、异常实例、处置步骤、处理轨迹、触达留痕、扫描留痕 |
| `geelato-alert` | 业务告警中心：告警规则、通知模板、静默窗口、告警记录 |
| `geelato-mail` | 个人邮箱客户端：IMAP 收信、SMTP 发信、过滤器、标签、签名、假期回复、联系人 |
| `geelato-maintenance` | 内置维护模块：实体三者同步校验与补偿、MQL 测试套件与在线调试工具链 |

### 定制模块与独立构建

| 模块 | 职责 |
| --- | --- |
| `geelato-archive-policy` / `geelato-archive-engine` | 数据归档策略管理与归档引擎（两阶段对账搬移、调度、回迁），自包含，按项目显式引入，不属于平台标准件/脚手架 |
| `geelato-mcp` | MCP 服务（common/fms/platform 三子模块），不在聚合构建内，需单独 install |
| 兄弟仓库 | 工作流（geelato-workflow）、动态集成（geelato-dyn，Karaf/OSGi）、应用打包（geelato-package）源码不在本仓库，文档见对应章节 |

## 关键架构约束

- `SecurityContext` 只能在鉴权成功后由安全链路写入。
- 动态数据源切换以 `DynamicDataSourceScope` 为唯一出入口；`primaryDao` 恒不路由，兼容回退时 `dynamicDao` 仍优先于 `primaryDao`。
- 带强实现偏好的上传能力不进入最小 Starter 底座，以可选依赖 + 守卫形式存在于平台特性包。

## 继续阅读

- [ORM 总览](../orm/overview.md)、[ORM 注解说明](../orm/annotations.md)、[Fluent DSL 指引](../orm/fluent-dsl.md)
- [动态数据源](../dynamic-datasource/overview.md)
- 最小样例：`geelato-sample-quickstart`（在 geelato-hello-example 中）
- 平台应用壳（运行时 / 设计器）：[PlatformWebRuntime](../runtime/platform-web-runtime.md)、[PlatformDesginer](../designer/platform-desginer.md)
