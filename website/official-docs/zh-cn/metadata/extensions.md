---
title: 元数据的上层延伸
sidebar_label: 元数据的上层延伸
---

# 元数据的上层延伸

本页回答一个问题:**有了元数据,平台延伸出了什么**。每个子系统都以 MetaManager 的注册表为唯一事实来源,本页逐个给出消费入口(类#方法)、消费的元数据对象、用途。

:::info 阅读方式
各小节按"离元数据最近"到"离元数据最远"排列。每节的入口路径都可在对应模块源码中定位,类名均为全路径的最后一段。
:::

## ORM CRUD:SQL 生成的全部依据

所有 SQL Provider 经 `MetaBaseSqlProvider#getEntityMeta` 拿到 EntityMeta 后才开始生成 SQL——元数据缺席时直接报错,不存在"无元数据的降级 SQL"。

| 消费入口 | 拿什么 | 干什么 |
| --- | --- | --- |
| `MetaInsertSqlProvider#buildOneSql` | tableName、`getColumnName(field)`、ColumnMeta.dataType | 拼 INSERT 结构名/列名,按列类型定 JDBC type |
| `MetaUpdateSqlProvider#buildOneSql` | tableName、`isIgnoreUpdateField()` | 拼 UPDATE SET 子句,跳过创建类字段与主键 |
| `MetaDeleteSqlProvider#buildOneSql` | tableName、deleteMode(经 `DeleteCommands.resolve`) | 逻辑删除生成 update,物理删除生成 delete |
| `MetaQuerySqlProvider#buildOneSql/buildForeignJoins` | 字段列映射、TableForeign、ViewMeta.viewConstruct、dbType | 拼 SELECT、按外键元数据自动 left join 并选展示列、按 dbType 加方言引用符 |
| `MetaBaseSqlProvider#requireFilterFieldMeta` | FieldMeta + dataType | 过滤字段校验(不存在抛 InvalidFilterFieldException)、参数 SQL 类型绑定、JSON 列禁用比较 |
| `EntitySaveParser#parse` | 全部 fieldMetas、主键、newDefaultEntityMap | 实体对象→SaveCommand:按主键判 insert/update、生成主键、触发字段填充 SPI |
| `Dao#convert` / `CommonRowMapper` | getJsonFieldKeys、字段列映射 | 行数据→实体/Map 映射 + JSON 列反序列化 + 加密列解密 |
| `EncryptedColumns#resolve` | getEncryptedColumnNames | 查询列解密(含 join 实体的列并集) |
| `PageTotalStrategy#uniqueKeyBounded` | `getId()` 主键 | 主键等值条件命中时跳过 count SQL |
| `SaveCommandAdapter` / `DeleteCommandAdapter`(Fluent API) | 主键、默认值、deleteMode | MetaInsert/MetaUpdate/MetaDelete → SaveCommand/DeleteCommand 适配 |

## MQL:平台查询语言的合法性来源

MQL 报文里写的实体名与字段名,必须先通过元数据校验才有资格变成 SQL:

| 消费入口 | 拿什么 | 干什么 |
| --- | --- | --- |
| `CommandValidator#validateEntity/validateField` | containsEntity、containsField、getColumnName、tableForeigns | 实体存在性校验、字段存在性校验、`ref(xxx->yyy)` 外键判定 |
| `JsonTextQueryParser` | isMaterializedEntity | 物化视图查询剥除其不存在的字段(stripNonExistentFields) |
| `JsonTextSaveParser` | EntityMeta、newDefaultEntityMap、encryptedColumnNames | 保存报文字段→SaveCommand、默认值兜底、加密列识别 |
| `JsonTextDeleteParser` | EntityMeta | 解析 `@physicalDelete`、按实体字段填逻辑删除值 |
| `MqlQueryProcessor#applyViewTemplateParams` | ViewMeta.viewType | 仅虚拟(VIRTUAL)视图允许注入 `@pf` 模板参数 |
| `MqlFuzzymatchRouteResolver` | getFieldMetaByColumn | 模糊搜索路由 Lucene 时做列名↔字段名换算 |

## 数据源路由:实体→数据源的归属判定

| 消费入口 | 拿什么 | 干什么 |
| --- | --- | --- |
| `DataSourceInterceptor#aroundDaoMethod` | 从 Dao 入参解析 entityName | 拦截所有 Dao 方法,按实体切数据源 |
| `EntityDataSourceResolver#resolveFromMetaData` | `MetaManager.resolveConnectId(entityName)` | 路由 key = TableMeta.connectId 优先,catalog 映射次之;归属数据源未注册时硬失败,不回退主库 |
| `MetaBaseSqlProvider#resolveEffectiveDbType` | tableMeta.dbType → connectId → 默认库 | 逐实体确定 SQL 方言(标识符引用符等) |
| `MaterializedViewGuard#assertWritable` | isMaterializedEntity | 物化视图实体只读门禁,拦截写方法 |

路由的完整规则见[实体与数据源绑定](../dynamic-datasource/entity-binding.md)。

## 行级权限与租户过滤:按实体名挂规则

| 消费入口 | 拿什么 | 干什么 |
| --- | --- | --- |
| `PlatformQueryFilterSupport#applyTenantFilter/applyDataPermissionFilter` | command.getEntityName() | 注入 tenantCode 过滤;按实体取用户数据权限规则(SQL 片段,默认兜底 `creator='当前用户'`),写入 originalWhere |
| `MetaQuerySqlProvider#decorateOriginalWhere` | fieldMetas | 把权限规则里的裸列名自动限定为 `别名.列名`,使规则正确落入带 join 的 SQL |

权限规则本身按实体名组织——没有元数据提供的稳定实体名,行级权限就没有挂接点。

## 删除模式与审计字段填充:按字段存在性开关

| 机制 | 消费入口 | 元数据依赖 |
| --- | --- | --- |
| 逻辑删除 | `DeleteCommands#resolve/fillLogicDeleteValues` | 模式优先级:调用级显式 > `EntityMeta.deleteMode` > 全局默认;LOGIC 要求实体含 delStatus 字段,按实体实际拥有的字段填 delStatus/deleteAt/updateAt/updater/updaterName |
| 审计填充 | `PlatformFieldValueFillSupport#applyMqlDefaults/applyFluentDefaults/applyEntityDefaults` | 以 `containsField` 判定可写性:插入填 createAt/creator/creatorName/tenantCode/buId/deptId,更新填 updateAt/updater/updaterName 并剥离创建类字段防伪造 |

## 视图与物化视图:方言与门禁

| 消费入口 | 拿什么 | 干什么 |
| --- | --- | --- |
| `MetaReflex#getEntityMetaByView` | 主体实体的 TableMeta.dbType | 视图自身无连接时继承主体 dbType(方言按主体解析);物化副本有自身连接则不继承 |
| `MetaQuerySqlProvider#buildFromSql` | ViewMeta.viewConstruct + viewType | 虚拟视图以 viewConstruct + `@pf` 渲染为派生表;物化视图按物理名直查 |
| `MetaViewQuerySqlProvider` | ViewMeta 列映射 | 命名视图命令的查询 SQL 构建 |

详见[物化视图](../dynamic-datasource/materialized-view.md)。

## 脚本引擎与 AI 通道

脚本引擎没有 `$gl.meta` kit,元数据能力通过以下通道暴露给脚本与外部工具:

| 通道 | 入口 | 说明 |
| --- | --- | --- |
| `$gl.mql` | MqlKit(`list/multiList/save/batchSave/multiSave/delete/deleteByGql/explain`) | 以 MQL 报文驱动的元数据查询通道;内部经 MqlQueryProcessor/MetaQLManager 消费元数据生成 SQL;`entityConnectId(entityName)` 取 `TableMeta.connectId` 做数据源路由;写方法受 `dao.write-enabled` 门控 |
| `$gl.sql` 跨库定位 | MetaTableLocationResolver | 遍历注册表按结构名(精确→忽略大小写)找 EntityMeta,取 `resolveConnectId` + 连接定义(`ConnectMeta`)定位物理库;结果缓存 LocalBoundedCache(60s/4096) |
| MQL 函数参数 | GFunction(increment/findinset/fuzzymatch) | `$实体.字段` 经 MetaManager 翻译为真实列名,生成 `gfn_xxx(...)` 函数 |
| MCP 工具 | MetaModelTool(geelato-mcp) | 向 AI 暴露 getAllEntityNames/getEntityMeta(字段清单),供模型理解数据模型 |

## 归档:以元数据为前置条件

`ArchivePolicyService#enableArchiveForEntity` 经 `MetaManager.get(entityName)` 取结构名与标题生成归档策略——**无元数据的裸结构拒绝归档**。归档查询模式下,按实体归属判定引导到通用元数据查询通道。详见[归档特性](../archive/overview.md)。

## 工程链路:设计态与运维

| 消费入口 | 拿什么 | 干什么 |
| --- | --- | --- |
| 表/列/视图设计器(`DevTableController` / `DevTableColumnController` / `DevViewController`) | refreshDBMeta、getTableUpgradeList、getClassSourceEntity | 设计器保存后刷新元数据、生成结构升级清单、双源冲突校验 |
| `MetaDdlService#createOrUpdateTableByEntityMeta` | EntityMeta | 按实体元数据建结构/升结构(DDL) |
| `EntityJavaSourceGenerator` | 实体名/标题/字段/列/主键 | 反向生成带 `@Entity/@Col/@Title/@Id` 的 Java 源码 |
| `PackageService#assertColumnsConsistent` + `PackageUtils.validatePackageData` | getAll() | 打包前校验"物理结构列 ⊆ 实体 FieldMeta 列",不一致禁止打包 |
| `ImportExcelService` | getFieldMeta/ColumnMeta | Excel 导入的列定位、类型转换、值校验、唯一列检测 |
| `AuditBusinessNamer` / `DictDisplayResolver` | entityTitle、FieldMeta.title、dictDataSourceMap | 审计日志把字段值翻译成"实体.字段(字典标签)" |
| `OcrUtils` | EntityMeta 列映射 | OCR 结果按元数据落库 |
| `ConsistencyChecker`(geelato-maintenance) | 三方 EntityMeta | ORM 注解源 / 在线定义源 / 物理库源逐字段 diff 与修复 |
| `MqlTestModelRegistrar` / `MqlExplainService` | MetaManager | 把测试模型注册进注册表,供 MQL explain/试跑 |
| `PlatformMetaMonitorEndPoint` | getAll() | 运维端点输出全部实体元数据快照 |

## 总表:去掉元数据会失去什么

| 若无元数据 | 失去的能力 |
| --- | --- |
| 无结构名/列映射 | ORM 的 insert/update/delete/query 全部无法生成 SQL |
| 无字段校验 | MQL 任何报文都无法判定实体与字段合法性,通用数据接口无入口 |
| 无 connectId/dbType | 多数据源路由与逐实体方言判定失效,动态数据源失去"实体→数据源"依据 |
| 无实体名挂接点 | 行级数据权限、租户过滤无从注入 |
| 无 containsField | 逻辑删除、审计字段填充失去按实体开关的能力 |
| 无 SimpleFieldMeta | 低码前端失去表单渲染契约,通用接口退化为裸 SQL 通道 |
| 无设计器回写+refreshDBMeta | 模型变更须重启生效,设计态与运行态割裂 |
| 无打包结构一致性校验 | 部署期才暴露 unfound FieldMeta,故障后移到最坏时机 |

## 延伸阅读

- [元数据模型](./model.md)——消费方拿到的属性从哪里来
- [元数据加载机制](./loading.md)——消费方查询的注册表如何建立
- [元数据运行期操作](./operations.md)——消费方调用的查询与刷新 API
