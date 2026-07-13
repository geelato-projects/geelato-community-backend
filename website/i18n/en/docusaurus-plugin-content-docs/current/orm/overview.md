# ORM

`geelato-orm` is the ORM module of Geelato Framework on the backend side. Its goal is not to be a heavyweight JPA-style persistence layer, but to provide a unified metadata-driven access path for server-side services.

This ORM chapter has two major parts:

- ORM annotations: define how Java classes are recognized as framework entities
- Fluent DSL: define how backend code queries and writes data through `MetaFactory`
- ORM events: provide extensible listener hooks around save and delete flows

## Role in the Framework

`geelato-orm` is used to:

- keep entity-to-table and field-to-column mapping consistent
- expose metadata-driven CRUD entry points for backend Java services
- reuse framework capabilities such as dynamic datasource switching, view parameters, default audit field filling, and value references
- attach custom audit, mirror, validation, cache, and side-processing logic to save and delete flows
- reduce repeated DAO boilerplate and direct MQL JSON construction in service code

## Recommended Scope

Use ORM first when:

- backend services need standard CRUD by entity name or entity class
- queries need pagination, sorting, light joins, or a few referenced fields
- the service wants to reuse `useDataSource(...)`, `viewParams(...)`, or `ValueRefs`

Do not force ORM when:

- frontend traffic still goes through `MetaController + MQL`
- the query is SQL-first and heavily depends on recursive CTEs, window functions, or very complex filtering
- the scenario depends on multi-result-set procedures or complex MyBatis `resultMap` behavior

## Three Parts of This Chapter

### ORM Annotations

Annotations answer the question: what is the entity metadata?

They define:

- which table a class maps to
- which column a field maps to
- which properties stay transient
- which business-facing titles or descriptions belong to entities and fields

See [ORM Annotations](annotations.md).

### Fluent DSL

The Fluent DSL answers the question: how does backend code access data?

It covers:

- querying one row or a page of rows
- insert, update, and delete
- `selectRef`, join, datasource switching, and procedure calls
- `ValueRefs.ctx/fn/parent` on the write path

See [Fluent DSL Guide](fluent-dsl.md).

### ORM Events

ORM events answer the question: what extra logic should run before or after save and delete?

Typical examples:

- validate domain rules before save
- write audit logs, notifications, or mirror-table updates after save
- block delete under certain constraints
- clear cache or side indexes after delete

See [ORM Event Features](event-features.md).

## Recommended Reading Order

1. Read [ORM Annotations](annotations.md) first
2. Continue with [Fluent DSL Guide](fluent-dsl.md)
3. Then read [ORM Event Features](event-features.md)
4. Then read [Core Modules](../reference/core-modules.md) for the framework-level position of `geelato-orm`

## Relationship to Other Data Access Options

- `MetaFactory + Fluent DSL`: preferred for backend Java metadata CRUD
- `MetaController + MQL`: preferred for platform data APIs and frontend-facing protocol flow
- `MetaFactory.sql(...)`: preferred when the team already owns the final SQL and only wants the execution chain
- MyBatis / native SQL: still the better fit for highly complex SQL and mapping-heavy cases
