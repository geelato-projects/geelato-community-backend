---
title: 查询过滤与字段填充 SPI 扩展
sidebar_label: 查询过滤与字段填充 SPI
---

# 查询过滤与字段填充 SPI 扩展

这篇文档面向需要扩展 Geelato 平台默认行为的开发人员，重点说明两组能力：

- 查询过滤 SPI：在查询链路中自动注入租户、权限或其他平台级过滤条件
- 字段值填充 SPI：在保存链路中自动补齐创建人、更新时间、租户编码等默认字段

这两组能力的共同目标，是把“平台默认规则”从 `core/orm` 中抽离出来，改为通过 SPI 在上层工程中显式扩展。

## 什么时候应该扩展

适合扩展的场景：

- 需要在 MQL 或 Fluent DSL 查询中自动附加租户、数据权限、组织隔离等条件
- 需要在保存时统一补齐审计字段、租户字段、组织字段
- 需要让不同宿主项目接入自己的平台规则，而不是直接修改底层框架

不适合扩展的场景：

- 只是某一个业务服务里的局部补丁逻辑
- 只想在个别接口中临时拼接查询条件
- 希望把平台语义重新写回 `geelato-core` 或 `geelato-orm`

## 架构边界

当前职责边界已经固定：

- `geelato-core` / `geelato-orm`
  - 只保留 SPI 契约
  - 只保留上下文对象与运行时解析器
  - 不承载默认租户规则、默认权限规则、默认字段填充规则
- `geelato-web-platform`
  - 承载平台默认实现
  - 承载默认业务规则

如果你的项目要替换默认行为，推荐在业务宿主或平台层实现 SPI，而不是修改底层模块。

## 查询过滤 SPI

### 1. MQL 查询入口

MQL 查询侧使用：

- `cn.geelato.core.mql.spi.MqlQueryFilterInjector`
- `cn.geelato.core.mql.spi.support.MqlQueryFilterRuntimeResolver`

对应调用链路是：

- `JsonTextQueryParser`
  - 解析出 `QueryCommand`
  - 调用 `MqlQueryFilterRuntimeResolver.injectIfAvailable(command)`

接口形态：

```java
public interface MqlQueryFilterInjector {
    boolean isEnabled();
    void inject(QueryCommand command);
}
```

### 2. Fluent DSL 查询入口

Fluent DSL 查询侧使用：

- `cn.geelato.orm.spi.FluentQueryFilterInjector`
- `cn.geelato.orm.spi.support.FluentQueryFilterRuntimeResolver`

对应调用链路是：

- `QueryCommandAdapter`
  - 将 `MetaQuery` 适配为 `QueryCommand`
  - 调用 `FluentQueryFilterRuntimeResolver.injectIfAvailable(command, query)`

接口形态：

```java
public interface FluentQueryFilterInjector {
    boolean isEnabled();
    void inject(QueryCommand command, MetaQuery query);
}
```

### 3. 平台默认实现

当前平台默认实现位于 `geelato-web-platform`：

- `PlatformMqlQueryFilterInjector`
- `PlatformFluentQueryFilterInjector`
- `PlatformQueryFilterSupport`

其中 `PlatformQueryFilterSupport` 负责承载默认租户与权限规则，底层模块不再内置这些规则。

## 字段值填充 SPI

字段值填充按入口拆成三套 SPI，而不是一套大而全的统一接口。

### 1. MQL Save

- `cn.geelato.core.mql.spi.MqlSaveFieldValueFiller`
- `cn.geelato.core.mql.spi.support.MqlSaveFieldValueFillRuntimeResolver`

调用入口：

- `JsonTextSaveParser`

接口形态：

```java
public interface MqlSaveFieldValueFiller {
    boolean isEnabled();
    void fill(MqlSaveFieldValueFillContext context);
}
```

### 2. Fluent DSL Save

- `cn.geelato.orm.spi.FluentSaveFieldValueFiller`
- `cn.geelato.orm.spi.support.FluentSaveFieldValueFillRuntimeResolver`

调用入口：

- `SaveCommandAdapter`

接口形态：

```java
public interface FluentSaveFieldValueFiller {
    boolean isEnabled();
    void fill(FluentSaveFieldValueFillContext context);
}
```

### 3. 对象保存链路

- `cn.geelato.core.meta.spi.EntitySaveFieldValueFiller`
- `cn.geelato.core.meta.spi.support.EntitySaveFieldValueFillRuntimeResolver`

调用入口：

- `EntitySaveParser`

接口形态：

```java
public interface EntitySaveFieldValueFiller {
    boolean isEnabled();
    void fill(EntitySaveFieldValueFillContext context);
}
```

### 4. 平台默认实现

当前平台默认实现位于 `geelato-web-platform`：

- `PlatformMqlSaveFieldValueFiller`
- `PlatformFluentSaveFieldValueFiller`
- `PlatformEntitySaveFieldValueFiller`
- `PlatformFieldValueFillSupport`

其中 `PlatformFieldValueFillSupport` 负责承载默认字段规则，但按入口保留差异，不强行合并成一套全集。

## 统一运行时规则

查询过滤 SPI 与字段值填充 SPI 都遵循同一套运行时规则：

1. 扫描到 `0` 个实现：跳过
2. 扫描到 `1` 个实现：按 `isEnabled()` 决定是否执行
3. 扫描到多个实现：直接抛出 `IllegalStateException`

这套规则的意义是：

- 不做隐式回退
- 让实现是否启用保持可见
- 避免多个平台规则并存时出现不透明叠加

## 如何扩展

推荐扩展方式是在宿主工程或平台工程中新增一个 Spring Bean 实现。

### 示例一：扩展 MQL 查询过滤

```java
@Component
public class DemoMqlQueryFilterInjector implements MqlQueryFilterInjector {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void inject(QueryCommand command) {
        // 这里根据你的平台规则向 QueryCommand 注入过滤条件
    }
}
```

### 示例二：扩展 Fluent DSL 保存字段填充

```java
@Component
public class DemoFluentSaveFieldValueFiller implements FluentSaveFieldValueFiller {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(FluentSaveFieldValueFillContext context) {
        // 这里根据你的平台规则补齐默认字段
    }
}
```

## 扩展时的注意事项

1. 每一类 SPI 在运行时只保留一个启用实现
   - 例如 `MqlQueryFilterInjector` 同时启用两个实现会直接报错
2. 不要把平台规则重新写回 `geelato-core` / `geelato-orm`
3. 查询过滤与字段填充都应尽量做成“框架级复用规则”，不要退化成业务散点补丁
4. 如果规则只适用于某一个入口，就实现对应入口的 SPI，不要强行做一套统一总接口

## 与旧能力的关系

`SaveDefaultValueFiller` / `DefaultSaveDefaultValueFiller` 当前仍保留在 `geelato-orm` 中，但它们已经降级为兼容层，不再是 `SaveCommandAdapter` 主链路的推荐扩展入口。

如果你要扩展当前保存链路，优先使用：

- `MqlSaveFieldValueFiller`
- `FluentSaveFieldValueFiller`
- `EntitySaveFieldValueFiller`

## 验证与排障

可以按下面的顺序验证扩展是否生效：

1. 确认容器里只有一个同类 SPI Bean 处于启用状态
2. 确认 `isEnabled()` 返回 `true`
3. 通过查询或保存链路实际触发对应入口
4. 如果启动或运行时报出 `Multiple ... beans found`，优先检查是否同时注册了多个实现

对字段值填充而言，还需要特别注意：

- `BaseEntityMetaObjectHandler` 当前仍保持现状
- 本次 SPI 主链路覆盖的是：
  - `JsonTextSaveParser`
  - `SaveCommandAdapter`
  - `EntitySaveParser`

## 推荐继续阅读

- [Fluent DSL 指引](../orm/fluent-dsl)
- [MQL 使用指引](../mql/usage)
- [覆盖默认实现](./override-default-implementations)
