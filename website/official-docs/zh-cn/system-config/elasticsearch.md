# Elasticsearch 模块

对应配置文件：

- `properties/elasticsearch.properties`

## 作用

这个文件承载 Elasticsearch 连接参数。

## 关键配置

- `geelato.es.url`
- `geelato.es.username`
- `geelato.es.password`

## 配置含义

这组配置决定：

- 连接哪个 Elasticsearch 服务
- 使用哪个账号鉴权

同时在主配置里还存在：

- `geelato.es.debug`
- `geelato.es.debug-max-length`

它们用于控制调试输出。

## 使用建议

- 生产环境不要把明文密码直接保留在源码默认值中
- 调试开关开启时，要关注日志量和敏感信息暴露
- 地址中不要保留多余空格，避免部署环境下解析异常

## 推荐继续阅读

- [系统配置](overview.md)
