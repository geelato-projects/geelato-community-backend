# 核心模块说明

这一页说明当前对框架使用者真正有复用价值的基础模块角色。

## Foundation 模块

### `geelato-lang`

- 承载基础契约、API 结果模型与低层注解

### `geelato-utils`

- 承载通用工具能力与跨模块辅助能力

### `geelato-security`

- 承载安全契约与上下文模型

### `geelato-core`

- 承载 Meta、DAO、脚本和核心运行管理能力

### `geelato-orm`

- 承载 Fluent DSL 与 ORM 侧执行能力

### `geelato-dynamic-datasource`

- 承载可选的动态数据源能力，并已收敛为更轻量默认行为

## 当前关键架构约束

- `SecurityContext` 只能在鉴权成功后由安全链路写入
- 兼容回退时 `dynamicDao` 仍优先于 `primaryDao`
- 带强实现偏好的上传能力不进入最小 Starter 底座

## 推荐继续阅读

- [ORM 总览](../orm/overview.md)
- [ORM 注解说明](../orm/annotations.md)
- [Fluent DSL 指引](../orm/fluent-dsl.md)
- 最小样例：`geelato-sample-quickstart`
- 运行时 / 设计时拆分：本官方站中的对应页面
