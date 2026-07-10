# geelato-app-scaffold-starter

该 Starter 用于让开发者基于 Geelato 快速初始化一个可生产开发的后端工程。
它默认只暴露“脚手架最小能力集”，剔除了冗余的低代码平台配置接口，让工程保持极简，同时开箱即用地提供了一套企业级后端必备的基础设施。

---

## 一、 脚手架开箱即用能力总览

引入本 Starter 后，你的工程将自动具备以下 7 大核心能力：

1. **🔐 登录与鉴权**：内置 JWT 本地登录与 OAuth2 授权码登录，自动拦截并解析 Token。
2. **📁 文件处理与 OSS**：提供统一的本地/OSS 文件上传、下载、预览接口。
3. **🔍 MQL 动态查询**：通过 `/api/meta/*` 接口，前端可直接发送 JSON 动态查询数据库。
4. **⚙️ ORM Fluent DSL**：后端业务代码可直接使用 `MetaFactory` 进行防 SQL 注入的链式 CRUD 操作。
5. **📖 数据字典**：内置字典项的创建、维护与按 Code 快速查询能力。
6. **👥 组织架构与 RBAC**：内置标准的用户、组织、角色、权限模型及分配接口。
7. **📚 OpenAPI (Swagger)**：默认在 dev/test 环境自动生成并暴露接口文档。

---

## 二、 通用调用约定

在调用以下所有能力之前，请注意基础约定：

- **统一前缀**：所有脚手架接口均以 `/api` 开头。
- **租户隔离**：建议在所有请求头中携带 `Tenant-Code`（如未提供，默认为 `geelato`）。
- **认证凭证**：登录成功后，需在请求头携带 `Authorization`。
  - JWT 登录：`Authorization: JWTBearer {token}`
  - OAuth2 登录：`Authorization: Bearer {accessToken}`

---

## 三、 核心能力使用指南

### 1. 登录与鉴权能力

脚手架默认开放了标准的登录入口。

**使用方式：JWT 密码登录**
无需携带 Token，直接请求获取：
```bash
curl -X POST "http://localhost:8088/api/user/login" \
  -H "Content-Type: application/json" \
  -H "Tenant-Code: geelato" \
  --data "{\"username\":\"gl_user\",\"password\":\"***\"}"
```
*返回的 token 用于后续所有业务请求。*

### 2. MQL 动态查询能力

允许前端通过 JSON 定义查询结构，直接获取所需数据，无需后端额外编写 Controller/Mapper。

**使用方式：前端请求 MQL 接口**
- **列表查询**：`POST /api/meta/list`
- **单条保存**：`POST /api/meta/save/{biz}`
- **单条删除**：`POST /api/meta/delete/{biz}/{id}`

*查询示例（获取用户列表，只返回指定字段并排序）：*
```json
{
  "platform_user": {
    "@fs": "id,loginName,name",
    "@p": "1,10",
    "@order": "createAt|-"
  }
}
```

### 3. ORM Fluent DSL 能力

后端开发人员在编写自定义业务逻辑时，不再需要写繁琐的 SQL 或 MyBatis XML。

**使用方式：在 Service 中直接调用 MetaFactory**
```java
// 查询示例：获取第 1 页的 10 个用户，仅返回 id 和 name 字段
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name"})
        .page(1, 10)
        .list();
```

### 4. 数据字典能力

提供标准的数据字典维护体系，支持树形或列表结构。

**使用方式：维护与查询字典**
- **创建字典**：`POST /api/dict/createOrUpdate`
- **创建字典项**：`POST /api/dict/item/createOrUpdate`
- **业务查询**：前端通过 `GET /api/dictionary/{code}`（例如 `GET /api/dictionary/order_status`）即可快速拉取字典项用于下拉框渲染。

### 5. 组织、用户与 RBAC 能力

内置了一套完整的权限管理 API。

**使用方式：用户与角色分配**
- **创建组织**：`POST /api/security/org/createOrUpdate`
- **创建用户**：`POST /api/security/user/createOrUpdate`
- **分配角色**：`POST /api/security/role/user/insert`
*示例：为新员工绑定研发角色*
```bash
curl -X POST "http://localhost:8088/api/security/role/user/insert" \
  -H "Content-Type: application/json" \
  -H "Authorization: JWTBearer {token}" \
  --data "{\"userId\":\"123\",\"roleId\":\"456\",\"tenantCode\":\"geelato\"}"
```

### 6. 文件处理与 OSS 能力

统一处理前端的文件上传与下载。如果配置了阿里云等对象存储，会自动切换至 OSS 模式。

**使用方式：文件上传**
- **接口**：`POST /api/upload/file` (multipart)
```bash
curl -X POST "http://localhost:8088/api/upload/file" \
  -H "Authorization: JWTBearer {token}" \
  -F "file=@D:/tmp/demo.png"
```
*上传成功后会返回包含附件 `id` 的元数据，前端可使用 `GET /api/resources/file?id={id}&isPreview=true` 进行图片预览。*

### 7. OpenAPI (Swagger) 自动生成能力

你的自定义接口与脚手架接口会自动汇总为标准的 OpenAPI 规范文档。

**使用方式：访问文档**
- **Swagger UI 界面**：`http://localhost:8088/swagger-ui/index.html`
- **JSON 契约**：`http://localhost:8088/v3/api-docs`

---

## 四、 高级配置项 (application.properties)

脚手架默认会自动屏蔽非必要的低代码平台内部接口，保持业务工程干净。

```properties
# 生产环境是否暴露 Swagger（默认 false，保障安全）
geelato.app.scaffold.openapi-expose-in-prod=false

# 只要配置了以下 OSS 参数，脚手架会自动开启 /api/oss/* 相关能力
geelato.oss.accessKeyId=***
geelato.oss.accessKeySecret=***
geelato.oss.endPoint=oss-cn-xxx.aliyuncs.com
geelato.oss.bucketName=your-bucket
```
