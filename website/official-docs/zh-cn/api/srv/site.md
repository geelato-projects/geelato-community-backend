---
title: API · 静态站点
sidebar_label: 静态站点
---
# 静态站点（site）

1 个 Controller，共 13 个端点。本域接口均为设计态接口。

## StaticSiteController ⚙ — `/site/statics`

静态站点管理与站点目录文件操作：站点的新建/更新/删除与查询、编码唯一性校验、目录树节点、文件列表、文件与目录的增删改名、上传与下载。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/site/statics/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md)。 |
| 返回 | `ApiPagedResult`：data 为静态站点分页数据（外层含分页信息）。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/site/statics/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md)。 |
| 返回 | `ApiResult<List<StaticSite>>`：data 为符合条件的静态站点列表。 |

### get 站点详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/site/statics/get/{id}` |
| 参数 | `id`：path，string，站点 ID。 |
| 返回 | `ApiResult<StaticSite>`：data 为静态站点记录。 |

### createOrUpdate 新建或更新站点

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/site/statics/createOrUpdate` |
| 参数 | body：`StaticSite` 结构；`id` 为空时新建，否则更新，并按配置的站点根目录同步创建站点文件夹。 |
| 返回 | `ApiResult<StaticSite>`：data 为新建/更新后的站点记录。 |

### isDelete 删除站点

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/site/statics/isDelete/{id}` |
| 参数 | `id`：path，string，站点 ID。 |
| 返回 | `ApiResult<NullResult>`：删除站点目录并标记删除记录，`data` 为空；站点不存在时返回失败。 |

### validate 校验编码唯一

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/site/statics/validate` |
| 参数 | body：`StaticSite` 结构，取 `id`、`code`、`appId`、`tenantCode` 参与校验。 |
| 返回 | `ApiResult<Boolean>`：data 为 `code` 在同应用、同租户且未删除的站点中是否唯一（排除自身 `id`）。 |

### treeNode 目录树节点

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/site/statics/treeNode` |
| 参数 | `path`：body，string，可选，目录路径；为空时按站点生成根节点。<br />`appId`：body，string，可选，应用 ID，缺省取请求头。<br />`tenantCode`：body，string，可选，租户编码，缺省取请求头。 |
| 返回 | `ApiResult<Set<TreeNodeData>>`：`path` 为空时 data 为该应用、租户下的站点根节点列表（`appId` 与 `tenantCode` 均为空时报错）；否则 data 为 `path` 目录下的文件夹树节点列表。 |

### queryFile 目录文件列表

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/site/statics/queryFile` |
| 参数 | `path`：body，string，必填，目录路径。<br />`type`：body，string，可选，文件类型过滤，默认 `all`。 |
| 返回 | `ApiResult<Set<FileInfo>>`：data 为 `path` 目录下的文件列表（已排序）；`path` 为空时返回失败。 |

### batchDelete 批量删除文件目录

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/site/statics/batchDelete` |
| 参数 | `path`：body，string，必填，待删除路径，逗号分隔（自动去重）。 |
| 返回 | `ApiResult<NullResult>`：逐个删除对应文件或目录，`data` 为空；`path` 为空时返回失败。 |

### download 下载文件

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/site/statics/download` |
| 参数 | `path`：body，string，必填，文件路径（目录路径报错）。<br />`isPreview`：body，boolean，可选，是否预览模式，默认 false。 |
| 返回 | 直接写响应流（无 JSON body）：输出文件下载/预览流（设置缓存头，内容类型与是否内联由下载服务按文件类型处理）。 |

### upload 上传文件

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/site/statics/upload` |
| 参数 | `file`：form-data，文件，必填，上传的文件。<br />`path`：query，string，必填，目标根目录（不存在时返回失败）。<br />`isCompress`：query，boolean，可选，是否解压压缩包（zip/tar/rar/7z 等，false 或非压缩格式时按普通文件保存）。<br />`isByStep`：query，boolean，可选，是否分步解压。<br />`isExist`：query，int，可选，同名文件处理方式：1 覆盖、2 自动重命名、其他值报错。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### updateName 重命名文件目录

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/site/statics/updateName` |
| 参数 | `path`：body，string，必填，原路径。<br />`name`：body，string，必填，新名称（不能含特殊字符）。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；参数为空或名称含特殊字符时返回失败。 |

### createFolder 新建目录

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/site/statics/createFolder` |
| 参数 | `path`：body，string，必填，父目录路径。<br />`name`：body，string，必填，目录名称（不能含特殊字符）。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；参数为空或名称含特殊字符时返回失败。 |
