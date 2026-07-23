---
title: BOM 与 Starter
sidebar_label: BOM 与 Starter
---

# BOM 与 Starter

框架的官方交付入口采用 `BOM + Starter` 模式。

## 官方接入入口

- `geelato-framework-bom`
- `geelato-framework-starter`

## 为什么采用这种交付方式

框架使用者不应该自己去理解所有基础模块之间的拼装关系和装配顺序。

Starter 负责给出统一推荐入口，BOM 负责统一版本对齐。

## Starter 当前覆盖范围

`geelato-framework-starter` 当前承接最小框架底座：

- `geelato-lang`
- `geelato-utils`
- `geelato-security`
- `geelato-core`
- `geelato-web-common`
- `geelato-dynamic-datasource`
- `geelato-orm`

## Starter 不负责什么

Starter 不承接这些实现偏好强的平台默认能力：

- 平台化上传运行时
- 设计时元数据治理
- 仅属于 sample 的扩展依赖

## 推荐使用规则

先从 Starter 起步，再按需叠加：

- runtime 模块
- designer 模块
- sample 或业务扩展模块
