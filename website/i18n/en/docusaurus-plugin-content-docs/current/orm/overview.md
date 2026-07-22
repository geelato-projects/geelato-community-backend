# ORM

ORM stands for Object-Relational Mapping. In Geelato Framework, it is the operational layer between programming-language objects and relational databases such as MySQL and PostgreSQL.

In practice, Geelato does not expose ORM as a single monolithic API. It is a capability system built around metadata, JSON protocol access, Java DSL access, and extension hooks:

- annotation layer: declares how Java objects map to entities, tables, and columns
- protocol layer: uses `MetaController + MQL` for frontend and platform-side data access
- Java API layer: uses `MetaFactory + Fluent DSL` for backend service code
- extension layer: uses events, dynamic datasource, and SPI-based rule injection for platform-specific behavior

## Current State

The current Geelato ORM state can be summarized as:

- it is not a heavyweight JPA/Hibernate-style stateful ORM
- it is not only a thin table-mapping helper either
- it is a metadata-centered data access system
- different entry points serve different runtime layers

The recommended mental model is:

- `@Entity / @Col / @Title / @Transient` answer how objects map to relational structures
- `MQL` answers how platform-side JSON requests describe query and save behavior
- `Fluent DSL` answers how backend Java code performs CRUD and light advanced querying
- events, dynamic datasource, and SPI extensions answer how platform rules are injected into the execution path

## Purpose

This ORM system is designed to:

- keep Java objects, relational tables, columns, and metadata titles declared in one place
- let frontend protocol flow and backend Java flow reuse the same metadata model
- reduce repeated DAO boilerplate, scattered SQL assembly, and direct MQL JSON construction in services
- centralize dynamic datasource, view parameters, value references, default field filling, and query-rule injection
- move platform rules out of low-level CRUD and expose them through events or SPI extensions

## Four Entry Types

### 1. ORM Annotations

Annotations answer: what is the entity?

They define:

- which entity name and table a Java class maps to
- which column a field maps to
- which properties stay transient
- which titles and descriptions are attached to entities and fields

See [ORM Annotations](annotations.md).

### 2. MQL

MQL answers: how does the platform-side JSON protocol access data?

It mainly targets:

- frontend pages
- platform-wide generic data APIs
- low-code or JSON-driven scenarios

It typically includes capabilities such as:

- `@fs` field selection
- `@p` pagination
- `@order` sorting
- `@group` grouping
- `@b` nested boolean logic
- `@pf` view template parameters
- `ref(...)` referenced fields
- `$ctx.* / $fn.* / $parent.*` built-in variables

MQL is closely related to ORM, but it is a platform protocol rather than the backend Java DSL.

See [MQL Overview](../mql/overview.md) and [MQL Usage](../mql/usage.md).

### 3. Fluent DSL

The Fluent DSL answers: how does backend Java service code access data?

It mainly targets:

- server-side service code
- standard CRUD in Java
- light join, pagination, aggregation, and procedure use cases
- scenarios that want to reuse datasource switching, view parameters, and value references

See [Fluent DSL Guide](fluent-dsl.md).

### 4. Advanced Features and Extension Hooks

Advanced features answer: how are platform rules injected beyond standard CRUD?

Current extension areas include:

- ORM events around save and delete
- dynamic datasource switching
- query-filter SPI for tenant, permission, or organization rules
- field-filling SPI for audit and default values

See:

- [ORM Event Features](event-features.md)
- [ORM / Datasource Extension](datasource-extension.md)
- [Query Filter and Field Fill SPI](../reference/spi-query-filter-and-save-fill-extension.md)

## Recommended Scope

Use the Geelato ORM system first when:

- backend services need standard CRUD by entity name or entity class
- platform APIs or frontend requests need JSON-based metadata access
- queries need pagination, sorting, light joins, lightweight aggregation, or lightweight procedure support
- the service wants to reuse datasource switching, view parameters, default filling, or value references

Do not force it when:

- the query is SQL-first and depends on recursive CTEs, window functions, or very complex filtering
- the scenario depends on multi-result-set procedures or mapping-heavy MyBatis behavior
- the team already intentionally owns the final SQL text

## Relationship to Other Data Access Options

- `MetaFactory + Fluent DSL`: preferred for backend Java metadata CRUD
- `MetaController + MQL`: preferred for platform data APIs and frontend-facing protocol flow
- `MetaFactory.sql(...)`: preferred when the team already owns the final SQL and only wants the execution chain
- MyBatis / native SQL: still the better fit for highly complex SQL and mapping-heavy cases

## Suggested Reading Order

1. Read [ORM Annotations](annotations.md) first
2. Continue with [MQL Overview](../mql/overview.md)
3. Continue with [Fluent DSL Guide](fluent-dsl.md)
4. Then read [ORM Event Features](event-features.md) and [ORM / Datasource Extension](datasource-extension.md)
5. Finally read [Query Filter and Field Fill SPI](../reference/spi-query-filter-and-save-fill-extension.md) and [Core Modules](../reference/core-modules.md)
