---
title: 系统配置
sidebar_label: 系统配置
---
# 系统配置

本页说明 `geelato-web-quickstart` 的主配置入口、各业务模块的配置文件，以及每个模块的关键配置含义。

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

下面按导入顺序逐个说明各模块配置文件。

## Seata 模块

对应配置文件：

- `properties/seata.properties`

### 作用

这个文件用于承载与分布式事务相关的预留配置。

当前主要配置包括：

- `geelato.svcp`
- `seata.enabled`
- `seata.application-id`
- `seata.tx-service-group`

### 关键含义

#### 服务能力开关

- `geelato.svcp`

这是一个模块级能力开关，通常用于控制相关服务能力是否参与当前部署。

#### Seata 开关

- `seata.enabled`

默认仍然是关闭的，说明 quickstart 当前设计不是默认把 Seata 当成必选依赖。

#### 应用与事务组

- `seata.application-id`
- `seata.tx-service-group`

用于标识当前应用在 Seata 事务体系中的身份和事务组归属。

### 使用建议

- 普通单库或轻量多库场景下，不建议默认开启
- 只有明确需要分布式事务时，再结合动态数据源与 Seata 能力一起配置
- 若要进一步做多库事务扩展，继续阅读 [ORM / 数据源扩展](../orm/datasource-extension.md)

### 推荐继续阅读

- [动态数据源](../dynamic-datasource/overview.md)
- [ORM / 数据源扩展](../orm/datasource-extension.md)

## OSS 模块

对应配置文件：

- `properties/oss.properties`

### 作用

这个文件承载阿里云 OSS 的访问配置，主要用于对象存储接入。

### 关键配置

前缀：

- `geelato.oss.*`

当前包括：

- `accessKeyId`
- `accessKeySecret`
- `endPoint`
- `bucketName`
- `region`

### 使用说明

这组配置决定：

- 上传文件最终落到哪个 OSS 账号和 Bucket
- 访问哪个地域的 OSS 端点
- 后端对象存储能力如何完成鉴权

### 使用建议

- 密钥信息必须通过环境变量注入，不要直接在源码里写死
- `bucketName` 和 `region` 需要与实际 OSS 资源保持一致
- 如果未配置 OSS，宿主工程应确保上传能力有合理的本地或其他存储兜底

## Package 模块

对应配置文件：

- `properties/package.properties`

### 作用

这个文件承载打包发布相关的基础路径配置。

### 关键配置

- `geelato.package.env`
- `geelato.package.path`
- `geelato.package.uploadFolder`

### 配置含义

#### 环境标识

- `geelato.package.env`

用于标识当前打包逻辑运行在哪个环境。

#### 包输出路径

- `geelato.package.path`

用于指定打包产物或中间文件的存放根路径。

#### 上传目录

- `geelato.package.uploadFolder`

用于指定打包相关上传临时目录。

### 使用建议

- 部署环境应根据机器目录结构显式覆盖默认路径
- 上传临时目录与正式产物目录建议区分开
- 如果后续需要更复杂的发布链路，可在此基础上继续扩展

## SC 模块

对应配置文件：

- `properties/sc.properties`

### 作用

这个文件承载 SC 相关能力的开关与站点目录配置。

### 关键配置

- `geelato.sc`
- `geelato.sc.folder`
- `geelato.sc.path`

### 配置含义

#### 模块开关

- `geelato.sc`

用于控制 SC 能力是否启用。

#### 目录与访问路径

- `geelato.sc.folder`
- `geelato.sc.path`

分别用于：

- 指定本地站点文件目录
- 指定站点对外访问的路径片段

### 使用建议

- 本地目录应与部署机真实文件结构保持一致
- 若作为静态站点或资源站点目录使用，需要同时考虑 Web 层映射
- 生产环境建议不要依赖硬编码磁盘路径，优先通过环境变量覆盖

## Auth 模块

对应配置文件：

- `properties/auth.properties`

### 作用

这个文件承载统一认证、微信相关登录以及 `sa-token` 的基础配置。

### 关键配置

#### OAuth2

前缀：

- `geelato.oauth2.*`

当前包括：

- `url`
- `clientId`
- `clientSecret`

它们用于：

- 指向统一认证中心地址
- 标识当前客户端身份
- 完成客户端鉴权

#### 微信

前缀：

- `geelato.wx.*`

当前包括：

- `url`
- `appId`
- `secret`
- `gzh-appId`
- `gzh-secret`

它们主要服务于：

- 微信开放能力接入
- 公众号或相关微信身份能力接入

#### `sa-token`

当前包括：

- `sa-token.token-name`
- `sa-token.is-share`
- `sa-token.is-log`

用于控制：

- token 名称
- 是否共享
- 是否打印日志

### 使用建议

- `clientSecret`、微信密钥等敏感项必须走环境变量
- OAuth2 地址应与统一认证部署地址保持一致
- 如果只是消费统一认证中心 token，还需结合“认证鉴权”章节理解运行时后端如何消费这些凭证

### 推荐继续阅读

- [认证鉴权](../authentication/security-authentication.md)
- [统一认证](../authentication/overview.md)

## Market 模块

对应配置文件：

- `properties/market.properties`

### 作用

这个文件承载 Market 模块自己的数据源与调试日志配置。

### 关键配置

#### 模块数据源

前缀：

- `spring.datasource.market.*`

包括：

- JDBC URL
- 用户名
- 密码
- 驱动

以及 Hikari 连接池参数：

- `pool-name`
- `minimum-idle`
- `maximum-pool-size`
- `connection-timeout`
- `idle-timeout`
- `max-lifetime`
- `connection-test-query`

#### 日志级别

包括：

- `logging.level.cn.geelato.market`
- `logging.level.cn.geelato.orm`
- `logging.level.org.springframework.jdbc`

用于强化：

- Market 模块调试
- ORM 执行过程观察
- JDBC 调试输出

### 使用建议

- 如果 Market 使用独立数据库，需显式配置 `GEELATO_MARKET_JDBCURL`
- 生产环境不建议长期保持 DEBUG 级别
- 该模块与主库凭据当前复用了主库用户名密码占位变量，部署时要注意是否真的共用同一账号

### 推荐继续阅读

- [动态数据源](../dynamic-datasource/overview.md)

## Message 模块

对应配置文件：

- `properties/message.properties`

### 作用

这个文件承载消息模块的：

- 调度处理参数
- 消息专用数据源
- RabbitMQ 连接参数

### 关键配置

#### 调度处理

- `geelato.message.schedule.enabled`
- `geelato.message.schedule.process-interval`
- `geelato.message.schedule.batch-size`

用于控制：

- 是否启动消息调度处理
- 轮询间隔
- 每批处理数量

#### 模块数据源

前缀：

- `spring.datasource.message.*`

包括：

- JDBC URL
- 用户名
- 密码
- 驱动

以及 Hikari 连接池参数。

#### RabbitMQ

包括：

- `spring.rabbitmq.host`
- `spring.rabbitmq.port`
- `spring.rabbitmq.username`
- `spring.rabbitmq.password`
- `spring.rabbitmq.virtual-host`

用于连接消息中间件。

### 使用建议

- 如果消息模块独立用库，应显式设置 `GEELATO_MESSAGE_JDBCURL`
- 若只本地验证，可使用默认 RabbitMQ guest 账号；生产环境必须替换
- `process-interval` 和 `batch-size` 应根据消息积压量与处理能力调优

## 企业微信模块

对应配置文件：

- `properties/weixin_work.properties`

### 作用

这个文件承载企业微信服务端验签相关参数。

### 关键配置

- `wework.validate.token`
- `wework.validate.aeskey`
- `wework.validate.corpid`

### 配置含义

#### Token

- `wework.validate.token`

用于企业微信回调校验时的 token。

#### AES Key

- `wework.validate.aeskey`

用于消息体加解密。

#### CorpId

- `wework.validate.corpid`

用于校验当前企业微信企业身份。

### 使用建议

- 这些参数都属于敏感配置，应优先通过环境变量覆盖
- 必须与企业微信后台配置保持一致，否则会出现验签失败或消息解密失败
- 如同时使用微信与企业微信能力，应注意和 `auth.properties` 中的普通微信配置区分，普通微信配置见上文 [Auth 模块](#auth-模块)

## Elasticsearch 模块

对应配置文件：

- `properties/elasticsearch.properties`

### 作用

这个文件承载 Elasticsearch 连接参数。

### 关键配置

- `geelato.es.url`
- `geelato.es.username`
- `geelato.es.password`

### 配置含义

这组配置决定：

- 连接哪个 Elasticsearch 服务
- 使用哪个账号鉴权

同时在主配置里还存在：

- `geelato.es.debug`
- `geelato.es.debug-max-length`

它们用于控制调试输出。

### 使用建议

- 生产环境不要把明文密码直接保留在源码默认值中
- 调试开关开启时，要关注日志量和敏感信息暴露
- 地址中不要保留多余空格，避免部署环境下解析异常

## Monitor 模块

对应配置文件：

- `properties/monitor.properties`

### 作用

这个文件承载辅助套件监控能力配置。

### 关键配置

前缀：

- `geelato.monitor.auxiliary-suites.*`

当前包括：

- `enabled`
- `poll-interval-seconds`
- `connect-timeout-seconds`
- `read-timeout-seconds`
- `suites-json`

### 配置含义

#### 开关

- `enabled`

用于控制辅助套件监控是否启用。

#### 轮询与超时

- `poll-interval-seconds`
- `connect-timeout-seconds`
- `read-timeout-seconds`

用于控制：

- 监控检查频率
- HTTP 连接超时
- 响应读取超时

#### 监控目标

- `suites-json`

用于定义被监控的外部套件列表。

当前默认示例里包含：

- OpenAPI 健康检查地址

### 使用建议

- 若监控目标较多，应根据实际情况调整轮询频率
- `suites-json` 建议保持结构化和可读性，避免把过多复杂逻辑堆在单个字符串里
- 外部监控地址应优先使用可稳定访问的健康接口

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
