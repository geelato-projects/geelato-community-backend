# SrvExplain：对外 HTTP 接口调用说明

## 目标
- 以“接口调用者”的视角，提供可直接落地调用的 HTTP 接口说明。
- 文档以 Controller 为维度归档：每个 Controller 一份 Markdown 文档，覆盖其所有 endpoints。
- 文档内容从源码静态扫描自动生成；对无法静态推导的部分以占位区块明确标注，便于人工补齐。

## 文档结构
- `index.md`：全仓库索引（按模块/Controller 分组）
- `<module>/README.md`：模块索引
- `<module>/controllers/<Controller>.md`：Controller 说明
- `_meta/`：生成器统计与冲突检测
- `_templates/`：生成模板（生成器使用）

## 通用请求约定

### Base URL
- `{{baseUrl}}`：调用方需要根据部署环境替换，例如 `http://localhost:8080`、`https://api.example.com`

### 常用 Header
- `App-Id`：应用 ID（部分代码兼容 `appId/AppId/App-Id` 变体）
- `Tenant-Code`：租户编码（部分代码兼容 `tenantCode/TenantCode/Tenant-Code` 变体；缺省可能从上下文读取）
- `Accept-Language`：语言，例如 `zh-CN`
- `Authorization`：鉴权 token（若接口未标注“无需鉴权”）

### 鉴权（概览）
- 默认需要 `Authorization`，除非接口方法标注 `@IgnoreVerify`（文档会显式写出“无需 Authorization”）。
- 该仓库鉴权实现支持多种 token 前缀（例如 `JWTBearer `、`Bearer `、`Anonymous ` 等），具体以运行环境网关/客户端实现为准。

## 通用响应约定

### ApiResult（通用返回包装）
常见字段：
- `code`：业务码（成功通常为 200）
- `status`：业务状态（成功通常为 success）
- `msg`：提示信息
- `data`：返回数据

不同 Controller 可能返回：
- `ApiResult<T>`
- `ApiPagedResult<T>` / `ApiMultiPagedResult<T>`（分页/多分页）
- `ApiMetaResult<T>`（元数据相关）

## 特殊请求体约定（GQL/JSON 直读）
部分接口不使用 `@RequestBody` 参数，而是直接从原始 request body 读取字符串作为“GQL/JSON”（例如 Meta 相关接口）。这类接口在文档中会以“Body: 原始 JSON/GQL 字符串”标注，并给出最小示例占位。

## 生成说明
本目录下的文档由 `geelato-srvexplain-generator` 静态扫描生成（不启动站点）。生成器会：
- 扫描全仓库 modules 的 `src/main/java`
- 识别 Controller 与其 endpoint 注解（`@RequestMapping/@GetMapping/.../@RuntimeMapping`）
- 生成每个 Controller 的 Markdown 文档与索引
- 输出冲突检测与统计到 `_meta/`

