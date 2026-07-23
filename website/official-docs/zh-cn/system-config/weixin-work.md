---
title: 企业微信模块
sidebar_label: 企业微信模块
---
# 企业微信模块

对应配置文件：

- `properties/weixin_work.properties`

## 作用

这个文件承载企业微信服务端验签相关参数。

## 关键配置

- `wework.validate.token`
- `wework.validate.aeskey`
- `wework.validate.corpid`

## 配置含义

### Token

- `wework.validate.token`

用于企业微信回调校验时的 token。

### AES Key

- `wework.validate.aeskey`

用于消息体加解密。

### CorpId

- `wework.validate.corpid`

用于校验当前企业微信企业身份。

## 使用建议

- 这些参数都属于敏感配置，应优先通过环境变量覆盖
- 必须与企业微信后台配置保持一致，否则会出现验签失败或消息解密失败
- 如同时使用微信与企业微信能力，应注意和 `auth.properties` 中的普通微信配置区分

## 推荐继续阅读

- [Auth 模块](auth.md)
