# Seata 模块

对应配置文件：

- `properties/seata.properties`

## 作用

这个文件用于承载与分布式事务相关的预留配置。

当前主要配置包括：

- `geelato.svcp`
- `seata.enabled`
- `seata.application-id`
- `seata.tx-service-group`

## 关键含义

### 服务能力开关

- `geelato.svcp`

这是一个模块级能力开关，通常用于控制相关服务能力是否参与当前部署。

### Seata 开关

- `seata.enabled`

默认仍然是关闭的，说明 quickstart 当前设计不是默认把 Seata 当成必选依赖。

### 应用与事务组

- `seata.application-id`
- `seata.tx-service-group`

用于标识当前应用在 Seata 事务体系中的身份和事务组归属。

## 使用建议

- 普通单库或轻量多库场景下，不建议默认开启
- 只有明确需要分布式事务时，再结合动态数据源与 Seata 能力一起配置
- 若要进一步做多库事务扩展，继续阅读 [ORM / 数据源扩展](../orm/datasource-extension.md)

## 推荐继续阅读

- [动态数据源](../dynamic-datasource/overview.md)
- [ORM / 数据源扩展](../orm/datasource-extension.md)
