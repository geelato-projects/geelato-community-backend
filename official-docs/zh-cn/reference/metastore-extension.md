# MetaStore 扩展

当前框架已经把元数据定义来源抽象为 `MetaStore` SPI，用来支持“平台表默认实现”之外的上层替换。

## 当前可见扩展点

当前已经落地的元数据 SPI 包括：

- `MetaStore`
- `MetaStoreProvider`
- `MetaResourceProvider`
- `MetaBootstrap`

这些扩展点都位于 `geelato-core` 的 `cn.geelato.core.meta.spi` 包下。

## 默认实现

当前模块内默认实现是 `DefaultMetaStore`。

它的职责是：

- 从当前平台表结构读取表定义
- 读取列、视图、校验和外键信息
- 按实体名或视图名返回元数据定义包

这意味着默认实现仍然兼容现有平台表，但不再把“平台表”硬写死成唯一的框架入口。

## 装配方式

`geelato-web-runtime` 中承接的 `MetaConfiguration` 当前支持按 Bean 可选注入：

- `MetaStore`
- `MetaResourceProvider`
- `MetaBootstrap`

如果你在宿主工程中提供了这些 Bean，`MetaManager` 会优先使用你注入的实现，然后再执行：

- 包扫描元数据解析
- 数据库元数据解析
- 可选的自定义 bootstrap

## 最典型的替换场景

适合自定义 `MetaStore` 的场景包括：

- 不使用平台默认元数据表
- 元数据定义来自外部配置中心
- 元数据定义来自 JSON / YAML / 文件系统
- 需要在启动时合并多种元数据来源

## 推荐实现边界

建议把职责拆开：

- `MetaStore`：负责定义来源
- `MetaResourceProvider`：负责资源文件来源
- `MetaBootstrap`：负责启动后补充初始化

这样可以避免把扫描、读取、缓存、启动初始化全部揉进一个实现类。

## 使用建议

如果只是兼容现有平台表，继续使用默认实现即可。

如果是新项目或非平台表结构项目，推荐：

1. 自定义 `MetaStore`
2. 保留 `MetaManager` 作为统一消费入口
3. 继续使用框架现有的扫描、缓存和 ORM / MQL 访问链路

## 推荐继续阅读

- [核心模块说明](core-modules.md)
- [覆盖默认实现](override-default-implementations.md)
- [新项目最小接入](../guide/minimal-integration.md)
