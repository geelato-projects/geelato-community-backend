---
title: Message 模块
sidebar_label: Message 模块
---
# Message 模块

对应配置文件：

- `properties/message.properties`

## 作用

这个文件承载消息模块的：

- 调度处理参数
- 消息专用数据源
- RabbitMQ 连接参数

## 关键配置

### 调度处理

- `geelato.message.schedule.enabled`
- `geelato.message.schedule.process-interval`
- `geelato.message.schedule.batch-size`

用于控制：

- 是否启动消息调度处理
- 轮询间隔
- 每批处理数量

### 模块数据源

前缀：

- `spring.datasource.message.*`

包括：

- JDBC URL
- 用户名
- 密码
- 驱动

以及 Hikari 连接池参数。

### RabbitMQ

包括：

- `spring.rabbitmq.host`
- `spring.rabbitmq.port`
- `spring.rabbitmq.username`
- `spring.rabbitmq.password`
- `spring.rabbitmq.virtual-host`

用于连接消息中间件。

## 使用建议

- 如果消息模块独立用库，应显式设置 `GEELATO_MESSAGE_JDBCURL`
- 若只本地验证，可使用默认 RabbitMQ guest 账号；生产环境必须替换
- `process-interval` 和 `batch-size` 应根据消息积压量与处理能力调优

## 推荐继续阅读

- [系统配置](overview.md)
