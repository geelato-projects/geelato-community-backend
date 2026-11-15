---
title: 应用与设计器
sidebar_label: 应用与设计器
---

# 应用与设计器

本页覆盖应用定义、版本管理、页面协作与多语言、菜单导航共 8 个实体。应用（App）是低代码平台的聚合根，页面（AppPage）是设计器的核心产出；页面通过签入签出机制多人协作；多语言实体承载应用与页面的国际化文案；TreeNode 与 MenuItem 承载导航。

## App 应用

低代码应用定义，平台的聚合根：前端运行时按应用加载页面、菜单与主题。继承 `BaseSortableEntity`，公共字段见[概览](overview.md#公共基类字段)。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| name | name | String | 应用名称 |
| code | code | String | 编码 |
| type | type | String | 应用分类 |
| icon | icon | String | 图标 |
| appKey | app_key | String | 应用秘钥 |
| token | token | String | 应用 Token |
| tree | tree | String | 文件树，JSON 字符串（如 jsTree） |
| logo | logo | String | Logo |
| theme | theme | String | 应用主题 |
| watermark | watermark | int | 应用水印，是否启用水印，默认不启用 |
| href | href | String | 首页链接，加载模块之后打开的首页面 |
| dependAppCode | depend_app_code | String | 依赖的应用模块编码，可多个，格式如 `dev,sys` |
| powerInfo | power_info | String | 权限信息，页面底部的网站说明 |
| versionInfo | version_info | String | 版本信息 |
| description | description | String | 描述 |
| applyStatus | apply_status | int | 应用站点状态：1 启用、0 禁用 |
| designStatus | design_status | int | 设计站点状态：1 启用、0 禁用 |
| appStorage | app_storage | String | 应用仓库地址 |
| purpose | purpose | String | 应用范围 |
| ideVersion | ide_version | String | IDE 版本 |
| modelColumnPcl | model_column_pcl | String | 模型字段权限校验级别：strict 严格、loose 宽松 |
| roles | — | String | `@Transient` 非落库，查询辅助字段 |

## AppVersion 应用版本

应用版本管理，记录每次打包产物的地址与状态。继承 `BaseEntity`。与应用打包流程的关系见[应用打包](../packaging/overview.md)。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| packagePath | package_path | String | 包地址 |
| version | version | String | 版本名称 |
| packageSource | package_source | String | 包来源 |
| appId | app_id | String | 所属应用 |
| packetTime | packet_time | Date | 打包时间 |
| status | status | String | 状态 |
| description | description | String | 描述 |

## AppPage 页面

页面设计器产出的页面定义，源码、预览、发布三态内容分列存储；签入签出字段支撑多人协作。继承 `BaseSortableEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| extendId | extend_id | String | 扩展信息，扩展 id，如对应的叶子节点 id |
| appId | app_id | String | 应用 Id，所属应用 |
| type | type | String | 类型，如 `api`/`form`/`table` |
| title | title | String | 标题 |
| code | code | String | 编码 |
| sourceContent | source_content | String | 源文件内容，longText，非空 |
| previewContent | preview_content | String | 预览的内容，longText，非空 |
| releaseContent | release_content | String | 发布的内容，longText，非空 |
| description | description | String | 描述 |
| version | version | int | 版本 |
| checkUserId | check_user_id | String | 签入用户 ID |
| checkUserName | check_user_name | String | 签入用户名 |
| checkStatus | check_status | String | 签入状态：`unchecked`（未签出）、`checkedOut`（已签出），对应 `CheckStatusEnum` |
| checkAt | check_at | Date | 签入时间 |

## AppPageCheckReq 页面签出申请接管请求

页面签出申请/接管请求实体：页面被他人签出时，通过本实体发起接管申请并跟踪审批。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| pageId | page_id | String | 页面 ID，非空 |
| pageTitle | page_title | String | 页面标题，非空 |
| requesterId | requester_id | String | 发起用户 ID，非空 |
| requesterName | requester_name | String | 发起用户名称，非空 |
| targetUserId | target_user_id | String | 目标用户 ID（当前签出人），非空 |
| targetUserName | target_user_name | String | 目标用户名称，非空 |
| requestTime | request_time | Date | 请求时间，非空 |
| status | status | String | 请求状态：`pending`/`approved`/`rejected`/`expired`，非空 |
| processTime | process_time | Date | 处理时间 |
| processUserId | process_user_id | String | 处理用户 ID |
| processUserName | process_user_name | String | 处理用户名称 |
| processComment | process_comment | String | 处理备注，varchar(500) |

## AppMultiLang 应用多语言

应用级多语言文案：一种语言一行语言包。继承 `BaseEntity`。有 `enable_status` 字段（未实现 `EntityEnableAble` 接口）。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 ID |
| langType | lang_type | String | 语言类型 |
| langPackage | lang_package | String | 语言包 |
| purpose | purpose | String | 语言范围 |
| enableStatus | enable_status | int | 启用状态：1 启用、0 未启用 |

## AppPageLang 页面多语言

页面级多语言文案，按页面版本存储。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| pageId | page_id | String | 所属页面 |
| version | version | int | 版本 |
| langType | lang_type | String | 语言类型 |
| content | content | String | 多语言内容 |
| extendId | extend_id | String | 菜单树节点 ID |
| appId | app_id | String | 所属应用 |

## TreeNode 菜单

通用树节点实体，类级标题"菜单"：既承载运行时菜单树，也承载设计器文件树等任意业务树（由 treeEntity/treeId 挂接归属）。继承 `BaseSortableEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| treeEntity | tree_entity | String | 树实体：节点所属树对应的业务实体，如项目文件树对应 `platform_project` |
| treeId | tree_id | String | 树 Id：树对应业务实体某条记录的 id，如项目 id，非空 |
| pid | pid | String | 父节点 Id，字符串格式，解决数字类型太大在 Web 端展示失真 |
| type | type | String | 节点类型 |
| icon | icon | String | 节点图标 |
| text | text | String | 节点标题 |
| iconType | icon_type | String | 节点图标类型 |
| extendEntity | extend_entity | String | 扩展实体，如叶子节点对应的文件表名、业务表名 |
| extendId | extend_id | String | 扩展实体 ID，如叶子节点对应的文件 id、表单 id |
| meta | meta | String | 节点扩展元信息，JSON 格式字符串 |
| flag | flag | String | 标志旗，对节点做特别标识，如该节点作为菜单项 |
| url | url | String | 链接，通过菜单项打开第三方链接 |
| description | description | String | 描述 |

## MenuItem 菜单项

菜单项与菜单子项定义，可链接设计器页面或树节点。继承 `BaseSortableEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| title | title | String | 标题 |
| clazz | clazz | String | 样式类 |
| active | active | String | 激活 |
| linkType | link_type | String | 链接类型：`dynamicPage`（设计器配置的页面）或 `other`，长度 20 |
| href | href | String | 链接 |
| pageCode | page_code | String | 链接页面，链接打开的页面编码 |
| treeNodeId | tree_node_id | String | 树节点 id，关联 TreeNode |

## 推荐继续阅读

- [接口与编排授权](api-orchestration.md)——应用的能力出口：接口服务与自定义编排
- [角色与权限](role-permission.md)——应用与角色、菜单与角色的授权关系
- [应用打包](../packaging/overview.md)——AppVersion 的包从哪里来
