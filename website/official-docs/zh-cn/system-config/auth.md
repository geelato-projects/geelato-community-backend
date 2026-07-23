---
title: Auth 模块
sidebar_label: Auth 模块
---
# Auth 模块

对应配置文件：

- `properties/auth.properties`

## 作用

这个文件承载统一认证、微信相关登录以及 `sa-token` 的基础配置。

## 关键配置

### OAuth2

前缀：

- `geelato.oauth2.*`

当前包括：

- `url`
- `clientId`
- `clientSecret`

它们用于：

- 指向统一认证中心地址
- 标识当前客户端身份
- 完成客户端鉴权

### 微信

前缀：

- `geelato.wx.*`

当前包括：

- `url`
- `appId`
- `secret`
- `gzh-appId`
- `gzh-secret`

它们主要服务于：

- 微信开放能力接入
- 公众号或相关微信身份能力接入

### `sa-token`

当前包括：

- `sa-token.token-name`
- `sa-token.is-share`
- `sa-token.is-log`

用于控制：

- token 名称
- 是否共享
- 是否打印日志

## 使用建议

- `clientSecret`、微信密钥等敏感项必须走环境变量
- OAuth2 地址应与统一认证部署地址保持一致
- 如果只是消费统一认证中心 token，还需结合“认证鉴权”章节理解运行时后端如何消费这些凭证

## 推荐继续阅读

- [认证鉴权](../authentication/security-authentication.md)
- [统一认证](../authentication/overview.md)
