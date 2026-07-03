# Docker 部署

这篇文档用于说明 Geelato Framework 在容器化场景下的推荐部署边界。

当前仓库已经明确了运行时与设计时的模块拆分：

- `geelato-web-runtime`
- `geelato-web-platform`
- `geelato-web-designer`
- `geelato-app-scaffold`

因此 Docker 化时也建议沿用同样的职责边界，而不是把所有能力重新混成一个不可区分的镜像。

## 推荐容器化对象

### 业务运行时容器

适用于只提供业务执行能力的场景，建议打包：

- `geelato-web-runtime`
- 或基于它构建的 `geelato-app-scaffold`

这类容器通常只暴露运行时接口，不承载设计时管理能力。

### 设计时平台容器

适用于需要元数据设计、脚本管理和平台治理能力的场景，建议打包：

- `geelato-web-designer`

它用于承载设计时能力，因此部署时应与纯运行时环境区分开。

## 配置建议

- 通过环境变量注入数据库连接、账号和密码
- 上传目录、转换目录等文件路径通过挂载卷提供

例如：

```properties
spring.datasource.primary.jdbc-url=${GEELATO_PRIMARY_JDBCURL}
spring.datasource.primary.username=${GEELATO_PRIMARY_JDBCUSER}
spring.datasource.primary.password=${GEELATO_PRIMARY_JDBCPASSWORD}
geelato.upload.root-directory=/data/upload
geelato.upload.convert-directory=/data/upload/convert
geelato.upload.config-directory=/data/upload/config
```

## 当前建议

在官方仓库现阶段，更推荐先完成：

1. 本地普通部署验证
2. 数据库初始化验证
3. 运行时与设计时边界确认

再把上述配置外置到 Docker 镜像与编排环境中。

## 推荐继续阅读

- [普通部署](runtime-designer-deployment.md)
- [PlatformWebRuntime](../runtime/platform-web-runtime.md)
- [PlatformDesginer](../designer/platform-desginer.md)
- [App Scaffold](../guide/app-scaffold.md)
