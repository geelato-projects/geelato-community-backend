---
title: API · 平台基础
sidebar_label: 平台基础
---
# 平台基础（base）

15 个 Controller，共 46 个端点。标注 ⚙ 的为设计态接口。

## AuxiliarySuiteHealthController ⚙ — `/system/monitor/auxiliary-suites`

辅助套件（Auxiliary Suite）健康状态查询与手动刷新，仅系统管理员可用。

### health 健康状态查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/system/monitor/auxiliary-suites/health` |
| 参数 | 无 |
| 返回 | `ApiResult<AuxiliarySuiteHealthSummary>`：辅助套件健康汇总（检查时间、轮询间隔秒数、套件总数与健康/异常/未知数量、是否存在故障 `hasFailure`、最近错误、各套件健康快照列表 `suites`）；非管理员返回失败。 |

### refresh 立即刷新健康

| 项 | 内容 |
| --- | --- |
| URL | `GET、POST /api/system/monitor/auxiliary-suites/refresh` |
| 参数 | 无 |
| 返回 | `ApiResult<AuxiliarySuiteHealthSummary>`：立即执行一次健康检查后的最新汇总；非管理员返回失败。 |

## CacheController — `/cache`

J2Cache 缓存的键查询、移除、清空与运行配置查看。

### remove 按键清除缓存

| 项 | 内容 |
| --- | --- |
| URL | `GET、POST /api/cache/remove/{keys}` |
| 参数 | `keys`：path，string，必填，逗号分隔的缓存 key 列表，逐个移除（空值项跳过）；为空时返回失败。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### config 缓存配置查看

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/cache/config` |
| 参数 | 无 |
| 返回 | `ApiResult<Map<String,Object>>`：`l1`/`l2`（是否启用与 provider，`l2.config` 含 redis 模式、主机、命名空间、库编号，密码打码）、`config`（`open_spring_cache`、`clean_on_start`、`broadcast`、`serialization`、`default_cache_expire`）、`capabilities`（`channel_present`、`clear_region_supported`、`evict_supported`）。 |

### listL1 一级缓存列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/cache/list/l1` |
| 参数 | `keyword`：query，string，可选，按缓存 key 模糊过滤（忽略大小写）。 |
| 返回 | `ApiResult<List<Map<String,Object>>>`：一级（L1）缓存条目列表，每项含 `key`、`type`（`L1`）、`region`、`size`（UTF-8 字节数）、`createTime`（恒为 null）、`expireTime`（null）。 |

### listL2 二级缓存列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/cache/list/l2` |
| 参数 | `keyword`：query，string，可选，按缓存 key 模糊过滤（忽略大小写）。 |
| 返回 | `ApiResult<List<Map<String,Object>>>`：二级（L2）缓存条目列表，字段同 `listL1` 但 `type` 为 `L2`；`expireTime` 依据 Redis TTL 推算（仅 redis `single` 模式可取，其余为 null）。 |

### get 读取缓存值

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/cache/get/{key}` |
| 参数 | `key`：path，string，必填，缓存 key；为空时返回失败。 |
| 返回 | `ApiResult<Object>`：`data` 为缓存值（经 `CacheUtil` 反射调用 `get`/`getValue` 获取，取不到时为 null）。 |

### clearAll 清空缓存

| 项 | 内容 |
| --- | --- |
| URL | `GET、POST /api/cache/clear` |
| 参数 | 无 |
| 返回 | `ApiResult<NullResult>`：清空 `default` 区域缓存（`channel.clear`）；通道不可用时回退为逐 key 移除。`data` 为空。 |

## ConfigController — `/config`

平台系统配置查询（按平台/租户/应用分组）与指定配置项刷新。

### list 平台配置查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/config` |
| 参数 | `tenantCode`：query，string，可选，命中租户级配置时输出 `tenant` 分组。<br />`appId`：query，string，可选，命中应用级配置时输出 `app` 分组。 |
| 返回 | `ApiResult<Map<String,Object>>`：配置键值分组：`platform`（全局）、`tenant`（指定租户）、`app`（指定应用），取 `webapp` 与 `all` 两个用途的配置合并。 |

### refresh 刷新配置项

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/config/refresh/{configKey}` |
| 参数 | `configKey`：path，string，必填，配置键。 |
| 返回 | `ApiResult<NullResult>`：通过 `EnvManager` 刷新指定配置，`data` 为空；异常时返回失败消息。 |

## DictionaryController — `/system/dictionary`

按字典编码查询启用状态的字典项。

### getByCode 按编码查字典项

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/system/dictionary/{code}` |
| 参数 | `code`：path，string，字典编码；为空时返回空列表。 |
| 返回 | `ApiResult<List<DictItem>>`：启用且未删除的字典项列表，按 `seqNo` 升序；字典不存在时返回空列表。 |

## DownloadController — `/resources`

附件文件下载与站点配置文件读取。

### downloadFile 下载附件文件

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/resources/file` |
| 参数 | `id`：query，string，附件 ID。<br />`isPdf`：query，boolean，是否转换为 PDF 后下载（需宿主提供 aspose 依赖）。<br />`isPreview`：query，boolean，是否预览模式。<br />`isThumbnail`：query，boolean，是否下载缩略图。<br />`name`、`path`：query，string，方法签名中声明，当前处理逻辑未使用。 |
| 返回 | 直接写响应流（无 JSON body）：按附件 ID 取附件（OSS 或本地路径）并以附件原文件名输出；`isPdf=true` 时转 PDF 并以 PDF 文件名输出；响应带 `Cache-Control`、`ETag`、`Last-Modified` 头。 |

### downloadJson 读取站点配置

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/resources/json` |
| 参数 | `fileName`：query，string，必填，配置文件名；无扩展名时自动补 `.config`。 |
| 返回 | `ApiResult<String>`：`data` 为站点配置文件内容（UTF-8 全文）；文件不存在时返回失败。 |

## IDGenerateController — `/id`

生成全局唯一 ID。

### generate 生成唯一ID

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/id/generate` |
| 参数 | 无 |
| 返回 | `ApiResult<Long>`：`data` 为 `UIDGenerator.generate()` 生成的唯一 ID（long）。 |

## OrganizationController — `/org`

按组织 ID 查询组织机构记录。

### getOrgById 按ID查组织

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/org/{orgId}` |
| 参数 | `orgId`：path，string，组织 ID。 |
| 返回 | 非 `ApiResult` 包装，直接返回 JSON 对象 `Map<String,Object>`：成功 `{success: true, data: <组织记录>}`（`platform_org` 表整行），失败 `{success: false, message: <原因>}`。 |

## PageCheckController — `/page/check`

页面签入/签出与页面接管申请（check request）流转。

### checkPage 页面签入签出

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/page/check` |
| 参数 | `pageId`：body，string，必填，页面 ID。<br />`action`：body，string，必填，`checkout` 签出 / `checkin` 签入。<br />`force`：body，boolean，可选，`checkin` 且页面被他人占用时是否强行签入（要求应用管理员权限）。 |
| 返回 | `ApiResult<Map<String,Object>>`：`data` 含 `checkStatus`、`checkUserId`、`checkUserName`、`checkAt`；按签出/签入各分支返回相应成功或失败消息。 |

### getCheckedOutPageList 已签出页面列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page/check/list` |
| 参数 | `appId`：query，string，必填，应用 ID。 |
| 返回 | `ApiResult<List<Map<String,Object>>>`：该应用下已签出且未删除的页面列表，每项含 `pageId`、`extendId`、`title`、`checkUserId`、`checkUserName`、`checkAt`、`lastUpdateTime`。 |

### syncPageStatus 同步页面状态

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page/check/sync` |
| 参数 | `pageId`：query，string，必填，页面 ID。 |
| 返回 | `ApiResult<Map<String,Object>>`：页面签出状态：`checkStatus`、`checkUserId`、`checkUserName`、`checkAt`、`version`、`lastUpdateTime`。 |

### requestPageCheckOut 申请接管页面

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/page/check/request` |
| 参数 | `pageId`：body，string，必填，页面 ID。<br />`targetUser`：body，string，必填，目标用户 ID（接管对象）。 |
| 返回 | `ApiResult<Map<String,Object>>`：`data` 含 `requestId`；该页面已有待处理（pending）申请时返回失败。 |

### getCheckRequestList 接管申请列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page/check/request/list` |
| 参数 | `userId`：query，string，必填，目标用户 ID。<br />`status`：query，string，可选，按申请状态过滤。 |
| 返回 | `ApiResult<List<AppPageCheckReq>>`：发往该用户的接管申请列表。 |

### processCheckRequest 处理接管申请

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/page/check/request/process` |
| 参数 | `requestId`：body，string，必填，申请 ID。<br />`action`：body，string，必填，`approve` 批准 / `reject` 拒绝。<br />`comment`：body，string，可选，处理意见。 |
| 返回 | `ApiResult<Map<String,Object>>`：`data` 含 `pageId`、`checkStatus`；`approve` 时页面被签入（记录处理人为签入用户），仅 pending 状态的申请可处理。 |

### cancelCheckRequest 取消接管申请

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/page/check/request/cancel` |
| 参数 | `requestId`：body，string，必填，申请 ID。 |
| 返回 | `ApiResult<String>`：仅发起人可取消 pending 申请，状态置为 `rejected` 并记录处理信息；`data` 为成功提示文本。 |

## PageController — `/page_v1`

页面配置接口 V1（原始实现，作为 V2 的回退参照）：运行时/设计时页面定义获取、多语言包、保存与更新通知。

### getPageAndCustom 页面定义与自定义

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page_v1/getPageAndCustom/{idType}/{id}`、`GET /api/page_v1/getPageAndCustom/{idType}/{id}/*` |
| 参数 | `idType`：path，string，`pageId` 或 `extendId`。<br />`id`：path，string，对应的 ID 值。 |
| 返回 | `ApiResult<HashMap<String,Object>>`：运行时页面数据：`id`、`type`、`appId`、`code`、`releaseContent`（JSON 字符串）、`sourceContent`（空串，不含源码）、`pageLang`（当前语言语言包）、`pageCustom`（当前用户对该页面的自定义配置）、`pagePerms`（当前用户对该页面的操作权限）。 |

### getPageById 按ID查页面配置

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page_v1/getPageById/{pageId}` |
| 参数 | `pageId`：path，string，页面 ID。 |
| 返回 | `ApiResult<HashMap<String,Object>>`：设计时完整配置：同 `getPageAndCustom` 字段，但 `sourceContent` 为页面源码，`pageCustom`、`pagePerms` 返回空串。 |

### getPageByExtendId 按扩展ID查页面

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page_v1/getPageByExtendId/{extendId}` |
| 参数 | `extendId`：path，string，页面树节点扩展 ID。 |
| 返回 | 同 `getPageById`，按扩展 ID 查询。 |

### getPageLang 页面多语言

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page_v1/getPageLang/{idType}/{id}`、`GET /api/page_v1/getPageLang/{idType}/{id}/*` |
| 参数 | `idType`：path，string，`pageId` 或 `extendId`。<br />`id`：path，string，对应的 ID 值。 |
| 返回 | `ApiResult<HashMap<String,Object>>`：`pageLang`（当前语言的语言包内容）、`locale`（当前语言）；用于页面已加载后仅切换语言的场景。 |

### notifyUpdate 页面更新通知

| 项 | 内容 |
| --- | --- |
| URL | `GET、POST /api/page_v1/notifyUpdate/{pageId}/{extendId}` |
| 参数 | `pageId`：path，string，页面 ID。<br />`extendId`：path，string，页面扩展 ID。 |
| 返回 | `ApiResult<String>`：发布页面升级事件通知所有客户端更新页面配置，`data` 为成功提示文本。 |

### savePage 保存页面配置

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/page_v1/savePage` |
| 参数 | `appPage`：body，`AppPage` 实体（未标注 `@RequestBody`，由 Spring 按查询/表单参数绑定），常用字段如 `id`、`appId`、`code`、`title`、`extendId`、`description`、`sourceContent`；`id` 非空为更新且版本号自增，为空为新建（版本号 1）。 |
| 返回 | `ApiResult<AppPage>`：保存后的页面（版本号已更新）；同时写页面日志、清除页面缓存并发布页面更新事件。 |

## PageV2Controller — `/page`

页面配置接口 V2（性能优化版，路径与 V1 完全一致）：运行时 pageLang/pageCustom/pagePerms 三段并行查询，运行时 `releaseContent` 以原生 JSON 对象返回。

### getPageAndCustom 页面定义与自定义

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page/getPageAndCustom/{idType}/{id}`、`GET /api/page/getPageAndCustom/{idType}/{id}/*` |
| 参数 | `idType`：path，string，`pageId` 或 `extendId`。<br />`id`：path，string，对应的 ID 值。 |
| 返回 | `ApiResult<HashMap<String,Object>>`：字段同 V1 `getPageAndCustom`，但 `releaseContent` 被解析为原生 JSON 对象返回（解析失败回退为原字符串）；三段查询并行执行。 |

### getPageById 按ID查页面配置

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page/getPageById/{pageId}` |
| 参数 | `pageId`：path，string，页面 ID。 |
| 返回 | `ApiResult<HashMap<String,Object>>`：设计时完整配置（串行逻辑与 V1 相同）：`sourceContent` 为页面源码，`pageCustom`、`pagePerms` 为空串，`releaseContent` 保持 JSON 字符串。 |

### getPageByExtendId 按扩展ID查页面

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page/getPageByExtendId/{extendId}` |
| 参数 | `extendId`：path，string，页面树节点扩展 ID。 |
| 返回 | 同 `getPageById`，按扩展 ID 查询。 |

### getPageLang 页面多语言

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/page/getPageLang/{idType}/{id}`、`GET /api/page/getPageLang/{idType}/{id}/*` |
| 参数 | `idType`：path，string，`pageId` 或 `extendId`。<br />`id`：path，string，对应的 ID 值。 |
| 返回 | `ApiResult<HashMap<String,Object>>`：`pageLang`（当前语言的语言包内容）、`locale`（当前语言）。 |

### notifyUpdate 页面更新通知

| 项 | 内容 |
| --- | --- |
| URL | `GET、POST /api/page/notifyUpdate/{pageId}/{extendId}` |
| 参数 | `pageId`：path，string，页面 ID。<br />`extendId`：path，string，页面扩展 ID。 |
| 返回 | `ApiResult<String>`：发布页面升级事件，`data` 为成功提示文本。 |

### savePage 保存页面配置

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/page/savePage` |
| 参数 | `appPage`：body，`AppPage` 实体（未标注 `@RequestBody`，由 Spring 按查询/表单参数绑定），常用字段如 `id`、`appId`、`code`、`title`、`extendId`、`description`、`sourceContent`；`id` 非空为更新且版本号自增，为空为新建（版本号 1）。 |
| 返回 | `ApiResult<AppPage>`：保存后的页面（版本号已更新）；同时写页面日志、清除页面缓存并发布页面更新事件。 |

## ScheduledTaskMonitorController ⚙ — `/system/monitor/scheduled-tasks`

平台定时任务监控汇总查询与手动扫描，仅系统管理员可用。

### list 任务监控汇总

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/system/monitor/scheduled-tasks` |
| 参数 | 无 |
| 返回 | `ApiResult<ScheduledTaskMonitorSummary>`：定时任务监控汇总（扫描时间、任务总数与 active/inactive/running/success/failed/neverRun 数量、最近错误、任务快照列表 `tasks`）；非管理员返回失败。 |

### refresh 立即扫描任务

| 项 | 内容 |
| --- | --- |
| URL | `GET、POST /api/system/monitor/scheduled-tasks/refresh` |
| 参数 | 无 |
| 返回 | `ApiResult<ScheduledTaskMonitorSummary>`：立即扫描一次后的最新汇总；非管理员返回失败。 |

## SqlKeyController — `/sk`

按命名 SQL key 执行 SQL，支持指定数据源连接。

### exec 执行命名SQL

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/sk/{key}` |
| 参数 | `key`：path，string，必填，命名 SQL key。<br />`connectId`：query，string，可选，数据源连接 ID，提供时切换到对应数据源执行。<br />`paramMap`：body，`Map<String,Object>`，SQL 参数（供命名 SQL 模板取值）。 |
| 返回 | `ApiResult<Object>`：由该 SQL 配置的响应类型决定：`map`/`object` 返回单行 `Map`，`list`/`array` 返回 `List`，其余仅执行语句且 `data` 为 null。 |

## StateWorkFlowController — `/stateWorkFlow`

状态工作流定义更新通知。

### notifyUpdate 工作流更新通知

| 项 | 内容 |
| --- | --- |
| URL | `GET、POST /api/stateWorkFlow/notifyUpdate/{procDefId}` |
| 参数 | `procDefId`：path，string，工作流定义（扩展）ID。 |
| 返回 | `ApiResult<String>`：发布状态工作流升级事件通知所有客户端更新，`data` 为成功提示文本。 |

## SystemInitController — `/system/init`

平台初始化自检：配置项、数据源与 SQL 脚本盘点。

### properties 配置项盘点

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/system/init/properties` |
| 参数 | 无 |
| 返回 | `ApiResult<Map<String,Map<String,String>>>`：按配置文件来源分组的配置键值（auth、market、message、oss、package、sc、seata、weixin_work、workflow、default）；含 password/secret 的键值打码，未解析的值显示"未配置"。 |

### datasource 数据源盘点

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/system/init/datasource` |
| 参数 | 无 |
| 返回 | `ApiResult<List<Map<String,String>>>`：Spring 管理的数据源与动态注册数据源合并后的信息列表（`key`、`class`、`jdbcUrl`、`username`、`password` 打码，以及配置表中的 `dbType`、`host`、`port`、`dbName` 等）。 |

### validate 数据源连通校验

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/system/init/datasource/validate` |
| 参数 | `key`：query，string，可选，数据源 key，缺省 `primary`。 |
| 返回 | `ApiResult<Map<String,String>>`：连接校验结果：`key`、`connected`、`jdbcUrl`、`username`、`product`、`driver`、`durationMs`（毫秒）；未找到数据源时返回失败。 |

### sql SQL脚本盘点

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/system/init/sql` |
| 参数 | 无 |
| 返回 | `ApiResult<Map<String,Object>>`：`db`（数据库内 SQL 脚本列表，每项含 `sqlKey`、`sql`、`type=db`）与 `file`（文件 SQL 脚本列表，每项含 `sqlKey`、`sql`、`type=file`）。 |

## UploadController — `/upload`

文件与配置上传：附件上传、配置对象/JSON 落盘、实体记录导出为配置文件。

### uploadFile 上传附件文件

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/upload/file` |
| 参数 | `file`：form-data 文件字段，必填，上传文件。<br />`serviceType`：query/form-data，string，文件服务器类型。<br />`tableType`：query/form-data，string，表类型（决定默认保存路径与附件来源）。<br />`root`：query/form-data，string，可选，自定义文件根目录，提供时按其生成保存路径。<br />`isRename`：query/form-data，boolean，可选，是否重命名，默认 true。<br />`objectId`：query/form-data，string，可选，业务对象 ID。<br />`formIds`：query/form-data，string，可选，关联表单 ID。<br />`genre`：query/form-data，string，可选，文件类型。<br />`batchNo`：query/form-data，string，可选，批次号。<br />`invalidTime`：query/form-data，string，可选，失效时间（日期时间格式）。<br />`validDuration`：query/form-data，integer，可选，有效时长（秒），设置后覆盖 `invalidTime` 计算失效时间。<br />`isThumbnail`：query/form-data，boolean，可选，是否生成缩略图。<br />`onlyThumb`：query/form-data，boolean，可选，仅生成缩略图。<br />`dimension`：query/form-data，string，可选，缩略图尺寸。<br />`thumbScale`：query/form-data，string，可选，缩略图比例。<br />`appId`：query/form-data，string，可选，应用 ID，缺省取当前应用。<br />`tenantCode`：query/form-data，string，可选，租户编码，缺省取当前租户。 |
| 返回 | `ApiResult<Attachment>`：上传后的附件信息（`source` 置为 `tableType`，`storageType` 置为 `serviceType`）。 |

### uploadObject 上传配置对象

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/upload/object` |
| 参数 | `params`：body，`Map<String,Object>`，必填，写入文件的配置内容（Java 序列化对象）。<br />`fileName`：query/form-data，string，必填，文件名，无 `.config` 后缀时自动补。<br />`catalog`：query/form-data，string，可选，配置根目录下的子目录。 |
| 返回 | `ApiResult<String>`：`data` 为落盘文件名；同名文件已存在时先备份改名，备份失败则覆盖原文件。 |

### uploadJson 上传JSON配置

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/upload/json` |
| 参数 | `JsonData`：body，string，必填，JSON 文本（原始请求体）。<br />`fileName`：query/form-data，string，必填，文件名，无 `.config` 后缀时自动补。<br />`catalog`：query/form-data，string，可选，配置根目录下的子目录。 |
| 返回 | `ApiResult<String>`：`data` 为落盘文件名；同名文件处理方式同 `uploadObject`。 |

### uploadModel 导出模型记录

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/upload/model/{entityName}/{id}` |
| 参数 | `entityName`：path，string，实体（表）名。<br />`id`：path，string，记录 ID。<br />`fileName`：query/form-data，string，必填，导出配置文件名，可逗号分隔多个。 |
| 返回 | `ApiResult<NullResult>`：查询该记录字段值（排除实体默认列与 `unSaveConfig` 配置的字段），逐个写为 `.config` 文件；`data` 为空。 |
