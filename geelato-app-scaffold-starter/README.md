# geelato-app-scaffold-starter

该 Starter 用于让外部用户基于 Geelato 快速初始化一个可生产开发的后端工程，并且默认只暴露“脚手架最小能力集”，避免引入 `geelato-web-platform` 的全量接口面。

## 开箱即用能力（默认开启）
- 登录鉴权：JWT + OAuth2
- 文件上传/下载：支持 OSS（配置后启用）
- MQL：`/api/meta/*`（runtime 版）
- ORM Fluent DSL：通过 `geelato-orm` 在业务代码中直接使用 `MetaFactory`
- 字典：Dict/DictItem + Dictionary（按 code 获取字典项）
- 组织用户 + RBAC：Org/User/Role/Permission 及映射
- Swagger：dev/test 默认开启；prod 默认关闭（可配置开启）
- 快速开发 HTTP 服务：开发者可直接新增 Controller/Service，不与平台能力耦合

## 使用方式（逐项）
### 0. 通用约定
- BaseUrl：`http://localhost:{port}`
- 统一前缀：`/api`
- 常用 Header：
  - `Tenant-Code`: 租户编码（建议始终传）
  - `App-Id`: 应用标识（如你启用了应用隔离）
  - `Authorization`: 登录后携带
    - JWT：`JWTBearer {token}`
    - OAuth2：`Bearer {accessToken}`

### 1. 登录鉴权
**JWT 登录**
- 接口：`POST /api/user/login`
- 说明：该接口标注 `@IgnoreVerify`，不需要 `Authorization`
- 示例：

```bash
curl -X POST "http://localhost:8088/api/user/login" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  --data "{\"username\":\"gl_user\",\"password\":\"***\"}"
```

返回的 `token` 用于后续请求：

```bash
Authorization: JWTBearer {token}
```

**OAuth2 登录**
- 接口：`POST /api/oauth2/login`
- 刷新：`POST /api/oauth2/refreshToken`

### 2. 文件上传 / 下载（含 OSS）
**上传**
- 接口：`POST /api/upload/file`（multipart）

**下载文件**
- 接口：`GET /api/resources/file`

**下载配置（常用于登录前加载站点配置）**
- 接口：`GET /api/resources/json?fileName={...}`

**OSS 管理**
- 接口：`/api/oss/*`（配置齐全才会出现路由）

### 3. MQL（MetaRuntimeController）
- 列表：`POST /api/meta/list`
- 保存：`POST /api/meta/save/{biz}`
- 删除：`POST /api/meta/delete/{biz}/{id}`

请求体示例（查询）：

```json
{
  "platform_user": {
    "@fs": "id,loginName,name",
    "@p": "1,10",
    "@order": "createAt|-"
  }
}
```

### 4. ORM Fluent DSL（后端代码能力）
参考文档：[backend-fluent-dsl-guide.md](file:///d:/geelato/geelato-enterprise/geelato-community/docs/orm/backend-fluent-dsl-guide.md)

最小示例：

```java
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name"})
        .page(1, 10)
        .list();
```

### 5. 字典（维护 + 获取）
- 维护字典：`/api/dict/*`（DictController）
- 维护字典项：`/api/dictItem/*`（DictItemController）
- 按 code 获取字典项：`GET /api/dictionary/{code}`（DictionaryController）

### 6. 组织 / 用户 / RBAC
- 组织：`/api/security/org/*`
- 用户：`/api/security/user/*`
- 角色：`/api/security/role/*`
- 权限：`/api/security/permission/*`
- 用户-角色、角色-权限等映射：`/api/security/role/*`、`/api/security/org/*` 对应映射接口

### 7. Swagger（OpenAPI）
- OpenAPI JSON：`GET /v3/api-docs`
- Swagger UI：`/swagger-ui/index.html`

### 8. 快速开发 HTTP 服务
- 新增你自己的 Controller/Service 放在业务工程包下（确保 `scanBasePackages` 包含业务包）
- 对外接口建议同样使用 `@ApiRestController`，从而自动具备：
  - `/api` 前缀
  - 统一 JSON produces 约定

## 接口前缀
- 本 Starter 会对 `@ApiRestController / @DesignTimeApiRestController / @ApiRuntimeRestController` 统一添加 `/api` 前缀。

## 能力收口（strict 模式）
默认开启 strict 模式，Starter 会在 Spring 容器初始化早期移除非最小能力集的 platform Controller BeanDefinition，从而达到：
- 路由不可访问（404）
- Swagger 不展示

相关配置：

```properties
geelato.app.scaffold.enabled=true
geelato.app.scaffold.strict=true
geelato.app.scaffold.capabilities=login,mql,organization,user,dictionary,upload
```

若需要临时放开全量平台能力（不建议用于生产）：

```properties
geelato.app.scaffold.strict=false
```

若需要额外放行个别 Controller（FQCN）：

```properties
geelato.app.scaffold.extra-controllers[0]=cn.geelato.web.platform.srv.notice.NoticeController
```

## Swagger（OpenAPI）
dev/test：默认开启  
prod：默认关闭，除非显式开启

```properties
geelato.app.scaffold.openapi-enabled=true
geelato.app.scaffold.openapi-expose-in-prod=false
```

prod 显式开启：

```properties
geelato.app.scaffold.openapi-expose-in-prod=true
```

## OSS（S3/对象存储）
当以下配置齐全时，会自动创建 `OSSFileHelper` Bean，从而启用 `/api/oss/*` 相关接口：

```properties
geelato.oss.accessKeyId=***
geelato.oss.accessKeySecret=***
geelato.oss.endPoint=oss-cn-xxx.aliyuncs.com
geelato.oss.bucketName=your-bucket
geelato.oss.region=cn-xxx
```
