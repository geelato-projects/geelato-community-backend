# Sample Quickstart

`geelato-sample-quickstart` 是官方最小样例工程，面向框架使用者验证底座是否可以独立消费。

## 为什么它重要

它用于证明：不依赖重平台扩展的前提下，框架底座本身就能完成启动、装配主库并对外提供接口。

## 它包含什么

- `geelato-framework-starter`
- H2 内存数据源
- 最小 Spring Boot 启动入口
- 一个运行时示例接口

## 它刻意不包含什么

- 消息中心
- 市场扩展
- 调度扩展
- 平台鉴权扩展
- 带强实现偏好的上传运行时

## 适用场景

适合在这些场景中作为第一参考：

- 验证 Starter 是否可单独消费
- 验证数据源与 ORM 自动装配链路
- 在不引入无关扩展模块时排查启动问题

## 事实源

当前模块级启动说明仍维护在：

- `../geelato-hello-example/geelato-sample-quickstart/README.md`

官方站负责产品视角说明，模块 README 负责本模块的操作细节。

