---
title: 组织、用户与角色定义
sidebar_label: 组织、用户与角色
---

# 组织、用户与角色定义

Geelato 安全协议的核心主数据概念。平台运行时（认证、组织补全、在线态展示、鉴权）只依赖统一抽象读取这些数据，不把"数据从哪里来"硬编码在消费方代码里。

## 核心概念

| 概念 | 说明 |
| --- | --- |
| 用户 User | 登录主体，支持多种扩展身份键（`loginName`、`weixinUnionId`、`weixinWorkUserId` 等），运行时鉴权链路可直接使用扩展身份入口 |
| 组织 Org | 组织单元，一个用户可归属多个组织 |
| 部门 / 业务单元 | 组织下的层级概念，登录后随用户上下文回填 |
| 角色 Role | 权限载体，通过 `getUserRoles(userId)` 查询 |
| 租户 Tenant | 多租户隔离维度，随全局上下文传递 |

## 读取抽象：UserProvider 与 OrgProvider

`geelato-security` 把"用户/组织数据从哪里来"抽象为两个只读接口：

- **`UserProvider`**：用户只读查询能力——`getUser`、`getUserByExtendKey`、`getUserRoles`、`getUserOrgs`、`refresh` 等。
- **`OrgProvider`**：组织只读查询能力——组织树查询、组织补全等。

默认实现读主库快照，可整体替换为外部 IAM、企业微信、组织中心、缓存中台等任意来源，参见[安全 Provider 扩展](../reference/security-provider-extension.md)。

:::info 文档建设状态
本页为骨架页。组织模型的字段级定义、角色与权限的关系模型、数据权限规则等细节将在后续版本补充。
:::

## 延伸阅读

- [安全认证](../authentication/security-authentication.md)——认证鉴权整体流程
- [SecurityContext 生命周期](../runtime/security-context-lifecycle.md)——用户上下文的初始化、传递与销毁
- [安全 Provider 扩展](../reference/security-provider-extension.md)——替换用户/组织数据来源
- [权限接入](../authentication/permission-integration.md)——AuthServer 侧的统一权限管理
