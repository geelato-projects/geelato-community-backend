---
title: SC 模块
sidebar_label: SC 模块
---
# SC 模块

对应配置文件：

- `properties/sc.properties`

## 作用

这个文件承载 SC 相关能力的开关与站点目录配置。

## 关键配置

- `geelato.sc`
- `geelato.sc.folder`
- `geelato.sc.path`

## 配置含义

### 模块开关

- `geelato.sc`

用于控制 SC 能力是否启用。

### 目录与访问路径

- `geelato.sc.folder`
- `geelato.sc.path`

分别用于：

- 指定本地站点文件目录
- 指定站点对外访问的路径片段

## 使用建议

- 本地目录应与部署机真实文件结构保持一致
- 若作为静态站点或资源站点目录使用，需要同时考虑 Web 层映射
- 生产环境建议不要依赖硬编码磁盘路径，优先通过环境变量覆盖

## 推荐继续阅读

- [系统配置](overview.md)
