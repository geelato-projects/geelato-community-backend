# 统一认证中心架构设计

这一页描述统一认证中心的整体架构，而不是单个接入应用的局部接入说明。

这里的统一认证中心，指以下整体能力的组合：

- `geelato-auth-server`：统一认证后端与统一 token 签发中心
- `gl-admin-sso`：面向平台内部站点的统一认证门面
- `gl-lite-sso`：面向第三方应用的轻量嵌入认证门面
- `geelato-web-quickstart`：平台后端宿主，可内嵌统一认证能力
- 平台站点：如 `gl-admin-arco-rt-std`、`gl-admin-arco-dt-std`
- 第三方应用：如 `freight-portal`

本文档回答四个问题：

- 统一认证中心的整体边界是什么
- 平台内部应用与第三方应用分别如何接入
- 统一 token 在系统之间如何流转
- 后续如何同时支持合并部署和独立部署

## 总体设计结论

### 核心定位

- `auth-server` 是统一认证中心的后端核心
- `auth-server` 是全体通用 token 的唯一签发方
- `gl-admin-sso` 是面向平台站点的认证前端门面
- `gl-lite-sso` 是面向第三方应用的轻量认证前端门面
- 平台应用和第三方应用都不再各自维护独立的主 token 体系

### 两类接入对象

统一认证中心服务两类应用：

- 平台内部应用：如 `rt`、`dt`
- 第三方应用：如 `freight-portal`

两者的区别不在于认证中心不同，而在于前端接入门面不同：

- 平台应用优先接 `gl-admin-sso`
- 第三方应用优先接 `gl-lite-sso`

两类应用最终都回到同一个 `auth-server` 认证与 token 体系。

### 部署结论

短期可以采用合并部署：

- 前端站点可容纳在平台任一宿主站点中
- 后端统一收口到 `geelato-web-quickstart`

长期必须支持独立部署：

- `admin-sso` 可独立
- `lite-sso` 可独立
- `rt`、`dt` 可独立
- `auth-server` 可独立

无论部署拓扑如何变化，认证模型保持不变：

- 统一认证后端只有一个事实来源
- 统一 token 只有一套

## 整体架构

### 前端入口层

- 平台站点：`gl-admin-arco-rt-std`、`gl-admin-arco-dt-std`
- 平台认证门面：`gl-admin-sso`
- 第三方认证门面：`gl-lite-sso`
- 第三方应用：如 `freight-portal`

### 统一认证后端层

- 后端宿主：`geelato-web-quickstart`
- 认证核心：`geelato-auth-server`

### 运行依赖

- 站点配置：`.config`
- 数据库：认证库与平台库
- Redis：认证态、缓存和运行时协作

### 逻辑关系

- 平台站点通过 `gl-admin-sso` 接入统一认证
- 第三方应用通过 `gl-lite-sso` 接入统一认证
- `gl-admin-sso` 与 `gl-lite-sso` 最终都调用 `auth-server`
- 平台后端与第三方后端都直接消费 `auth-server` 签发的统一 token

## 模块边界

### 统一认证中心

- `auth-server`：负责认证协议、登录校验、统一 token 与统一用户身份
- `admin-sso`：平台登录前端门面，不是认证事实来源
- `lite-sso`：第三方轻量登录前端门面，不是认证事实来源

### 平台应用

- `rt`
- `dt`
- `quickstart` 后端宿主

### 第三方应用

- `freight-portal`
- 其他外部站点

### 边界说明

- 平台应用和第三方应用都是认证中心使用方，不再自己定义主 token
- `freight-portal` 只是第三方接入示例，不是认证中心主体的一部分
- 前端门面可以变化，统一 token 模型不变

## 统一 token 架构

### 统一原则

- 登录后返回的主凭证统一为 `auth-server` 签发的 `access_token`
- 所有应用都以这枚 token 作为统一登录凭证
- 业务系统后端直接信任并消费这枚 token

### 统一 token 的意义

这意味着：

- `admin-sso` 不自己造 token
- `lite-sso` 不自己造 token
- 平台站点不自己造主 token
- 第三方应用不自己造主 token

整个体系只有一套认证主凭证。

## 平台内部应用接入时序

平台内部应用，指 `rt`、`dt` 这类平台站点。

### 平台主时序

1. 用户打开平台站点
2. 平台站点读取运行时站点配置与认证模式
3. 平台站点跳转或嵌入 `gl-admin-sso`
4. 用户在 `gl-admin-sso` 中完成登录
5. `gl-admin-sso` 调用 `auth-server` 完成认证
6. `auth-server` 签发统一 token
7. 平台站点接收统一 token
8. 平台后端基于该 token 建立平台业务上下文
9. 前端进入平台首页

### 平台链路角色

- 平台站点：负责承接登录态与页面跳转
- `gl-admin-sso`：负责平台登录交互
- `auth-server`：负责认证与 token 签发
- 平台后端：负责基于 token 建立业务用户上下文

## 第三方应用接入时序

第三方应用，指 `freight-portal` 或其他外部业务站点。

### 第三方主时序

1. 用户打开第三方应用
2. 第三方应用以 iframe、popup 或页面跳转方式加载 `gl-lite-sso`
3. 用户在 `gl-lite-sso` 中完成登录
4. `gl-lite-sso` 调用 `auth-server` 完成认证
5. `auth-server` 返回统一 `access_token`
6. `gl-lite-sso` 通过 `postMessage` 向第三方应用回传 `LOGIN_SUCCESS`
7. 第三方应用前端将 Bearer token 传给自己后端
8. 第三方应用后端调用 `auth-server` 校验 token 并识别统一身份
9. 第三方应用建立自己的业务用户上下文

### 第三方链路角色

- 第三方前端：负责嵌入登录门面、接收 token、转发给本系统后端
- `gl-lite-sso`：负责轻量登录交互
- `auth-server`：负责认证与 token 签发
- 第三方后端：负责 token 校验与身份映射

## 两条主链路

### 平台主链路

- 平台站点
- `gl-admin-sso`
- `auth-server`
- 平台后端

适用场景：

- 平台内部应用
- 平台管理端
- 需要与平台站点配置深度联动的应用

### 第三方主链路

- 第三方应用
- `gl-lite-sso`
- `auth-server`
- 第三方后端

适用场景：

- 轻量集成
- iframe 或 popup 接入
- 不希望引入平台完整登录页体系的外部应用

## 合并部署与独立部署

### 合并部署模式

合并部署时可以采用以下方式：

- `admin-sso`、`lite-sso` 容纳在同一个宿主站点中
- 后端统一收口到 `geelato-web-quickstart`
- `auth-server` 能力以内嵌或同服务方式承载

说明：

- 这是部署形态的合并，不是认证模型的合并
- 合并部署只影响部署拓扑，不影响统一 token 事实来源

### 独立部署模式

独立部署时可以拆分为：

- `rt`
- `dt`
- `admin-sso`
- `lite-sso`
- `auth-server`

说明：

- 拆分后只改变部署关系，不改变统一 token 模型
- 各前端门面的回跳协议与消息协议必须保持稳定

## 关键约束

### `admin-sso`

- `admin-sso` 是平台认证门面，不一定必须永远放在 `auth-server/templates` 中
- 当前若放在 `templates`，属于一种同源模板模式
- 后续可演进为独立前端部署，只要登录入口与回跳协议保持一致即可

### `lite-sso`

- `lite-sso` 必须收敛为轻量门面，不得演化成第二个认证中心
- `lite-sso` 与第三方应用之间必须有明确的 `postMessage` 协议和 origin 白名单

### 业务后端

- 业务后端必须直接信任并校验统一 token
- 业务后端不应再重新签发新的主 token

## 统一消息与回传协议建议

建议 `lite-sso` 和 `admin-sso` 在可嵌入场景下统一使用如下成功消息结构：

```json
{
  "type": "LOGIN_SUCCESS",
  "data": {
    "accessToken": "xxxx",
    "refreshToken": "xxxx",
    "expireInSeconds": 7200,
    "tokenType": "Bearer",
    "issuer": "auth-server"
  }
}
```

建议原则：

- 主字段以 token 为中心
- `user` 字段可以附带，但不是最终信任对象
- 业务站点最终用户信息应以后端确认结果为准

## 安全与治理要求

### 前端门面要求

- `admin-sso`、`lite-sso` 都必须明确来源域控制
- 不允许生产环境下对 `postMessage` 使用宽松 `*` 策略
- 登录成功后的消息回传必须使用明确的 `targetOrigin`

### 后端要求

- 所有业务后端必须以统一 token 为认证入口
- token 失效、过期、撤销时必须统一返回未认证状态
- 业务后端需要提供当前用户接口用于前端初始化

### 配置要求

- 平台站点通过运行时配置决定使用 `admin-sso` 还是本地模式
- 第三方应用通过运行时配置决定 `lite-sso` 地址、可信 origin 与回调策略

## 方案收益

### 认证中心统一

- 整个体系只有一个认证中心
- 整个体系只有一套主 token
- 多应用接入方式不同，但认证事实来源一致

### 工程边界清晰

- `auth-server` 管认证
- `admin-sso` 管平台登录门面
- `lite-sso` 管第三方轻量门面
- 业务系统管自己的业务上下文

### 便于演进

- 可先合并部署，再逐步拆分
- 可先接入单个第三方应用，再复制到其他第三方
- 可在不改变认证模型的前提下调整部署方式

## 最小验收标准

- `auth-server` 是唯一 token 签发方
- 平台应用通过 `admin-sso` 接入统一认证中心
- 第三方应用通过 `lite-sso` 接入统一认证中心
- 平台后端与第三方后端都直接消费 `auth-server` 统一 token
- `freight-portal` 只是第三方接入示例，不再被视作认证中心主体部分

## 推荐继续阅读

- [统一认证总览](overview.md)
- [lite-login 第三方应用接入](lite-login-integration.md)
- [PlatformWebRuntime](../runtime/platform-web-runtime.md)
