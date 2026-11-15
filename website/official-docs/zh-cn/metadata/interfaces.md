---
title: 元数据的持久化与接口
sidebar_label: 持久化与接口
---

# 元数据的持久化与接口

元数据有三种"存在形态":内存注册表(MetaManager)、持久化表(platform_dev_*)、对外契约(REST 端点与 SimpleFieldMeta)。本页说明后两者。注册表与加载见[元数据加载机制](./loading.md)。

## 持久化:platform_dev_* 六表

设计态的定义落在平台库,启动与刷新时由 `MetaStore` SPI 读回:

| 表 | 承载对象 | 说明 |
| --- | --- | --- |
| `platform_dev_table` | `TableMeta` | 表/实体定义,含 connectId、dbType、cacheType、tableType |
| `platform_dev_column` | `ColumnMeta` | 列定义,含类型/长度/精度/选择类型/是否加密 |
| `platform_dev_view` | `TableView` | 视图定义(viewType/viewConstruct/viewColumn) |
| `platform_dev_table_check` | `TableCheck` | CHECK 约束 |
| `platform_dev_table_foreign` | `TableForeign` | 外键关系 |
| `platform_dev_db_connect` | `ConnectMeta` | 数据库连接(表→连接 id→物理库) |

DDL 位于 `geelato-app-scaffold-starter/init/`(如 `platform_dev_table.sql`、`platform_dev_column.sql`)。

这六个类自身都标注 `@Entity`(catalog 为 platform),即元数据的定义本身也由元数据驱动:查询它们、给它们建表、打包部署校验,走的都是同一套 ORM 链路。这也是刷新保护禁止对 `catalog=platform` 实体执行 `refreshDBMeta` 的原因——它们以 Java 类为准(见[元数据加载机制](./loading.md#同名冲突策略))。

## /api/meta 端点

控制器:`cn.geelato.web.platform.srv.meta.MetaController`(`@ApiRestController("/meta")`)。同一前缀承载两类端点:**运行态**(通用数据接口,GQL 报文)与**设计态**(元数据定义的查询与维护)。

### 运行态端点

| 端点 | 方法 | 说明 |
| --- | --- | --- |
| `/api/meta/list` | POST/GET | GQL 单列表查询;`withMeta=true` 时响应附 `meta` 键,内容为请求字段的 SimpleFieldMeta |
| `/api/meta/multiList` | POST | 多实体组合查询 |
| `/api/meta/save/{biz}` | POST | 保存(按主键判 insert/update) |
| `/api/meta/batchSave` | POST | 批量保存 |
| `/api/meta/multiSave` | POST | 多实体保存 |
| `/api/meta/delete/{biz}/{id}` | POST | 按主键删除(删除模式按三级配置解析) |
| `/api/meta/delete2/{biz}` | POST | 按条件删除 |
| `/api/meta/tree/{biz}` | POST | 树形数据查询 |
| `/api/meta/uniqueness` | POST/GET | 唯一性校验 |

这些端点的实现经 `RuleService`,查询结果缓存也在此层(以 entityName 参与缓存 key,失效走 MetaCacheProvider)。

### 设计态端点

| 端点 | 方法 | 说明 |
| --- | --- | --- |
| `/api/meta/defined/{entity}` | POST/GET | 指定实体的 SimpleFieldMeta 列表(对外发布形态) |
| `/api/meta/fullDefined/{entity}` | POST/GET | 完整 EntityMeta(含表/外键/视图定义) |
| `/api/meta/entityNames` | POST/GET | 全部实体名 |
| `/api/meta/entityLiteMetas` | POST/GET | 精简实体列表 |
| `/api/meta/conflicts` | POST/GET | 全部双源冲突 |
| `/api/meta/conflicts/{entity}` | POST/GET | 单实体双源 diff |
| `/api/meta/sync/{entity}` | POST | 触发 refreshDBMeta(direction=db-to-class),多实例手工同步入口 |
| `/api/meta/codegen/{entity}` | POST/GET | 由元数据反向生成带注解的 Java 源码 |
| `/api/meta/codegen/{entity}/download` | GET | 生成源码下载 |

相关控制器:`MetaDdlController`(按元数据生成 DDL)、`geelato-maintenance` 的 `MetaSyncApiController`(三方一致性检查工具页 `/meta-sync.html`)、表/列/视图设计器的 `DevTableController` / `DevTableColumnController` / `DevViewController`(写 platform_dev_* 后调 refreshDBMeta)。

## 前端契约:SimpleFieldMeta

`/api/meta/defined/{entity}` 与 `withMeta=true` 的查询发布的是 `SimpleFieldMeta`——刻意过滤了表名、schema、连接等库信息,只保留渲染所需的属性:

| 属性 | 说明 | 前端用途 |
| --- | --- | --- |
| `name` | 字段名 | 数据键 |
| `type` | Java 类型 simpleName | 值类型推断 |
| `title` | 中文名 | 列头/表单标签 |
| `comment` | 备注 | 提示文案 |
| `nullable` | 可空 | 必填校验 |
| `charMaxLength` | 字符最大长度 | 长度校验 |
| `precision` / `scale` | 精度/小数位 | 数值格式化 |
| `selectType` | 选择类型(设计器控件类型) | 表单控件选型 |
| `typeExtra` / `extraValue` / `extraMap` | 选择类型附加数据/额外值/映射 | 控件配置 |
| `defaultValue` | 默认值 | 新建表单初值 |

低码设计器的表单/表格渲染、值校验全部基于这份契约——前端不需要知道物理表的存在。

## 脚本与 AI 通道

脚本引擎没有 `$gl.meta` kit,元数据能力通过以下通道暴露:

| 通道 | 入口 | 说明 |
| --- | --- | --- |
| `$gl.mql` | MqlKit(`list/multiList/save/batchSave/multiSave/delete/deleteByGql/explain`) | 方法面对齐 /meta/* 运行态端点;内部经 MqlQueryProcessor/MetaQLManager 消费元数据生成 SQL;`entityConnectId(entityName)` 取 `TableMeta.connectId` 做数据源路由;写方法受 `dao.write-enabled` 门控 |
| `$gl.sql` 跨库定位 | MetaTableLocationResolver | 遍历注册表按表名(精确→忽略大小写)找 EntityMeta,取 `resolveConnectId` + `platform_dev_db_connect` 行定位 host/port/db;结果缓存 LocalBoundedCache(60s/4096) |
| MQL 函数参数 | GFunction(increment/findinset/fuzzymatch) | `$实体.字段` 经 MetaManager 翻译为真实列名,生成 `gfn_xxx(...)` 函数 |
| MCP 工具 | MetaModelTool(geelato-mcp) | 向 AI 暴露 getAllEntityNames/getEntityMeta(字段清单),供模型理解数据模型 |

## 运维观测

| 入口 | 说明 |
| --- | --- |
| `PlatformMetaMonitorEndPoint` | 运维端点输出全部 EntityMeta 快照 |
| `/api/meta/conflicts` | 双源冲突清单 |
| `/meta-sync.html` | 三方(类源/元数据库源/物理库源)一致性检查与修复工具页 |

## 延伸阅读

- [元数据运行期操作](./operations.md)——端点背后的 API 与刷新机制
- [元数据的上层延伸](./extensions.md)——运行态端点与脚本通道的完整消费链
- [MetaStore 扩展](../reference/metastore-extension.md)——替换"元数据从哪里来"时,六表契约的字段约定
