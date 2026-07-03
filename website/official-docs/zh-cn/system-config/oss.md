# OSS 模块

对应配置文件：

- `properties/oss.properties`

## 作用

这个文件承载阿里云 OSS 的访问配置，主要用于对象存储接入。

## 关键配置

前缀：

- `geelato.oss.*`

当前包括：

- `accessKeyId`
- `accessKeySecret`
- `endPoint`
- `bucketName`
- `region`

## 使用说明

这组配置决定：

- 上传文件最终落到哪个 OSS 账号和 Bucket
- 访问哪个地域的 OSS 端点
- 后端对象存储能力如何完成鉴权

## 使用建议

- 密钥信息必须通过环境变量注入，不要直接在源码里写死
- `bucketName` 和 `region` 需要与实际 OSS 资源保持一致
- 如果未配置 OSS，宿主工程应确保上传能力有合理的本地或其他存储兜底

## 推荐继续阅读

- [系统配置](overview.md)
