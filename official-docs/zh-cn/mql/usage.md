# MQL 使用指引

这一页说明 Geelato Framework 当前在平台侧如何使用 MQL 发起查询、保存、删除与表达式计算。

## 核心接口

MQL 的核心处理类为 `MetaController`，主要接口如下：

| 操作 | 接口路径 | HTTP 方法 | 说明 |
| :--- | :--- | :--- | :--- |
| 查询列表 | `/api/meta/list` | POST / GET | 单实体列表查询，支持分页、过滤、排序 |
| 多列表查询 | `/api/meta/multiList` | POST | 一次请求查询多个实体列表 |
| 单条保存 | `/api/meta/save/{biz}` | POST | 保存单个实体，`{biz}` 为业务标识 |
| 批量保存 | `/api/meta/batchSave` | POST | 批量保存同一实体的多条数据 |
| 多实体保存 | `/api/meta/multiSave` | POST | 一次请求保存多个不同实体的数据 |
| ID 删除 | `/api/meta/delete/{biz}/{id}` | POST | 根据 ID 删除，支持逗号分隔多个 ID |
| 条件删除 | `/api/meta/delete2/{biz}` | POST | 根据 MQL 过滤条件删除数据 |

## 查询语法

查询请求通常采用这种 JSON 结构：

```json
{
  "platform_user": {
    "loginName": "admin"
  }
}
```

这表示查询 `platform_user` 实体中 `loginName = admin` 的记录。

## 常用关键字

MQL 使用以 `@` 开头的关键字控制查询行为。

| 关键字 | 说明 | 示例 |
| :--- | :--- | :--- |
| `@fs` | 字段选择 | `"@fs": "id,name,loginName"` |
| `@p` | 分页参数 `(页码,每页条数)` | `"@p": "1,10"` |
| `@order` | 排序 | `"@order": "createAt|+,name|-"` |
| `@group` | 分组 | `"@group": "type"` |
| `@b` | 复杂逻辑组合 | 见下文示例 |

## 过滤操作符

字段查询支持 `"字段名|操作符": "值"` 的写法；如果省略操作符，则默认按 `eq` 处理。

| 操作符 | 含义 | 示例 |
| :--- | :--- | :--- |
| `eq` | 等于 | `"name": "张三"` |
| `neq` | 不等于 | `"status\|neq": "0"` |
| `lt` | 小于 | `"age\|lt": "18"` |
| `lte` | 小于等于 | `"age\|lte": "18"` |
| `gt` | 大于 | `"age\|gt": "18"` |
| `gte` | 大于等于 | `"age\|gte": "18"` |
| `startwith` | 以...开头 | `"name\|startwith": "张"` |
| `endwith` | 以...结尾 | `"name\|endwith": "三"` |
| `contains` | 包含 | `"name\|contains": "三"` |
| `in` | 在集合内 | `"type\|in": "1,2,3"` |
| `nin` | 不在集合内 | `"type\|nin": "4,5"` |
| `bt` | 介于之间 | `"age\|bt": "10,20"` |
| `nil` | 空值检查 | `"memo\|nil": "1"` |

## 复杂逻辑查询

使用 `@b` 可以表达嵌套的 `AND / OR` 组合：

```json
{
  "platform_user": {
    "@b": [
      {
        "or": [
          { "loginName|eq": "admin" },
          { "phone|eq": "13800000000" }
        ]
      },
      {
        "and": [
          { "status|eq": "1" }
        ]
      }
    ]
  }
}
```

它表示：

```text
(loginName = 'admin' OR phone = '13800000000') AND status = '1'
```

## 关联字段查询

在 `@fs` 中可以通过 `ref(...)` 读取关联表字段：

```json
{
  "platform_user": {
    "@fs": "id,name,ref(platform_org->orgName)",
    "orgId|eq": "some_org_id"
  }
}
```

这表示基于关联关系取出 `platform_org` 中的 `orgName` 字段。

## 保存语法

接口 `/api/meta/save/{biz}` 会自动判断是新增还是更新。

自动判断规则：

- 请求数据里包含 `id` 且未设置 `forceId`，按更新处理
- 请求数据里不包含 `id`，或包含 `id` 但同时设置了 `forceId`，按新增处理

新增示例：

```json
{
  "platform_user": {
    "name": "NewUser",
    "loginName": "new_user",
    "password": "123",
    "type": 1
  }
}
```

更新示例：

```json
{
  "platform_user": {
    "id": "1234567890",
    "name": "UpdatedName"
  }
}
```

默认行为：

- 更新时自动补 `updateAt`、`updater`、`updaterName`
- 新增时自动补 `createAt`、`creator`、`tenantCode` 等默认字段

## 嵌套保存

MQL 支持主实体与子实体一次性级联保存，子实体键名以 `#` 开头：

```json
{
  "platform_user": {
    "name": "UserWithRoles",
    "loginName": "u_roles",
    "#platform_user_role": [
      { "roleId": "role_admin_id" },
      { "roleId": "role_guest_id" }
    ]
  }
}
```

## 批量保存

接口：`/api/meta/batchSave`

```json
{
  "platform_user": [
    { "name": "UserA", "loginName": "a" },
    { "name": "UserB", "loginName": "b" }
  ]
}
```

## 删除语法

按 ID 删除：

- 单个：`/api/meta/delete/1/12345`
- 多个：`/api/meta/delete/1/12345,67890`

按条件删除接口：`/api/meta/delete2/{biz}`

```json
{
  "platform_user": {
    "loginName|eq": "temp_user",
    "status|eq": "0"
  }
}
```

这类删除请求应谨慎使用，必须保证过滤条件准确。

## 函数与表达式

MQL 支持在查询和保存中使用函数表达式。

基本语法：

- 函数格式：`functionName(param1, param2, ...)`
- 支持引用实体字段：`$实体名.字段名`
- 支持字面量：数字直接写，字符串使用单引号

### `increment`

用于递增或构造计算列：

```json
{
  "platform_user": {
    "@fs": "id,name,increment($platform_user.loginCount,1) loginCountNext"
  }
}
```

更新登录次数：

```json
{
  "platform_user": {
    "id": "123",
    "loginCount": "increment($platform_user.loginCount,1)"
  }
}
```

### `findinset`

用于检查值是否出现在集合字段中，也可以使用更贴近业务的 `fis` 操作符。

推荐写法：

```json
{
  "platform_user": {
    "roleCodes|fis": "admin,manager"
  }
}
```

函数写法：

```json
{
  "platform_user": {
    "findinset($platform_user.roleCodes,'admin')|gt": "0"
  }
}
```

### `fuzzymatch`

用于字符串模糊匹配，并可与排序组合使用：

```json
{
  "platform_user": {
    "fuzzymatch($platform_user.name,'张三')|gt": "0",
    "@order": "fuzzymatch($platform_user.name,'张三')|-"
  }
}
```

## 内置变量

MQL 内置了几类常用变量：

- `$ctx.*`：会话上下文变量，例如 `$ctx.userId`
- `$fn.nowDate`、`$fn.nowDateTime`：当前日期和时间
- `$parent.*`：嵌套保存时引用父命令生成的值

示例：

```json
{
  "platform_user": {
    "lastLoginAt": "$fn.nowDateTime",
    "updater": "$ctx.userId"
  }
}
```

## 响应结构

列表查询通常返回 `ApiPagedResult`：

```json
{
  "code": "200",
  "msg": "操作成功",
  "data": [
    { "id": "...", "name": "...", "createAt": "..." }
  ],
  "page": 1,
  "size": 10,
  "total": 100,
  "dataSize": 10,
  "meta": []
}
```

保存或删除通常返回 `ApiResult`：

```json
{
  "code": "200",
  "msg": "操作成功",
  "data": "123456789"
}
```

## 推荐继续阅读

- [MQL 总览](overview.md)
- [API 参考](../api/reference.md)
- [Fluent DSL 指引](../orm/fluent-dsl.md)
