---
title: Controller 开发规范
sidebar_label: Controller 开发规范
---

# Controller 开发规范

平台 Web 层（Controller）的统一规范：分页参数解析、异常处理。规范的目标是**同一套参数约定、同一套异常链路**；端点由各控制器自行声明，`BaseController` 只提供参数捕获与工具能力（不带任何内置端点）。

## 总原则

1. **Controller 不写 try-catch**。业务异常直接抛出，由全局异常处理器 `PlatformExceptionHandler` 统一处理（见下文"异常处理约定"）。
2. **分页/过滤参数解析统一走** `ParameterOperator`（`BaseController` 已继承），不自造参数名。

## 分页参数规范

### 参数命名

| 参数 | 规范名 | 兼容别名（按优先级） | 说明 |
|---|---|---|---|
| 页码 | `pageNum` | `current`（旧 POST body）、`page` | 同时出现时规范名优先 |
| 页大小 | `pageSize` | `limit` | 单页上限 1000 |
| 排序 | `orderBy` | `order`（旧 POST body） | `\|` 分隔符替换为空格，如 `create_at\|desc` |

新接口一律使用规范名；旧接口传历史别名同样可解析。

### 默认值与语义

| 入口 | 未传页码/页大小时的默认值 | 语义 |
|---|---|---|
| POST body（pageQuery 链路） | `pageNum=1`、`pageSize=10` | 分页查询 |
| GET query（query 链路） | `pageNum=-1`、`pageSize=-1` | **不分页、全量查询**（`QueryCommand.hasPagination()` 以 `> 0` 判定） |

GET 链路的全量语义是既有行为：未传分页参数即要求全量。显式传 `-1` 或 `0` 会被拒绝（见下）。

### 非法参数硬失败

参数**非整数、非正数、页大小超上限（1000）**时不做静默纠正，直接抛 `InvalidPageParamException`（错误码 10005，HTTP 400），文案包含参数名与实际传入值，便于调用方自助修正：

```
分页参数 pageSize=abc 不是有效整数
分页参数 current=0 必须为正整数
分页参数 pageSize=5000 超出上限 1000
```

## 查询条件规范

沿用 `字段名|操作符` 键值约定，由 `getFilterGroup(entityClass, requestBody, true)` 统一解析为 `FilterGroup`（按实体字段名过滤，未知操作符报错）：

```json
{ "userName|contains": "张", "createAt|bt": "2026-01-01,2026-12-31", "enableStatus": 1 }
```

## 异常处理约定

### 不写 try-catch

Controller 方法内不捕获异常、不拼装失败响应。历史写法（`catch (Exception e) { log.error(...); return ApiResult.fail(e.getMessage()); }`）已废弃——它会吞掉异常，使全局处理器的排障机制全部失效。

### 全局处理器行为

异常统一由 `PlatformExceptionHandler`（`@RestControllerAdvice`）处理：

| 异常类型 | HTTP 状态 | 响应 |
|---|---|---|
| `CoreException`（业务异常基类） | 按 `getHttpStatus()`（鉴权类 401/403/400，默认 500） | `ApiResult.fail(PlatformErrorResult, 友好文案)` |
| `ConstraintViolationException`（JSR-303） | 400 | 字段错误明细 |
| 其他 `Exception`（兜底） | 400 | 透出 `ex.getMessage()`（无消息时"系统异常：类名"） |

所有分支均携带 **logTag 反馈凭据**：文案末尾追加 `（错误码 xxx，反馈凭据 xxx）`，异常完整堆栈异步落库到 `platform_exception_log`（id=logTag），用户报障凭截图即可在服务端检索根因。

### 前端判定约定

- **HTTP 状态码**：语义化（401/403/400/500），错误分支（axios `catch`）同样解析响应体。
- **业务码**：`ApiResult.code`，`20000` 为成功、`-2` 为失败（历史成功码即 20000）。
- 响应体结构（`msg/code/status/data`）在成功与失败路径保持一致。

### 业务校验失败

需要向前端反馈可恢复的业务错误（如"登录名不能为空"）时，抛 `CoreException` 子类（`httpStatus=400`），不要返回 `ApiResult.fail(...)`（后者 HTTP 200，不符合语义化状态码约定）。

## 控制器编写约定

- 端点由控制器自行声明（`@ApiRestController` + `@RequestMapping`），`BaseController` 不提供内置端点。
- 调用 `getFilterGroup` 的方法签名需声明 `throws ParseException`。
- **两个控制器不能使用相同的类级路径前缀**（父子前缀如 `/dict` 与 `/dict/item` 不冲突）。历史上 `/api/meta` 曾由运行态/设计态两个控制器"方法分家"共存，属遗留隐患，已合并为单一 `MetaController`（运行态 + 设计态接口分区，category 用中性的 `platform`）。

### 业务 Service 重载分裂陷阱

业务 Service 若声明比父类更窄参数边界的方法（如 `DictItemService.updateModel(DictItem)` 携带"禁用联动子项"逻辑），会与 `BaseService` 的泛型 `<T extends BaseEntity> updateModel(T)` 构成**重载而非覆盖**——调用点经 `BaseService` 静态类型分派时将**静默绕过业务逻辑**。

- Controller 内调用以具体 Service 类型（如 `dictItemService`）声明，让编译器解析到最具体的重载；
- `BaseSortableService` 的 `createModel`/`updateModel`/`isDeleteModel` 已改为真正覆写（原窄边界声明构成重载，经父类引用调用会绕过 seqNo 初始化与删除标记）——新增类似 Service 方法时勿重蹈。

## 存量迁移指引

旧风格控制器（无参方法 + 手动解析 + 方法内 try-catch）按以下步骤迁移：

1. 逐方法去除 try-catch 包装，方法体保留原逻辑（端点声明不动）。
2. `getFilterGroup` 调用需在方法签名补 `throws ParseException`。
3. 行为变化点（需与前端确认）：失败路径从 HTTP 200 变为语义化状态码；`msg` 末尾追加错误码与反馈凭据；成功路径响应完全不变。

## 参考实现

- 参数捕获基类：`cn.geelato.web.platform.srv.BaseController`（继承 `ParameterOperator`）
- 分页解析与防护：`cn.geelato.web.platform.srv.ParameterOperator`
- 全局异常处理：`cn.geelato.web.platform.run.PlatformExceptionHandler`
- 试点示例：`DictItemController`、`UserController`（去 try-catch 形态）、`MetaController`（运行态+设计态合并）、`AuditLogController`（独立 REST 风格，参数命名与异常约定与本规范一致）
