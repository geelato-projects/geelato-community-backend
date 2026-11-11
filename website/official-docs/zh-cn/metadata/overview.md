---
title: 元数据说明
sidebar_label: 元数据说明
---

# 元数据说明

元数据（`EntityMeta`）是 Geelato 框架的基石：ORM 的实体映射、MQL 的查询解析、实体到数据源的路由、字段自动填充等能力都由元数据驱动。

## 核心概念

| 概念 | 说明 |
| --- | --- |
| `EntityMeta` | 实体元数据：表名、字段映射、主键、校验规则、视图定义等 |
| `MetaManager` | 元数据管理器：负责元数据的加载、缓存与冲突检测，位于启动链路的核心位置 |
| `MetaStore` | 元数据**定义来源** SPI（`geelato-core` 的 `cn.geelato.core.meta.spi` 包），决定"元数据从哪里来" |
| `MetaResourceProvider` | 元数据相关**静态资源**来源 SPI（默认列定义、列选择类型、表升级列） |

## 元数据从哪里来

框架把"元数据定义从哪里来"抽象成 `MetaStore` SPI，框架层只认接口，具体来源可替换：

- **平台设计器表**：`DefaultMetaStore` 从平台设计器的五张表（`platform_dev_*`）读取定义，是默认实现之一。
- **注解声明**：实体类上通过 `@Model`、`@Col` 等注解直接声明映射，与设计器定义是两个来源，同名实体先到先得。
- **自定义来源**：JSON 文件、配置中心、外部元数据服务等——实现 `MetaStore` 接口并标 `@Component @Primary` 即可整条替换来源链路，不动框架一行代码。

:::info 文档建设状态
本页为骨架页。元数据缓存结构、冲突检测规则、视图元数据、刷新机制等细节将在后续版本补充。
:::

## 延伸阅读

- [MetaStore 扩展](../reference/metastore-extension.md)——自定义元数据来源的实现指南
- [核心模块说明](../reference/core-modules.md)——`MetaManager` 在整体架构中的位置
- [启动过程](../reference/startup-process.md)——元数据加载发生的时机
- [ORM 注解说明](../orm/annotations.md)——注解声明实体的写法
