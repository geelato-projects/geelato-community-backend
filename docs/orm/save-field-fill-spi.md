# 保存链路字段值填充 SPI

## 目标

把原先分散在各条保存链路里的默认字段填充逻辑收敛为 SPI 扩展点，让 `geelato-core` / `geelato-orm` 只负责定义契约与运行时解析，默认业务规则下沉到 `geelato-web-platform`。

## 第一阶段覆盖范围

本阶段已完成以下三条链路的 SPI 化：

1. `JsonTextSaveParser` 对应的 MQL Save
2. `SaveCommandAdapter` 对应的 Fluent DSL Save
3. `EntitySaveParser` 对应的对象保存链路

`BaseEntityMetaObjectHandler` 暂时保持现状，作为第二阶段衔接项。

## SPI 列表

- `cn.geelato.core.mql.spi.MqlSaveFieldValueFiller`
- `cn.geelato.orm.spi.FluentSaveFieldValueFiller`
- `cn.geelato.core.meta.spi.EntitySaveFieldValueFiller`

每套 SPI 都配有：

- 对应的上下文对象 `*FillContext`
- 对应的运行时解析器 `*RuntimeResolver`

## 运行时规则

三套解析器统一遵循同一策略：

1. 扫描到 `0` 个实现：直接跳过
2. 扫描到 `1` 个实现：按 `isEnabled()` 决定是否执行
3. 扫描到多个实现：抛出 `IllegalStateException`

这样可以保持扩展点透明，不做隐式回退。

## 默认实现位置

平台默认规则位于 `geelato-web-platform`：

- `PlatformMqlSaveFieldValueFiller`
- `PlatformFluentSaveFieldValueFiller`
- `PlatformEntitySaveFieldValueFiller`
- `PlatformFieldValueFillSupport`

其中 `PlatformFieldValueFillSupport` 负责承载默认字段规则，但按入口保留差异，不强行合并成一套全集。

## 审计字段防篡改（安全约定）

`creator`、`creatorName`、`createAt`、`updater`、`updaterName`、`updateAt`、`tenantCode` 等审计字段由填充器按当前登录用户/租户强制写入，**不接受调用方在报文中指定**：

- 覆盖判定以 `EntityMeta.containsField(...)` 的字段存在性为准，而非 `defaultEntityMap.containsKey(...)`。原因：`MetaManager.newDefaultEntityMap` 只收录“有非空默认值且状态启用”的列，注解驱动实体的 String 审计字段默认值为 null 不入表、元数据驱动链路（`platform_dev_column`）的 `enable_status` 经 `Boolean.parseBoolean("1")` 恒为 false，导致旧实现下这些列取不到，调用方传入的伪造 `creator`/`updater` 会绕过填充器直接落库。
- **Insert**：创建审计字段、更新审计字段与租户归属（`tenantCode`）均按当前用户/租户覆盖调用方传入值。
- **Update**：先丢弃调用方传入的 `createAt`/`creator`/`creatorName`/`tenantCode`（不纳入 `updateFields`，保持库中原值），再按当前用户写入 `updateAt`/`updater`/`updaterName`。租户归属创建后不可改，避免跨租户篡改。

回归用例见 `PlatformFieldValueFillSupportTest`。

## 主链路接入点

- `JsonTextSaveParser` 调用 `MqlSaveFieldValueFillRuntimeResolver.fillIfAvailable(...)`
- `SaveCommandAdapter` 调用 `FluentSaveFieldValueFillRuntimeResolver.fillIfAvailable(...)`
- `EntitySaveParser` 调用 `EntitySaveFieldValueFillRuntimeResolver.fillIfAvailable(...)`

这意味着默认字段填充发生在 `SaveCommand` 生成阶段，而不是放回业务代码手工补字段。

## 兼容层说明

`SaveDefaultValueFiller` / `DefaultSaveDefaultValueFiller` 仍保留在 `geelato-orm` 中，当前定位为兼容层，不再作为 `SaveCommandAdapter` 主链路的默认填充入口。

## 已验证内容

已覆盖并通过以下聚焦测试：

- `JsonTextSaveParserTest`
- `EntitySaveParserTest`
- `MqlSaveFieldValueFillRuntimeResolverTest`
- `EntitySaveFieldValueFillRuntimeResolverTest`
- `SaveCommandAdapterTest`
- `FluentSaveFieldValueFillRuntimeResolverTest`
- `DefaultSaveDefaultValueFillerTest`

其中 `geelato-orm` 聚焦测试通过命令：

```bash
mvn -pl geelato-orm -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SaveCommandAdapterTest,FluentSaveFieldValueFillRuntimeResolverTest,DefaultSaveDefaultValueFillerTest test
```
