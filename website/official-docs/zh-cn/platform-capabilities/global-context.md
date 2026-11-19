---
title: 全局上下文
sidebar_label: 全局上下文
---

# 全局上下文

本页说明 `geelato-core` 中 `cn.geelato.core.GlobalContext` 提供的全局上下文能力，主要包括：

- 系统环境标识
- 系统密级与匿名访问开关
- 列级加密与 API 加密开关
- 默认加密算法选择
- AES / SM4 / SM2 / RSA 密钥读取

它更像一个“平台底层全局运行参数入口”，而不是普通业务配置类。

## 这类能力解决什么问题

`GlobalContext` 主要解决的是平台底层在很多位置都需要统一读取的几类安全上下文参数，例如：

- 当前处于什么环境
- 是否允许列级加密
- 是否启用 API 加密语义
- 当前默认采用什么加密算法
- 当前算法所需密钥从哪里读取

因此它的定位偏底层公共能力，而不是给某一个单独模块使用的普通配置对象。

## 当前有哪些全局项

### 系统环境

当前代码内置：

- `__Environment__ = "development"`

并通过：

- `GlobalContext.getEnvironment()`

对外暴露。

### 系统密级

系统密级是部署级防护能力分级，通过环境变量配置：

- `GEELATO_SECURITY_LEVEL`
- 取值 0-10 的整数，缺省 `2`
- 非法值（非整数或超出 0-10）启动期即失败
- 类加载时读取一次固化，运行期不可变

等级定义：

| 码值 | 名称 | 列级加密 | API 报文加密 | 匿名登录通道 |
|---|---|---|---|---|
| 0 | 开发级 | 关 | 关 | 开 |
| 1 | 基础级 | 开 | 关 | 开 |
| 2（默认） | 标准级 | 开 | 开 | 开 |
| 3 | 增强级 | 开 | 开 | 开 |
| 4-9 | 预留（视同 3） | 开 | 开 | 开 |
| 10 | 严格级 | 开 | 开 | 关 |

派生规则：

- 列级加密：密级 ≥ 1 开启（`getColumnEncryptOption()`）
- API 报文加密：密级 ≥ 2 开启（`getApiEncryptOption()`）

### 匿名访问与缓存

匿名登录通道由两部分决定：

- 环境变量 `GEELATO_ANONYMOUS_ENABLED` 显式指定优先，取值 `true` / `false`（忽略大小写），非法值启动期即失败
- 未设置时按密级判定：密级 < 10 开启，≥ 10 关闭（`getAnonymousOption()`）

缓存开关 `__CACHE__` 取决于是否是 `product` 环境。

## 当前哪些项支持环境变量

`GlobalContext` 当前显式支持通过环境变量覆盖的是：

- `GEELATO_SECURITY_LEVEL`
- `GEELATO_ANONYMOUS_ENABLED`
- `GEELATO_ENCRYPT_TYPE`
- `GEELATO_AES_KEY`
- `GEELATO_SM4_KEY`
- `GEELATO_SM2_PUBLIC_KEY`
- `GEELATO_SM2_PRIVATE_KEY`
- `GEELATO_RSA_PUBLIC_KEY`
- `GEELATO_RSA_PRIVATE_KEY`

读取方式统一是：

- 若环境变量存在且非空，则优先使用环境变量
- 否则退回代码内置默认值

## 当前哪些项还不是环境变量驱动

需要特别说明：

当前 `GlobalContext` 里下面这些值仍然是代码常量，并没有像加密密钥那样从环境变量读取：

- `__Environment__`
- `__ColumnEncrypt__`（由密级派生）
- `__ApiEncrypt__`（由密级派生）

因此就当前仓库状态来说：

- 加密算法与密钥支持环境变量
- 密级与匿名访问开关支持环境变量（`GEELATO_SECURITY_LEVEL` / `GEELATO_ANONYMOUS_ENABLED`）
- 环境标识仍是代码常量
- 列加密与 API 加密开关由密级派生，不可独立设置

## 如何配置采用什么加密

默认算法来源是：

- `GlobalContext.getEncryptType()`

它优先读取：

- `GEELATO_ENCRYPT_TYPE`

当前 `EncryptUtils` 支持的算法包括：

- `aes`
- `rsa`
- `sm2`
- `sm4`

其中：

- `encrypt()` 以当前全局算法为准
- `decrypt()` 则会根据数据前缀自动识别算法

例如，加密后的持久化值格式是：

```text
aes:xxxx
rsa:xxxx
sm2:xxxx
sm4:xxxx
```

因此解密时不依赖“当前默认算法”，而是依赖数据本身的前缀。

## 各算法需要哪些密钥

### AES

环境变量：

- `GEELATO_AES_KEY`

默认值当前存在代码常量中。

### SM4

环境变量：

- `GEELATO_SM4_KEY`

### RSA

环境变量：

- `GEELATO_RSA_PUBLIC_KEY`
- `GEELATO_RSA_PRIVATE_KEY`

其中：

- 加密使用公钥
- 解密使用私钥

如果缺失，`EncryptUtils` 会抛出配置缺失异常。

### SM2

环境变量：

- `GEELATO_SM2_PUBLIC_KEY`
- `GEELATO_SM2_PRIVATE_KEY`

当前实现里，SM2 加解密都要求公私钥都可用。

## 环境变量配置示例

密级与匿名通道示例（生产高保密部署）：

```powershell
$env:GEELATO_SECURITY_LEVEL="3"
$env:GEELATO_ANONYMOUS_ENABLED="false"
```

涉密部署关闭匿名通道：

```powershell
$env:GEELATO_SECURITY_LEVEL="10"
```

PowerShell 示例：

```powershell
$env:GEELATO_ENCRYPT_TYPE="rsa"
$env:GEELATO_RSA_PUBLIC_KEY="your-public-key"
$env:GEELATO_RSA_PRIVATE_KEY="your-private-key"
```

AES 示例：

```powershell
$env:GEELATO_ENCRYPT_TYPE="aes"
$env:GEELATO_AES_KEY="your-16-char-key"
```

SM4 示例：

```powershell
$env:GEELATO_ENCRYPT_TYPE="sm4"
$env:GEELATO_SM4_KEY="your-sm4-key"
```

部署时的原则是：

- 密级通过 `GEELATO_SECURITY_LEVEL` 按部署场景设定（缺省 2 即一般商用生产）
- 生产环境建议显式关闭匿名通道（`GEELATO_ANONYMOUS_ENABLED="false"`）
- 算法通过 `GEELATO_ENCRYPT_TYPE` 指定
- 密钥通过对应环境变量注入
- 不要把真实密钥保留在源码默认值中

## 如何开启表字段级加解密

### 第 1 步：全局开关允许列加密

字段级加密首先受：

- `GlobalContext.getColumnEncryptOption()`

控制。

当前实现里，它由系统密级派生：

- 密级 ≥ 1 时开启

按照默认密级：

- `GEELATO_SECURITY_LEVEL` 缺省 `2`（标准级）

所以列加密默认是开启的。

### 第 2 步：列元数据标记为加密列

真正决定某个字段是否加密的，不是“所有字段统一加密”，而是列元数据里的：

- `ColumnMeta.encrypted`

只有当某个字段的列元数据满足：

- `encrypted = true`

保存时才会进入加密逻辑。

### 第 3 步：保存链路自动加密

在 `JsonTextSaveParser` 里：

- 先判断 `GlobalContext.getColumnEncryptOption()`
- 再遍历字段
- 对 `ColumnMeta.isEncrypted()` 为 `true` 的字段调用 `EncryptUtils.encrypt(...)`

因此保存链路的真实规则是：

- 全局列加密开关开启
- 且当前字段元数据标记为加密
- 才会在落库前加密

### 第 4 步：查询链路自动解密

查询时，`CommonRowMapper` 会对字符串值统一调用：

- `EncryptUtils.decrypt(...)`

它的实现会先判断是否匹配：

- `算法前缀:密文`

如果是，就按前缀算法解密；如果不是，就原样返回。

因此：

- 加密列可以自动解密
- 普通未加密字符串不会被误伤

## 字段级加解密的配置建议

更推荐的做法是：

- 生产环境通过环境变量注入真实密钥
- 统一约定 `GEELATO_ENCRYPT_TYPE`
- 只对真正敏感字段把 `ColumnMeta.encrypted` 设为 `true`
- 不要把“全局开启列加密”等同于“所有字段都加密”

## 关于系统环境和密级的现状

虽然 `GlobalContext` 暴露了：

- 环境
- 密级
- API 加密

这些概念，但外部化程度并不相同：

- 加密算法和密钥支持环境变量
- 密级与匿名访问开关支持环境变量（`GEELATO_SECURITY_LEVEL` / `GEELATO_ANONYMOUS_ENABLED`）
- 环境标识仍是代码常量
- 列加密与 API 加密开关由密级派生，不可独立设置

## 推荐继续阅读

- [认证鉴权](../authentication/security-authentication.md)
- [流量染色](traffic-tagging.md)
- [系统配置](../system-config/overview.md)
