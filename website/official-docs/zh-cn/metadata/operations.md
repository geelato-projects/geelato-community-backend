---
title: 元数据运行期操作
sidebar_label: 元数据运行期操作
---

# 元数据运行期操作

本页汇总元数据在运行期的全部操作入口:MetaManager 的查询/注册/刷新 API、EntityMeta 的字段级查询、以及 refreshDBMeta 的触发场景。加载机制见[元数据加载机制](./loading.md)。

## MetaManager 查询 API

路径:`cn.geelato.core.meta.MetaManager`。

### 按实体取元数据

| 方法 | 说明 |
| --- | --- |
| `get(Class clazz)` | 按 Java 类取;内部经 `MetaReflex.getEntityName(clazz)` 转实体名查主表 |
| `getByEntityName(String entityName)` | 按实体名取(主注册表) |
| `getByEntityName(String entityName, boolean cache)` | `cache=false` 时先对 DB 源实体执行 refreshDBMeta 再取,用于需要最新定义的场景 |
| `get(String tableName)` | 按表名取(查 `tableNameMetadataMap`) |
| `containsEntity(String entityName)` | 判定实体是否已注册;MQL 校验的实体存在性检查用它 |
| `getAll()` / `getAllEntityNames()` / `getAllEntityLiteMetas()` | 全量实体 / 全量实体名 / 精简列表 |

### 双源取与对比

| 方法 | 说明 |
| --- | --- |
| `getClassSourceEntity(String)` | 取 Java 类源副本 |
| `getDatabaseSourceEntity(String)` | 取 DB 在线源副本 |
| `compareEntitySources(String)` | 单实体双源字段级 diff(Map 结果) |
| `compareEntitySourcesAll(String)` | 双源全量对比(冲突检测开启时由 parseDBMeta 调用) |
| `getConflictingEntityNames()` / `getAllConflicts()` / `getConflictCount()` | 冲突清单,支撑 `/api/meta/conflicts` |

### 路由与派生信息

| 方法 | 说明 |
| --- | --- |
| `resolveConnectId(String entityName)` | 实体→连接 id:优先 `TableMeta.connectId`,其次 `catalogConnectIdMapping[catalog]`,都无则返回 null 由调用方落到默认 primary 数据源 |
| `isMaterializedEntity(String entityName)` | 判定是否物化视图实体(物化只读门禁与 MQL 字段剥除都用它) |
| `newDefaultEntityMap(String|EntityMeta)` | 按字段默认值构造新实体 Map(保存报文的默认值兜底) |
| `getDefaultColumn()` / `getColumnSelectType()` / `getTableUpgradeList()` | MetaResourceProvider 静态资源:默认列定义、列选择类型、表升级列 |

### 字段级查询

字段级查询下沉到 `EntityMeta`,MetaManager 不重复提供:

| 方法 | 说明 |
| --- | --- |
| `getFieldMeta(String fieldName)` | 字段名→FieldMeta |
| `getFieldMetaByColumn(String columnName)` | 列名→FieldMeta;找不到抛异常(带实体名/表名/列名) |
| `getColumnName(String fieldName)` | 字段名→列名;取不到抛异常 |
| `getSimpleFieldMetas(String[])` / `getAllSimpleFieldMetas()` | 对外发布的精简字段元数据 |
| `containsField(String)` | 字段存在性;审计填充/逻辑删除/权限注入都以此为准 |
| `isIgnoreUpdateField(String)` | 是否不可更新字段(创建类字段与主键) |
| `getEncryptedColumnNames()` / `getJsonFieldKeys()` | 加密列名集合 / JSON 列键集合(懒计算) |
| `getViewMeta(String viewName)` | 按视图名取关联视图 |

## 注册与移除

| 方法 | 说明 |
| --- | --- |
| `parseOne(Class clazz)` | 类源注册。已存在则跳过(conflictDetect 开启时 warn) |
| `parseTableEntity(TableMeta, List<ColumnMeta>, List<TableView>, List<TableCheck>, List<TableForeign>)` | DB 表源注册/合并,冲突按[加载机制](./loading.md#同名冲突策略)裁决 |
| `parseViewEntity(TableView)` / `parseViewEntity(List<TableView>)` | 视图源注册(物化优先) |
| `removeOne(String entityName)` | 从主表与来源缓存移除(DATABASE 策略覆盖前、refreshDBMeta 内部使用) |
| `removeLiteMeta(String entityName)` | 同步移除精简列表项 |

:::tip 动态注册的典型用法
非平台宿主(如 geelato-dyn 模块)可关闭自动扫描,改用 `MetaManager.singleInstance().parseOne(clazz)` 手工注册,见[动态集成模块开发](../geelato-dyn/develop-module.md)。
:::

## 运行期刷新:refreshDBMeta

`MetaManager#refreshDBMeta(String entityName)` 把指定实体的 DB 在线源定义重新装载,覆盖内存中的旧定义:

```mermaid
flowchart TB
    A["refreshDBMeta(entityName)"] --> B{"catalog = platform ?"}
    B -->|"是"| C["抛异常,禁止刷新<br/>(platform 实体以类为准,刷新无意义)"]
    B -->|"否"| D["refreshTableMeta:<br/>metaStore.loadByEntityName → removeOne → parseTableEntity"]
    B -->|"否"| E["refreshViewMeta:<br/>metaStore.loadByViewName → removeOne → parseViewEntity"]
    D & E --> F["主注册表与来源缓存同步更新"]
```

### 触发场景

| 触发点 | 时机 |
| --- | --- |
| `DevTableController`(保存/同步/启用) | 表设计器保存表定义后 |
| `DevTableColumnController`(增/改/删列) | 列设计器变更列定义后 |
| `DevViewController` | 视图设计器保存后 |
| `MaterializedViewService` | 物化视图创建/刷新后 |
| `MetaController#sync`(`/api/meta/sync/{entity}`,direction=db-to-class) | 手工或跨实例同步 |
| `getByEntityName(name, cache=false)` | 需要最新定义的即席查询 |

设计器链路的完整闭环是:写 `platform_dev_*` 表 → 调 `refreshDBMeta` → `/api/meta` 立即反映新定义,无需重启。

## 双源一致性维护

双源模型的成本是"类定义与设计器定义可能漂移"。框架提供的维护手段:

| 手段 | 入口 |
| --- | --- |
| 冲突检测 | `geelato.meta.conflict-detect.enabled=true` 后,parseDBMeta 输出 diff,`/api/meta/conflicts` 可查 |
| 单实体对比 | `compareEntitySources(entityName)` |
| 三方一致性检查 | `geelato-maintenance` 的 `ConsistencyChecker`:Java 类源 / 元数据库源 / 物理库源三方逐字段 diff 与修复(`/meta-sync.html` 工具页) |
| 反向生成 | `EntityJavaSourceGenerator`(`/api/meta/codegen/{entity}`)由元数据反向生成带注解的 Java 源码,消除类源漂移 |
| 打包校验 | 打包前校验"物理表列 ⊆ 实体 FieldMeta 列",不一致禁止打包(详见[打包部署](../packaging/contents.md)) |

## 延伸阅读

- [元数据加载机制](./loading.md)——注册表结构与冲突策略
- [元数据的持久化与接口](./interfaces.md)——refreshDBMeta 触发方对应的 REST 端点
- [元数据的上层延伸](./extensions.md)——这些 API 被哪些子系统消费
