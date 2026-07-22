---
title: 系统配置
sidebar_label: 系统配置
---
# 系统配置

本页说明 `geelato-web-quickstart` 的主配置入口，以及如何将各业务模块的配置拆分到独立的 `properties/*.properties` 文件中。

主配置文件位于：

- `geelato-web-quickstart/src/main/resources/application.properties`

分模块配置目录位于：

- `geelato-web-quickstart/src/main/resources/properties/`

## 配置组织方式

当前采用的是：

- 一个主配置文件承载全局基础配置
- 多个模块配置文件按能力拆分
- 通过 `spring.config.import` 统一导入

导入顺序当前是：

1. `workflow.properties`
2. `seata.properties`
3. `oss.properties`
4. `package.properties`
5. `sc.properties`
6. `auth.properties`
7. `market.properties`
8. `message.properties`
9. `weixin_work.properties`
10. `elasticsearch.properties`
11. `monitor.properties`

这意味着：

- `application.properties` 负责全局入口和基础能力
- 各模块细节由独立文件承载
- 业务方可以更清楚地区分“平台基础配置”和“模块专项配置”

## `application.properties` 里配置了什么

### 1. 基础启动开关

当前主配置里最上层的基础项包括：

- `server.port`
- `geelato.web`
- `geelato.schedule`

它们用于控制：

- Web 服务监听端口
- Web 能力是否启用
- 调度能力是否启用

### 2. 插件配置

当前插件相关项包括：

- `geelato.plugin.pluginDirectory`
- `geelato.plugin.pluginRepository`

分别用于：

- 指定插件目录
- 指定插件仓库地址

### 3. 主数据库配置

当前主库配置前缀是：

- `spring.datasource.primary.*`

包括：

- 名称
- JDBC URL
- 用户名
- 密码
- 驱动类
- 校验 SQL
- 初始连接数、最小空闲、最大活跃等池参数

这部分是 quickstart 启动最核心的数据库基础配置。

### 4. SQL 观测与上传配置

主配置里还包含：

- `decorator.datasource.p6spy.*`
- `spring.servlet.multipart.*`

分别用于：

- SQL 代理日志
- 文件上传大小限制

### 5. 日志与扫描配置

日志相关配置包括：

- `logging.config`
- `logging.level.*`
- `logging.pattern.console`

框架扫描相关包括：

- `geelato.meta.scan-package-names`
- `geelato.graal.scan-package-names`

这部分决定：

- 日志输出级别和样式
- 元数据与 Graal 扫描包范围

### 6. 平台运行时基础能力

主配置还直接放了一些跨模块共用的运行时项，例如：

- `geelato.es.debug`
- `geelato.es.debug-max-length`
- `geelato.file.root.path`
- `server.compression.*`
- `spring.data.redis.*`
- `geelato.upload.*`

这些属于：

- 调试辅助
- 文件目录
- 响应压缩
- Redis
- 上传目录

等基础设施能力。

### 7. OCR 与 AI

主配置中还直接保留了：

- Aliyun STS OCR
- Mineru OCR
- PDF OCR
- DeepSeek AI

这类跨业务模块的对接项。

这说明当前 quickstart 的主配置并不只负责纯粹框架底座，也承载了若干通用平台服务的接入参数。

## 配置值的风格

当前配置大量使用：

- `${ENV_NAME:defaultValue}`

这种 Spring 占位表达式。

这意味着：

- 优先从环境变量读取
- 未显式注入时再退回默认值

这种写法更适合：

- 本地开发
- 容器化部署
- 不同环境通过环境变量切换

## 分模块配置如何理解

`properties/` 目录下的每个文件都对应一个专项能力模块。

当前建议的理解方式是：

- 主配置解决“平台最小启动需要什么”
- 模块配置解决“某项专项能力需要什么”

例如：

- `auth.properties` 负责认证相关参数
- `message.properties` 负责消息模块和 RabbitMQ
- `workflow.properties` 负责工作流和 Druid
- `oss.properties` 负责对象存储

这种拆分方式有利于：

- 降低单文件复杂度
- 按模块定位配置问题
- 后续单独抽离模块时保留清晰边界

## 使用建议

当前更推荐这样维护 quickstart 配置：

- 保持 `application.properties` 聚焦全局基础入口
- 模块专项能力尽量放到 `properties/*.properties`
- 环境差异优先通过环境变量覆盖，而不是直接改源码默认值
- 敏感信息如密钥、密码、仓库地址优先走环境变量

## 推荐继续阅读

- [Workflow 模块](workflow.md)
- [Auth 模块](auth.md)
- [Message 模块](message.md)
- [OSS 模块](oss.md)
- [Monitor 模块](monitor.md)
