# MQL Usage Guide

This page explains how MQL is used in Geelato Framework for query, save, delete, and expression-based data access on the platform side.

## Core Endpoints

The main MQL handler is `MetaController`, which exposes the following endpoints:

| Action | Path | HTTP Method | Description |
| :--- | :--- | :--- | :--- |
| List query | `/api/meta/list` | POST / GET | Query one entity with filtering, pagination, and sorting |
| Multi-list query | `/api/meta/multiList` | POST | Query multiple entity lists in one request |
| Single save | `/api/meta/save/{biz}` | POST | Save one entity payload |
| Batch save | `/api/meta/batchSave` | POST | Save multiple rows of the same entity |
| Multi-entity save | `/api/meta/multiSave` | POST | Save multiple entities in one request |
| Delete by ID | `/api/meta/delete/{biz}/{id}` | POST | Delete by one or more IDs |
| Conditional delete | `/api/meta/delete2/{biz}` | POST | Delete by MQL filter conditions |

## Query Syntax

A typical query payload looks like this:

```json
{
  "platform_user": {
    "loginName": "admin"
  }
}
```

This means querying `platform_user` with `loginName = admin`.

## Common Keywords

MQL uses keywords prefixed with `@` to control query behavior.

| Keyword | Meaning | Example |
| :--- | :--- | :--- |
| `@fs` | field selection | `"@fs": "id,name,loginName"` |
| `@p` | pagination `(page,size)` | `"@p": "1,10"` |
| `@order` | sorting | `"@order": "createAt|+,name|-"` |
| `@group` | grouping | `"@group": "type"` |
| `@b` | nested boolean logic | see example below |
| `@pf` | view SQL template parameters | see example below |

## Filter Operators

MQL supports `"field|operator": "value"` syntax. If the operator is omitted, it defaults to `eq`.

| Operator | Meaning | Example |
| :--- | :--- | :--- |
| `eq` | equals | `"name": "Tom"` |
| `neq` | not equals | `"status\|neq": "0"` |
| `lt` | less than | `"age\|lt": "18"` |
| `lte` | less than or equal | `"age\|lte": "18"` |
| `gt` | greater than | `"age\|gt": "18"` |
| `gte` | greater than or equal | `"age\|gte": "18"` |
| `startwith` | starts with | `"name\|startwith": "Tom"` |
| `endwith` | ends with | `"name\|endwith": "son"` |
| `contains` | contains | `"name\|contains": "om"` |
| `in` | in set | `"type\|in": "1,2,3"` |
| `nin` | not in set | `"type\|nin": "4,5"` |
| `bt` | between | `"age\|bt": "10,20"` |
| `nil` | null check | `"memo\|nil": "1"` |

## Complex Boolean Logic

Use `@b` for nested `AND / OR` conditions:

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

This means:

```text
(loginName = 'admin' OR phone = '13800000000') AND status = '1'
```

## Referenced Field Query

You can use `ref(...)` in `@fs` to fetch fields from a related entity:

```json
{
  "platform_user": {
    "@fs": "id,name,ref(platform_org->orgName)",
    "orgId|eq": "some_org_id"
  }
}
```

This means reading `orgName` from the related `platform_org` entity.

## View Template Parameter `@pf`

When the queried entity itself is a view entity, you can pass:

- `@pf`

inside the entity node.

It is used for view SQL template rendering rather than normal field filtering.

For example, if the view SQL contains a fragment like:

```sql
#and order_type={orderType}#
```

the request can be written as:

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

Rules:

- `@pf` is extracted before standard MQL parsing, so it does not participate in normal keyword validation
- template rendering is applied only for view entities
- if `{orderType}` has a value, the fragment becomes `and order_type=123`
- if `{orderType}` is missing, `null`, or an empty string, the whole `#...#` fragment is removed
- parameters are replaced using raw value text and are not automatically quoted

So if you pass a string value, you should ensure the template and parameter content already match your intended SQL output.

## Save Syntax

The `/api/meta/save/{biz}` endpoint automatically decides between insert and update.

Rules:

- if the payload contains `id` and does not set `forceId`, it is treated as an update
- if the payload has no `id`, or has `id` together with `forceId`, it is treated as an insert

Insert example:

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

Update example:

```json
{
  "platform_user": {
    "id": "1234567890",
    "name": "UpdatedName"
  }
}
```

Default behavior:

- updates automatically fill `updateAt`, `updater`, and `updaterName`
- inserts automatically fill fields such as `createAt`, `creator`, and `tenantCode`

## Nested Save

MQL supports cascading save of a parent entity together with child entities. Child keys start with `#`.

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

## Batch Save

Endpoint: `/api/meta/batchSave`

```json
{
  "platform_user": [
    { "name": "UserA", "loginName": "a" },
    { "name": "UserB", "loginName": "b" }
  ]
}
```

## Delete Syntax

Delete by ID:

- single: `/api/meta/delete/1/12345`
- multiple: `/api/meta/delete/1/12345,67890`

Conditional delete endpoint: `/api/meta/delete2/{biz}`

```json
{
  "platform_user": {
    "loginName|eq": "temp_user",
    "status|eq": "0"
  }
}
```

Use this carefully and ensure the filter is accurate.

## Functions and Expressions

MQL supports function expressions in both query and save payloads.

Basic rules:

- function format: `functionName(param1, param2, ...)`
- entity fields can be referenced by `$entity.field`
- numbers can be written directly, while string literals use single quotes

### `increment`

Used for increment logic or computed fields:

```json
{
  "platform_user": {
    "@fs": "id,name,increment($platform_user.loginCount,1) loginCountNext"
  }
}
```

Increment during update:

```json
{
  "platform_user": {
    "id": "123",
    "loginCount": "increment($platform_user.loginCount,1)"
  }
}
```

### `findinset`

Used to check whether a value exists inside a set-like field. The `fis` operator is usually more convenient.

Recommended form:

```json
{
  "platform_user": {
    "roleCodes|fis": "admin,manager"
  }
}
```

Function form:

```json
{
  "platform_user": {
    "findinset($platform_user.roleCodes,'admin')|gt": "0"
  }
}
```

### `fuzzymatch`

Used for fuzzy string matching and can also be combined with sorting:

```json
{
  "platform_user": {
    "fuzzymatch($platform_user.name,'Tom')|gt": "0",
    "@order": "fuzzymatch($platform_user.name,'Tom')|-"
  }
}
```

## Built-In Variables

MQL provides several built-in variable families:

- `$ctx.*`: session context values such as `$ctx.userId`
- `$fn.nowDate` and `$fn.nowDateTime`: current date and time
- `$parent.*`: values generated from the parent command during nested save

Example:

```json
{
  "platform_user": {
    "lastLoginAt": "$fn.nowDateTime",
    "updater": "$ctx.userId"
  }
}
```

## Response Shape

List queries usually return `ApiPagedResult`:

```json
{
  "code": "200",
  "msg": "Success",
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

Save or delete requests usually return `ApiResult`:

```json
{
  "code": "200",
  "msg": "Success",
  "data": "123456789"
}
```

## Suggested Next Reading

- [MQL Overview](overview.md)
- [API Reference](../api/reference.md)
- [Fluent DSL Guide](../orm/fluent-dsl.md)
