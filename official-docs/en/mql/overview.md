# MQL

MQL, short for Meta Query Language, is the JSON-based metadata query and mutation protocol used by Geelato Framework on the platform side.

Its purpose is not to replace backend Java ORM Fluent DSL usage, but to provide a unified data access language for frontend pages, low-code screens, and generic platform data endpoints.

## What MQL Solves

MQL is mainly used when:

- frontend pages need to query, save, or delete data by entity
- the platform wants generic data endpoints instead of one controller per entity
- query conditions need to travel between page configuration and runtime as JSON
- the runtime should reuse metadata, default field filling, nested save, and related core capabilities

## Protocol Position

The typical MQL entry is a group of generic endpoints exposed by `MetaController`.

It is best understood as:

- an internal platform data protocol
- a frontend-to-runtime metadata access language
- a JSON description of entity CRUD behavior

It is not the same as:

- the official external OpenAPI contract
- the backend Java Fluent DSL in `MetaFactory`
- a strongly typed MyBatis mapper layer

## Boundary with Fluent DSL

These two layers solve different problems:

- MQL: primarily for frontend and platform protocol flow, expressed in JSON
- Fluent DSL: primarily for backend Java services, expressed through chained `MetaFactory` calls

Recommended boundary:

- use MQL first for pages, generic platform controllers, and low-code configuration
- use Fluent DSL first for backend service CRUD, light joins, and procedure calls
- keep native SQL or MyBatis for very complex SQL and mapping-heavy scenarios

## Core Entry Points

The main MQL handler is `MetaController`, with endpoints such as:

- `/api/meta/list`
- `/api/meta/multiList`
- `/api/meta/save/{biz}`
- `/api/meta/batchSave`
- `/api/meta/multiSave`
- `/api/meta/delete/{biz}/{id}`
- `/api/meta/delete2/{biz}`

## What You Will See in MQL

MQL requests are JSON payloads built around entity names, protocol keywords, and condition expressions.

Typical features include:

- `@fs` for field selection
- `@p` for pagination
- `@order` for sorting
- `@group` for grouping
- `@b` for nested boolean logic
- `@pf` for view template parameter passthrough
- `ref(...)` for referenced fields
- functions such as `increment(...)`, `findinset(...)`, and `fuzzymatch(...)`
- built-in variables such as `$ctx.*`, `$fn.*`, and `$parent.*`

## View Template Parameter `@pf`

When the queried entity is a view entity, MQL can also carry:

- `@pf`

This is not used as a normal field filter. It is a parameter container for SQL template fragments inside the view definition.

A typical template fragment looks like:

```sql
#and order_type={orderType}#
```

The client can send:

```json
{
  "order_view": {
    "@pf": {
      "orderType": 123
    }
  }
}
```

Rules:

- if `@pf.orderType` has a value, `{orderType}` is replaced with the raw value text and the fragment body is kept
- if `@pf.orderType` is missing, `null`, or an empty string, the whole `#...#` fragment is removed
- non-view entities do not apply `@pf` template rendering and keep the existing behavior

So `@pf` should be understood as a view-query template parameter container rather than a generic MQL filter keyword.

## Suggested Reading Order

1. Start with [MQL Usage Guide](usage.md)
2. Then read [API Reference](../api/reference.md) for the OpenAPI and `SrvExplain` dual-track model
3. If you need backend Java-side data access, continue with [Fluent DSL Guide](../orm/fluent-dsl.md)
