---
title: 文件下载
sidebar_label: 文件下载
---

# 文件下载

本页说明文件下载与预览能力的入口、运行方式，以及本地文件与 OSS 文件在下载时的差异，涉及的核心组件包括 `DownloadController`、`FileHandler`、`BaseHandler`、`AttachController`。

## 下载入口

当前最直接的下载入口是：

- `GET /api/resources/file`

常用参数包括：

- `id`
- `isPdf`
- `isPreview`
- `isThumbnail`

其中：

- `id`
  - 附件 ID
- `isPdf`
  - 是否先转 PDF 再输出
- `isPreview`
  - 是否以内联预览方式返回
- `isThumbnail`
  - 是否优先下载缩略图

控制器本身很薄，核心逻辑都交给：

- `FileHandler.download(...)`

## 下载时如何找到文件

下载不是直接根据路径拼接磁盘文件，而是先按附件 ID 查询附件元数据。

流程大致是：

1. 根据附件 ID 查询 `Attachment`
2. 判断当前附件是否要切换到缩略图记录
3. 判断该附件属于本地存储还是 OSS 存储
4. 读取输入流或本地文件
5. 按预览或下载方式回写响应

### 缩略图下载

如果：

- `isThumbnail=true`

系统会优先查找该附件的子缩略图，并选择较小分辨率记录作为输出目标。

因此：

- 图片详情页
- 缩略列表页

可以共用同一套附件下载接口。

## 本地文件如何下载

当附件记录中：

- `objectId` 为空

系统会认为这是本地文件，然后直接使用：

- `Attachment.path`

对应的本地路径读取文件。

这时的下载链路是：

1. 把 `path` 转成本地 `File`
2. 必要时先做 PDF 转换
3. 通过 `DownloadService` 输出到响应流

## OSS 文件如何下载

当附件记录中：

- `objectId` 非空

系统会认为附件位于 OSS。

这时不会直接访问本地磁盘，而是：

1. 用 `Attachment.path` 作为 OSS 对象名
2. 调用 `FileHelper.getFile(...)`
3. 获取 OSS 输入流
4. 再走统一下载输出逻辑

所以对于调用方来说：

- 下载接口保持不变

但底层读取来源会自动切换。

## 预览与下载的区别

下载链路里有一个很重要的参数：

- `isPreview`

它控制当前输出更偏向：

- 浏览器直接预览
- 浏览器触发下载

底层实际输出由：

- `DownloadService.downloadFile(...)`

负责处理响应头和内容流。

## PDF 预览与格式转换

下载接口支持：

- `isPdf=true`

这意味着：

1. 先把原始文件转换成 PDF
2. 再把 PDF 文件输出给浏览器

当前 PDF 转换能力由：

- `BaseHandler.toPdf(...)`

负责，转换后的 PDF 临时文件会落到：

- `geelato.upload.convert-directory`

对应的转换目录下。

这套机制适合：

- Office 文档在线预览
- Excel 转 PDF
- 统一浏览器端展示格式

此外，附件管理里还提供了：

- `GET /api/attach/toPdf/{id}`

它会把 Excel 附件转换成新的 PDF 附件记录，并返回新附件 ID。

## 下载时的缓存控制

当前下载逻辑会在响应头里设置：

- `Cache-Control`
- `ETag`
- `Last-Modified`

其中：

- `ETag` 使用附件 ID
- `Last-Modified` 对本地文件取最后修改时间

这有助于浏览器和中间层做基础缓存控制。

## 配置文件下载

除标准附件外，还提供：

- `GET /api/resources/json`

用于读取：

- `geelato.upload.config-directory`

目录下的 `.config` 文件内容。

这通常用于：

- 站点配置读取
- 设计时 JSON 配置读取
- 运行时读取配置对象文件

## 附件管理相关接口

`AttachController` 除了元数据查询，也提供了多种和下载前后相关的辅助能力：

- `GET /api/attach/get/{id}`
- `POST /api/attach/image/{id}`
- `POST /api/attach/valid`
- `POST /api/attach/storage/{type}`
- `DELETE /api/attach/remove/{id}`

其中：

- `valid`
  - 用于校验附件物理文件是否仍可读取
- `storage/{type}`
  - 用于在本地与 OSS 之间迁移存储
- `remove/{id}`
  - 支持删除附件记录，并可选联动删除 OSS 文件

## 删除时的行为

当前删除能力支持：

- 只删除附件记录
- 删除附件记录时联动删除 OSS 文件

当传入：

- `deleteOss=true`

并且该附件本身位于 OSS 时，系统会调用：

- `FileHelper.removeFile(...)`

尝试把对象存储中的文件一起删除。

## 使用建议

- 业务前端下载统一使用附件 ID，不要直接暴露磁盘路径或 OSS 对象名
- 图片场景优先使用 `isThumbnail` 降低带宽消耗
- 文档预览场景优先考虑 `isPreview + isPdf`
- 如果附件会跨存储迁移，业务侧不要把 `path` 当成稳定外链
- 如果要做严谨的文件生命周期治理，删除记录时应明确是否同步删除 OSS 对象

## 推荐继续阅读

- [文件上传](upload.md)
- [OSS 模块](../system-config/oss.md)
