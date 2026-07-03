# Market 模块

对应配置文件：

- `properties/market.properties`

## 作用

这个文件承载 Market 模块自己的数据源与调试日志配置。

## 关键配置

### 模块数据源

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

### 日志级别

包括：

- `logging.level.cn.geelato.market`
- `logging.level.cn.geelato.orm`
- `logging.level.org.springframework.jdbc`

用于强化：

- Market 模块调试
- ORM 执行过程观察
- JDBC 调试输出

## 使用建议

- 如果 Market 使用独立数据库，需显式配置 `GEELATO_MARKET_JDBCURL`
- 生产环境不建议长期保持 DEBUG 级别
- 该模块与主库凭据当前复用了主库用户名密码占位变量，部署时要注意是否真的共用同一账号

## 推荐继续阅读

- [动态数据源](../dynamic-datasource/overview.md)
