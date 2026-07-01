# Monitor 模块

对应配置文件：

- `properties/monitor.properties`

## 作用

这个文件承载辅助套件监控能力配置。

## 关键配置

前缀：

- `geelato.monitor.auxiliary-suites.*`

当前包括：

- `enabled`
- `poll-interval-seconds`
- `connect-timeout-seconds`
- `read-timeout-seconds`
- `suites-json`

## 配置含义

### 开关

- `enabled`

用于控制辅助套件监控是否启用。

### 轮询与超时

- `poll-interval-seconds`
- `connect-timeout-seconds`
- `read-timeout-seconds`

用于控制：

- 监控检查频率
- HTTP 连接超时
- 响应读取超时

### 监控目标

- `suites-json`

用于定义被监控的外部套件列表。

当前默认示例里包含：

- OpenAPI 健康检查地址

## 使用建议

- 若监控目标较多，应根据实际情况调整轮询频率
- `suites-json` 建议保持结构化和可读性，避免把过多复杂逻辑堆在单个字符串里
- 外部监控地址应优先使用可稳定访问的健康接口

## 推荐继续阅读

- [系统配置](overview.md)
