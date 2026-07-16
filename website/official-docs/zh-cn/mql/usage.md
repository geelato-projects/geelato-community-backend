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

## 查询链路的 SPI 扩展

除了前端显式传入的过滤条件，MQL 查询链路还支持通过 SPI 注入平台级默认过滤规则。

- MQL 查询入口对应：`MqlQueryFilterInjector`
- 运行时解析器对应：`MqlQueryFilterRuntimeResolver`
- 典型用途包括：租户隔离、数据权限、组织维度过滤
- 当前平台默认实现位于 `geelato-web-platform`

如果你需要在宿主项目中替换或扩展这类规则，建议阅读：[查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)

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
| `@pf` | 视图 SQL 模板参数 | 见下文示例 |

## 字段选择 `@fs`

MQL 通过：

- `@fs`

控制查询结果返回哪些字段。

基本格式是：

```text
字段1,字段2,字段3
```

例如：

```json
{
  "platform_user": {
    "@fs": "id,name,loginName"
  }
}
```

这表示查询结果里只返回：

- `id`
- `name`
- `loginName`

### 结合关联字段

`@fs` 也支持：

- `ref(...)`

例如：

```json
{
  "platform_user": {
    "@fs": "id,name,ref(platform_org->orgName)"
  }
}
```

### 结合函数表达式

`@fs` 还支持函数或计算列，例如：

```json
{
  "platform_user": {
    "@fs": "id,name,increment($platform_user.loginCount,1) loginCountNext"
  }
}
```

### 使用建议

- 列表查询尽量显式指定 `@fs`
- 避免默认返回过多无关字段
- 如果要做前端表格展示，优先只返回页面真正需要的列

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

## 复杂逻辑 `@b`

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

## 分页 `@p`

MQL 通过：

- `@p`

控制列表分页。

基本格式是：

```text
页码,每页条数
```

例如：

```json
{
  "platform_user": {
    "@fs": "id,name,createAt",
    "@p": "1,10"
  }
}
```

这表示：

- 查询第 `1` 页
- 每页返回 `10` 条

### 与响应结构的关系

分页查询通常会返回：

- `page`
- `size`
- `total`
- `dataSize`

因此 `@p` 控制的是：

- 当前取哪一页
- 每页取多少条

而总条数和当前页实际返回条数，则由响应里的分页字段体现。

### 使用建议

- 前端列表页尽量总是显式传入 `@p`
- 推荐和 `@order` 一起使用，避免分页结果不稳定
- 如果不传分页参数，就更适合用于小结果集查询，而不是大表列表页

## 分组 `@group`

MQL 通过：

- `@group`

控制分组查询。

基本格式是：

```text
字段名
```

也可以是多个字段，使用逗号分隔。

### 单字段分组

例如按用户类型分组：

```json
{
  "platform_user": {
    "@fs": "type",
    "@group": "type"
  }
}
```

这表示：

```text
GROUP BY type
```

### 多字段分组

例如按租户和状态一起分组：

```json
{
  "platform_user": {
    "@fs": "tenantCode,status",
    "@group": "tenantCode,status"
  }
}
```

这表示：

```text
GROUP BY tenantCode, status
```

### 分组时的字段选择建议

使用 `@group` 时，`@fs` 里更应优先放：

- 分组字段本身
- 聚合函数结果

而不要随意选择大量未参与分组、也未聚合的普通字段。

### 与排序结合

分组查询也可以继续搭配：

- `@order`

例如：

```json
{
  "platform_user": {
    "@fs": "tenantCode,status",
    "@group": "tenantCode,status",
    "@order": "tenantCode|+,status|+"
  }
}
```

这适合做：

- 统计类列表
- 分组汇总结果排序
- 维度分析结果展示

## 排序 `@order`

MQL 通过：

- `@order`

控制结果集排序。

基本格式是：

```text
字段名|方向
```

其中方向约定为：

- `+`：升序
- `-`：降序

### 单字段排序

例如按创建时间倒序：

```json
{
  "platform_user": {
    "@fs": "id,name,createAt",
    "@order": "createAt|-"
  }
}
```

这表示：

```text
ORDER BY createAt DESC
```

### 多字段排序

多个排序项之间使用逗号分隔。

例如先按状态升序，再按创建时间倒序：

```json
{
  "platform_user": {
    "@fs": "id,name,status,createAt",
    "@order": "status|+,createAt|-"
  }
}
```

这表示：

```text
ORDER BY status ASC, createAt DESC
```

### 函数排序

`@order` 不只支持普通字段，也可以对函数表达式排序。

例如按模糊匹配得分倒序：

```json
{
  "platform_user": {
    "fuzzymatch($platform_user.name,'张三')|gt": "0",
    "@order": "fuzzymatch($platform_user.name,'张三')|-"
  }
}
```

这类写法适合：

- 搜索结果按命中度排序
- 计算列排序
- 动态表达式排序

### 使用建议

- 列表分页查询时，尽量总是显式指定 `@order`
- 多字段排序时，把更稳定的字段放在后面作为次级排序条件
- 如果排序字段允许重复值，建议补一个稳定字段，例如 `id` 或 `createAt`

## 视图模板参数 `@pf`

当查询实体本身是“视图实体”时，可以在实体节点内传入：

- `@pf`

它用于给视图 SQL 模板片段传参，而不是普通字段过滤。

例如视图 SQL 中存在模板片段：

```sql
#and order_type={orderType}#
```

那么请求可以写成：

```json
{
  "order_view": {
    "@fs": "id,orderNo,orderType",
    "@pf": {
      "orderType": 123
    }
  }
}
```

处理规则：

- `@pf` 会在进入标准 MQL 解析前被提取，不参与普通关键字校验
- 只有视图实体才会启用模板渲染
- 当 `{orderType}` 有值时，模板片段会渲染为 `and order_type=123`
- 当 `{orderType}` 缺失、为 `null` 或空串时，整段 `#...#` 会被移除
- 参数按原值文本直接替换，不自动补单引号

因此如果你传的是字符串值，需要自行保证模板和参数值都符合你的 SQL 预期。

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
- 这些默认字段现在通过 `MqlSaveFieldValueFiller` SPI 注入，而不是要求业务侧手工补字段
- 当前平台默认实现位于 `geelato-web-platform`，运行时同样遵循 `0` 个跳过、`1` 个按 `isEnabled()`、多实现直接报错
- 扩展方式与完整说明见：[查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)

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
- [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)
