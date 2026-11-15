---
title: 接口与编排授权
sidebar_label: 接口与编排授权
---

# 接口与编排授权

本页覆盖接口服务、自定义编排、导出模板与应用授权关系共 7 个实体。Api 定义应用对外暴露的服务接口；CustomSql 是无代码的接口编排；三个 `App*Map` 关联表把应用与模型、视图、编排授权起来；ExportTemplate 支撑文件导出。

## Api 服务接口

应用对外暴露的服务接口定义：MQL/编排/JS 脚本等形式的接口，含匿名访问、分页、第三方开放等控制。继承 `BaseEntity`，公共字段见[概览](overview.md#公共基类字段)。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 所属应用 |
| name | name | String | 名称 |
| code | code | String | 编码 |
| paging | paging | boolean | 是否分页，默认 false |
| method | method | String | 请求方法 |
| remark | remark | String | 备注 |
| version | version | Integer | 版本 |
| anonymous | anonymous | int | 是否匿名访问：1 允许匿名不鉴权、0 不允许匿名需鉴权，默认 0。有 `enable_status` 字段但未实现 `EntityEnableAble` 接口 |
| groupName | group_name | String | 分组名称 |
| enableStatus | enable_status | int | 是否启用：0 禁用、1 启用，控制接口可访问性，默认 1 |
| sourceContent | source_content | String | GlPage 的组件树字符串 |
| releaseContent | release_content | String | 生成的可执行 JavaScript 脚本 |
| outsideUrl | outside_url | String | 第三方访问地址 |
| outsideStatus | outside_status | int | 第三方访问状态，默认 0 |
| responseFormat | response_format | String | 响应格式 |
| responseType | response_type | String | 响应参数类型 |
| logLevel | log_level | String | 日志级别 |
| requestParams | — | List\<ApiParam\> | `@Transient` 非落库，请求参数树，见 ApiParam |
| responseParams | — | List\<ApiParam\> | `@Transient` 非落库，响应参数树，见 ApiParam |

## ApiParam 接口服务参数

接口的入参/出参定义，按 pid 组成参数树。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 所属应用 |
| apiId | api_id | String | 所属接口 |
| pid | pid | String | 父参数 id，构成参数层级 |
| paramType | param_type | String | 参数类型（请求/响应归类） |
| bodyType | body_type | String | 类型 |
| name | name | String | 名称 |
| dataType | data_type | String | 参数数据类型 |
| defaultValue | default_value | String | 参数默认值 |
| demoValue | demo_value | String | 示例数据 |
| alternateType | alternate_type | String | 交互类型 |
| required | required | boolean | 是否必填，默认 false |
| jacket | jacket | boolean | 源码无注释，默认 false |
| remark | remark | String | 备注 |
| children | — | List\<ApiParam\> | `@Transient` 非落库，子参数树 |

## CustomSql 自定义接口编排

无代码的接口编排定义（`platform_sql`），声明请求/响应参数与编排内容，键名即调用标识。继承 `BaseEntity`。有 `enable_status` 字段但未实现 `EntityEnableAble` 接口。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 所属应用 |
| title | title | String | 标题 |
| keyName | key_name | String | 键名称 |
| requestParams | request_params | String | 请求参数 |
| responseType | response_type | String | 响应参数类型 |
| responseParams | response_params | String | 响应参数 |
| description | description | String | 描述 |
| enableStatus | enable_status | int | 是否启用：0 停用、1 启用 |
| configType | config_type | String | 配置类型 |
| encodingContent | encoding_content | String | 编码内容，保存时若为 base64 data URI 则自动解码（`afterSet`） |

## AppSqlMap 应用接口编排关系表

应用与接口编排（CustomSql）的授权关系。类级标题"应用接口编码关系表"中的"编码"指接口编排（sqlKey/sqlTitle 均指向 CustomSql）。继承 `BaseEntity`。有 `enable_status` 字段但未实现 `EntityEnableAble` 接口。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 申请应用主键 |
| appName | app_name | String | 申请应用名称 |
| sqlId | sql_id | String | 接口编排主键，指向 CustomSql |
| sqlTitle | sql_title | String | 接口编排标题 |
| sqlKey | sql_key | String | 接口编排键名称 |
| sqlAppId | sql_app_id | String | 接口编排所属应用 |
| approvalNeed | approval_need | boolean | 是否需要审批，默认 false |
| approvalStatus | approval_status | String | 审批状态 |
| enableStatus | enable_status | int | 是否启用：0 停用、1 启用 |
| description | description | String | 描述 |

## AppTableMap 应用模型授权关系表

应用与数据模型（设计器表定义）的授权关系。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 ID，`@ForeignKey` 指向 App（platform_app） |
| appName | app_name | String | 应用名称，引用列（isRefColumn，随 appId 取 platform_app.name） |
| tableId | table_id | String | 模型 ID，`@ForeignKey` 指向 TableMeta（platform_dev_table，设计器表定义） |
| tableName | table_name | String | 模型名称 |
| tableTitle | table_title | String | 模型标题 |
| tableAppId | table_app_id | String | 模型所属应用 ID |
| permissionId | permission_id | String | 权限 ID，指向 Permission |
| permissionName | permission_name | String | 权限名称 |
| approvalNeed | approval_need | boolean | 是否需要审批，默认 false |
| approvalStatus | approval_status | String | 审批状态 |
| enableStatus | enable_status | int | 是否启用：0 停用、1 启用 |
| description | description | String | 描述 |

## AppViewMap 应用视图授权关系表

应用与视图的授权关系。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 ID，`@ForeignKey` 指向 App（platform_app） |
| appName | app_name | String | 应用名称，引用列（isRefColumn） |
| tableName | table_name | String | 模型名称（视图所属主体表） |
| viewId | view_id | String | 视图 Id |
| viewName | view_name | String | 视图名称 |
| viewTitle | view_title | String | 视图标题 |
| viewAppId | view_app_id | String | 视图所属应用 |
| permissionId | permission_id | String | 权限 ID |
| permissionName | permission_name | String | 权限名称。源码中该字段的 @Col 名与 @Title 错位复制（标题写成"是否启用"），按实际语义撰写 |
| approvalNeed | approval_need | boolean | 是否需要审批，默认 false |
| approvalStatus | approval_status | String | 审批状态 |
| enableStatus | enable_status | int | 是否启用：0 停用、1 启用 |
| description | description | String | 描述 |

## ExportTemplate 导出文件模板

导出文件模板定义：按文件类型与用途配置模板，最多 19 个模板槽位（`indexTemplate` 按 1~19 取用），并携带业务数据类型、清洗规则、元数据与可视化布局 JSON。继承 `BaseEntity`，实现 `EntityEnableAble`。注意：该实体源码字段级 @Title 大面积复制粘贴为"状态"，下表按字段名与行内注释的实际语义撰写。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 所属应用（源码 @Title 误写"状态"） |
| title | title | String | 模板标题（源码 @Title 误写"状态"） |
| useType | use_type | String | 用途 |
| fileType | file_type | String | 文件类型 |
| fileCode | file_code | String | 文件编码 |
| template | template | String | 模板内容，槽位 1 |
| templateRule | template_rule | String | 模板规则 |
| enableStatus | enable_status | int | 状态：0 停用、1 启用 |
| description | description | String | 描述 |
| businessTypeData | business_type_data | String | 数据类型 |
| businessRuleData | business_rule_data | String | 清洗规则 |
| businessMetaData | business_meta_data | String | 元数据 |
| canvasLayoutData | canvas_layout_data | String | WYSIWYG 可视化布局 JSON |
| template2~template19 | template2~template19 | String | 模板槽位 2~19，经 `indexTemplate(index)` 按索引取用 |

## 推荐继续阅读

- [应用与设计器](app-and-designer.md)——被授权的应用与页面从哪里定义
- [角色与权限](role-permission.md)——Permission 与角色授权链路
- [元数据说明](../metadata/overview.md)——platform_dev_* 表与 TableMeta 的元数据机制
