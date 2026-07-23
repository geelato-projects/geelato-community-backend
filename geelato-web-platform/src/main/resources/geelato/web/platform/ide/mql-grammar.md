# MQL（Meta Query Language）文法摘要

MQL 是 geelato 自研的基于 JSON 的元数据查询语言，由 `MetaQLManager` 解析为 `QueryCommand` → `BoundSql`（参数化 SQL）。

## 1. 查询语法（Query）

```json
{
  "entity_name": {
    "@fs": "field1,field2",
    "@p": "1,10",
    "@order": "create_at DESC",
    "field_name|operator": "value"
  }
}
```

### 关键字（`@` 开头）

| 关键字 | 含义 | 示例 |
|---|---|---|
| `@fs` | 字段选择（fields），逗号分隔 | `"@fs": "id,name,create_at"` |
| `@p` | 分页（pageNum,pageSize） | `"@p": "1,20"` |
| `@order` | 排序 | `"@order": "create_at DESC"` |
| `@group` | 分组 | `"@group": "dept_id"` |
| `@b` | having 过滤 | `"@b": "count|>|10"` |
| `@distinct` | 去重 | `"@distinct": true` |

### 过滤操作符（字段名 `|` 操作符）

格式：`"fieldName|op": "value"`

| 操作符 | 含义 | 示例 |
|---|---|---|
| `eq` 或无 | 等于 | `"status": "DRAFT"` 或 `"status|eq": "DRAFT"` |
| `ne` | 不等于 | `"status|ne": "ARCHIVED"` |
| `gt` / `lt` / `gte` / `lte` | 大于/小于/大于等于/小于等于 | `"amount|gt": "100"` |
| `in` | IN 列表 | `"id|in": "1,2,3"` |
| `like` | 模糊 | `"name|like": "%order%"` |
| `between` | 区间 | `"create_at|between": "2026-01-01,2026-12-31"` |
| `contains` | JSON 包含 | `"tags|contains": "vip"` |
| `isnull` | 为空 | `"description|isnull": true` |

### 子实体关联（`~` 前缀，仅 query）

```json
{
  "platform_user": {
    "@fs": "id,name",
    "~platform_user_role": {
      "@fs": "role_id",
      "user_id": "${id}"
    }
  }
}
```

`~<sub_entity>` 表示子表关联，子表用 `${parent_field}` 引用父字段。

## 2. 保存语法（Save）

```json
{
  "entity_name": {
    "field1": "value1",
    "field2": "value2",
    "#sub_entity": [
      { "sub_field": "value" }
    ]
  }
}
```

### 关键约定

- 子实体用 `#` 前缀（**query 用 `~`，save 用 `#`，两者不同**）
- 主键有值且无 `forceId` → UPDATE；主键无值或带 `forceId` → INSERT
- 函数字段：`"age": "${now()+1}"`，由 `FunctionParser` 识别
- 布尔字段：`"true"→1, "false"→0`
- 加密字段：开启 `GlobalContext.getColumnEncryptOption()` 时按列标记自动加密

### 批量与多实体

- `parseBatch(jsonText, ctx)`：值是数组
- `parseMulti(jsonText, ctx)`：顶层多个实体键，**值是 JSON 字符串而非对象**

## 3. 删除语法（Delete）

```json
{
  "entity_name": {
    "id|in": "1,2,3"
  }
}
```

## 4. 内置变量与函数

- `$ctx` - 当前会话上下文
- `$fn` - 函数命名空间
- `$parent` - 子实体中引用父字段
- `${now()}` - 当前时间
- `${currentUser()}` - 当前用户

## 5. 注意事项

1. SQL 是参数化的（`BoundSql` 持 `Object[] params`），防 SQL 注入
2. 默认按 `tenant_code` 多租户隔离
3. 软删除：默认过滤 `del_status = 0`
4. 语句数有限制（worker dry-run 默认 1000000）

## 6. 资源

- 完整说明：`document/MQL使用说明.md`
- 解析器源码：`geelato-community/geelato-core/src/main/java/cn/geelato/core/mql/parser/JsonTextQueryParser.java`
- 保存解析器：`JsonTextSaveParser.java`（javadoc L26-L110 有完整约定）
