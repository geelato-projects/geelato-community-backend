---
title: Workflow 模块
sidebar_label: Workflow 模块
---
# Workflow 模块

对应配置文件：

- `properties/workflow.properties`

## 作用

这个文件承载的是：

- 工作流模块开关
- Workflow 专用数据源
- Platform 数据源桥接
- Druid 监控页
- Activiti 运行参数

## 关键配置

### 模块开关

- `geelato.workflow`

用于控制工作流能力是否启用。

### SpringDoc

- `springdoc.enable-spring-security=false`

这里体现的是工作流接入场景下对文档与安全联动的显式控制。

### Workflow 数据源

前缀：

- `spring.datasource.workflow.*`

包括：

- JDBC URL
- 用户名
- 密码
- Druid 类型
- 连接池参数

这是工作流引擎自己的数据库连接。

### Platform 数据源

前缀：

- `spring.datasource.platform.*`

这里显式再声明了一套平台数据源，主要用于工作流相关平台协同场景。

### Druid 监控

包括：

- `spring.datasource.druid.stat-view-servlet.*`
- `spring.datasource.druid.web-stat-filter.*`

用于：

- 暴露 Druid 监控页面
- 统计 Web 请求和连接池访问情况

### Activiti 参数

包括：

- `spring.activiti.dbHistoryUsed`
- `spring.activiti.historyLevel`
- `spring.activiti.databaseSchemaUpdate`

用于控制：

- 历史数据记录级别
- 数据库 schema 更新策略

## 使用建议

- 若未启用工作流，优先关闭 `geelato.workflow`
- 工作流库与平台库建议明确区分，不要混淆配置来源
- 生产环境使用 Druid 监控页时，应及时调整默认账号密码

## 推荐继续阅读

- [系统配置](overview.md)
