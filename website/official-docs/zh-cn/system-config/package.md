# Package 模块

对应配置文件：

- `properties/package.properties`

## 作用

这个文件承载打包发布相关的基础路径配置。

## 关键配置

- `geelato.package.env`
- `geelato.package.path`
- `geelato.package.uploadFolder`

## 配置含义

### 环境标识

- `geelato.package.env`

用于标识当前打包逻辑运行在哪个环境。

### 包输出路径

- `geelato.package.path`

用于指定打包产物或中间文件的存放根路径。

### 上传目录

- `geelato.package.uploadFolder`

用于指定打包相关上传临时目录。

## 使用建议

- 部署环境应根据机器目录结构显式覆盖默认路径
- 上传临时目录与正式产物目录建议区分开
- 如果后续需要更复杂的发布链路，可在此基础上继续扩展

## 推荐继续阅读

- [系统配置](overview.md)
