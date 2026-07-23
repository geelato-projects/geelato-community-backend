---
title: 查询过滤与字段填充 SPI 扩展
sidebar_label: 查询过滤与字段填充 SPI
---

# 查询过滤与字段填充 SPI 扩展

本页说明查询过滤 SPI 与字段填充 SPI 的使用方式，涵盖三类常见任务：

- 给查询链路自动注入租户、权限、组织等平台级过滤条件。
- 给保存链路自动补齐创建人、更新时间、租户编码等默认字段。
- 在宿主工程中替换平台默认规则，而非直接修改底层框架。

这两组能力的共同目标，是将"平台默认规则"从 `geelato-core / geelato-orm` 中抽离，改为通过 SPI 在上层工程中显式接入。

## 先判断这篇适不适合你

如果你的需求是下面这些，优先看本页：

- 想让 MQL 或 Fluent DSL 查询自动带上租户、权限、组织隔离条件
- 想让保存链路统一补齐审计字段、租户字段、组织字段
- 想让不同宿主项目接入自己的平台规则

如果你的需求是下面这些，本页不是首选：

- 只是某一个业务方法里临时拼接一个额外条件
- 只是单条查询临时切数据源
- 只是保存前后想做监听、通知、镜像同步

这类需求优先看：

- [ORM / 数据源扩展](../orm/datasource-extension.md)
- [ORM 事件特性](../orm/event-features.md)

## 先判断你要做的是哪一类扩展

在动手前，建议先把场景分清楚：

- 想影响查询：实现“查询过滤 SPI”
- 想影响保存：实现“字段值填充 SPI”
- 想影响 MQL：选 MQL 对应 SPI
- 想影响 Fluent DSL：选 Fluent DSL 对应 SPI
- 想影响对象保存链路：选 Entity Save 对应 SPI

这几个入口相似，但触发位置不同。先选错入口，后面代码写得再完整也不会生效。

## 架构边界

当前职责边界已经固定：

- `geelato-core / geelato-orm`
  - 只保留 SPI 契约
  - 只保留上下文对象与运行时解析器
  - 不承载默认租户规则、默认权限规则、默认字段填充规则
- `geelato-web-platform`
  - 承载平台默认实现
  - 承载默认业务规则

因此如果你的项目要替换默认行为，推荐在业务宿主或平台层实现 SPI，而不是把规则重新写回底层模块。

## 最短接入步骤

如果你只是想快速接入一条平台规则，可以直接按下面 4 步做。

### 第 1 步：先决定入口

可以按这张对照表来选：

- MQL 查询：`MqlQueryFilterInjector`
- Fluent DSL 查询：`FluentQueryFilterInjector`
- MQL 保存：`MqlSaveFieldValueFiller`
- Fluent DSL 保存：`FluentSaveFieldValueFiller`
- 对象保存链路：`EntitySaveFieldValueFiller`

一个实用经验是：

- 规则要影响前端/平台通用接口，就优先看 MQL
- 规则要影响后端 Java 服务，就优先看 Fluent DSL
- 规则要影响对象直存链路，就看 Entity Save

### 第 2 步：实现一个 SPI Bean

推荐直接在宿主工程里新增一个 Spring Bean，而不是先去改底层模块。

### 第 3 步：保证同类 SPI 只有一个启用实现

同类 SPI 不是“多个一起叠加执行”的设计，而是“最多保留一个启用实现”。

### 第 4 步：用真实查询或保存链路验证

不要只看 Spring 启动成功。一定要实际触发一条查询或保存，确认你的规则真的进到了执行链路里。

## 查询过滤 SPI

查询过滤 SPI 用来在查询链路里自动注入平台级条件，例如：

- 租户隔离
- 数据权限
- 组织隔离
- 默认只查有效数据

### 入口 1：MQL 查询

MQL 查询侧使用：

- `cn.geelato.core.mql.spi.MqlQueryFilterInjector`
- `cn.geelato.core.mql.spi.support.MqlQueryFilterRuntimeResolver`

对应调用链路：

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

适用场景：

- 页面列表查询
- 平台通用数据接口
- 低代码配置场景

### 入口 2：Fluent DSL 查询

Fluent DSL 查询侧使用：

- `cn.geelato.orm.spi.FluentQueryFilterInjector`
- `cn.geelato.orm.spi.support.FluentQueryFilterRuntimeResolver`

对应调用链路：

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

适用场景：

- 后端 Java 服务查询
- `MetaFactory.query(...)` 发起的查询链路

### 平台默认实现

当前平台默认实现位于 `geelato-web-platform`：

- `PlatformMqlQueryFilterInjector`
- `PlatformFluentQueryFilterInjector`
- `PlatformQueryFilterSupport`

其中 `PlatformQueryFilterSupport` 负责承载默认租户与权限规则，底层模块不再内置这些规则。

## 字段值填充 SPI

字段值填充 SPI 用来在保存链路里自动补齐默认字段，例如：

- 创建人
- 创建时间
- 更新人
- 更新时间
- 租户编码
- 组织字段

字段值填充按入口拆成三套 SPI，而不是做成一套大而全的统一接口。

### 入口 1：MQL Save

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

适用场景：

- 前端通过 MQL 发起保存
- 平台通用保存接口

### 入口 2：Fluent DSL Save

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

适用场景：

- 后端 Java 服务通过 `MetaFactory.insert/update(...)` 发起保存

### 入口 3：对象保存链路

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

适用场景：

- 直接走对象保存解析链路的宿主工程

### 平台默认实现

当前平台默认实现位于 `geelato-web-platform`：

- `PlatformMqlSaveFieldValueFiller`
- `PlatformFluentSaveFieldValueFiller`
- `PlatformEntitySaveFieldValueFiller`
- `PlatformFieldValueFillSupport`

其中 `PlatformFieldValueFillSupport` 负责承载默认字段规则，但按入口保留差异，不强行合并成一套全集。

## 如何实现 SPI

下面给出一套更接近实操的写法。

### 场景 1：给 MQL 查询补租户过滤

适合场景：

- 前端列表查询统一按租户隔离
- 平台通用接口统一附加权限条件

最小示例：

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

### 场景 2：给 Fluent DSL 查询补平台级过滤

适合场景：

- 后端 Java 服务查询自动带租户、权限、组织条件

最小示例：

```java
@Component
public class DemoFluentQueryFilterInjector implements FluentQueryFilterInjector {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void inject(QueryCommand command, MetaQuery query) {
        // 这里向 Fluent DSL 查询命令注入平台级过滤条件
    }
}
```

### 场景 3：给 Fluent DSL 保存补默认字段

适合场景：

- 后端 Java 服务保存时自动补齐创建人、更新时间、租户编码

最小示例：

```java
@Component
public class DemoFluentSaveFieldValueFiller implements FluentSaveFieldValueFiller {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(FluentSaveFieldValueFillContext context) {
        // 这里补齐默认保存字段
    }
}
```

### 场景 4：给 MQL 保存补默认字段

适合场景：

- 前端/平台协议侧保存时统一补字段

最小示例：

```java
@Component
public class DemoMqlSaveFieldValueFiller implements MqlSaveFieldValueFiller {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(MqlSaveFieldValueFillContext context) {
        // 这里补齐 MQL 保存链路的默认字段
    }
}
```

## 统一运行时规则

查询过滤 SPI 与字段值填充 SPI 都遵循同一套运行时规则：

1. 扫描到 `0` 个实现：跳过
2. 扫描到 `1` 个实现：按 `isEnabled()` 决定是否执行
3. 扫描到多个实现：直接抛出 `IllegalStateException`

这套规则一定要记住，因为它直接决定排障方向。

它的意义是：

- 不做隐式回退
- 让实现是否启用保持可见
- 避免多个平台规则并存时出现不透明叠加

这也意味着：

- 不要同时启用两个 `MqlQueryFilterInjector`
- 不要同时启用两个 `FluentQueryFilterInjector`
- 不要同时启用两个 `MqlSaveFieldValueFiller`
- 不要同时启用两个 `FluentSaveFieldValueFiller`

如果项目里确实存在多个候选实现，必须在宿主层先收敛成唯一启用实现。

## 推荐的实操顺序

如果你要在一个新项目里接入 SPI 扩展，推荐顺序是：

1. 先确定规则作用在哪条链路上
2. 再选对 SPI 接口
3. 再实现一个最小 Bean
4. 再确认同类 SPI 只有一个启用实现
5. 最后用真实查询或保存链路验证

这样做的好处是，一旦出问题，你能快速判断到底是：

- SPI 选错了入口
- Bean 没注册成功
- `isEnabled()` 返回了 `false`
- 同类实现注册了多个
- 实际业务根本没有走到你以为的那条链路

## 一步一步排障

如果你感觉“SPI 没生效”，建议按这个顺序排查：

1. 先确认当前请求到底走的是 MQL、Fluent DSL，还是对象保存链路
2. 再确认你实现的是否是对应入口的 SPI
3. 再确认容器里是否只存在一个同类 SPI Bean
4. 再确认 `isEnabled()` 是否返回 `true`
5. 再通过真实查询或保存链路触发一次
6. 如果报出 `Multiple ... beans found`，优先检查是否同时注册了多个实现

对字段值填充而言，还需要特别注意：

- `SaveDefaultValueFiller / DefaultSaveDefaultValueFiller` 现在只是兼容层
- 当前推荐主扩展入口是：
  - `MqlSaveFieldValueFiller`
  - `FluentSaveFieldValueFiller`
  - `EntitySaveFieldValueFiller`
- `BaseEntityMetaObjectHandler` 当前仍保持现状，不等于当前这套 SPI 主链路

## 扩展时的注意事项

1. 不要把平台规则重新写回 `geelato-core / geelato-orm`
2. 查询过滤与字段填充应尽量做成“框架级复用规则”，不要退化成业务散点补丁
3. 如果规则只适用于某一个入口，就实现对应入口的 SPI，不要强行做一套统一总接口
4. 如果只是个别接口的局部逻辑，优先放在业务层，不要滥用 SPI

## 与旧能力的关系

`SaveDefaultValueFiller / DefaultSaveDefaultValueFiller` 当前仍保留在 `geelato-orm` 中，但它们已经降级为兼容层，不再是 `SaveCommandAdapter` 主链路的推荐扩展入口。

如果你要扩展当前保存链路，优先使用：

- `MqlSaveFieldValueFiller`
- `FluentSaveFieldValueFiller`
- `EntitySaveFieldValueFiller`

## 推荐继续阅读

- [ORM 总览](../orm/overview.md)
- [ORM / 数据源扩展](../orm/datasource-extension.md)
- [Fluent DSL 指引](../orm/fluent-dsl.md)
- [MQL 使用指引](../mql/usage.md)
