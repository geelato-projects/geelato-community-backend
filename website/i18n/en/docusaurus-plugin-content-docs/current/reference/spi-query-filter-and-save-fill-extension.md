---
title: Query Filter and Field Fill SPI Extension
sidebar_label: Query Filter and Field Fill SPI
---

# Query Filter and Field Fill SPI Extension

This page explains how to extend Geelato through query filter SPI and save field fill SPI. These extension points are typically used for three kinds of tasks:

- inject platform-level filters such as tenant, permission, or organization constraints into query flows
- fill default values such as creator, updater, tenant code, or timestamps into save flows
- replace platform default rules in the host project instead of modifying the framework kernel directly

The common goal is to move platform rules out of `geelato-core / geelato-orm` and make them explicit SPI integrations in upper-layer projects.

## When This Page Fits

Start with this page if you want to:

- make MQL or Fluent DSL queries automatically include tenant, permission, or organization filters
- fill audit fields, tenant fields, or organization fields automatically during save
- plug custom platform rules into different host projects

This page is not the best starting point if you only need to:

- append one temporary condition inside a specific business method
- switch datasource for a single query
- listen to save events for notifications, sync jobs, or mirror writes

In those cases, read these first:

- [ORM / Datasource Extension](../orm/datasource-extension.md)
- [ORM Event Features](../orm/event-features.md)

## First Choose The Right Extension Type

Before writing code, separate the scenario clearly:

- affect query behavior: use a query filter SPI
- affect save behavior: use a field fill SPI
- affect MQL: choose the MQL SPI
- affect Fluent DSL: choose the Fluent DSL SPI
- affect entity save flow: choose the entity-save SPI

These entry points look similar, but they run at different positions in the execution chain. Picking the wrong one is the fastest way to end up with code that never runs.

## Architecture Boundary

The current responsibility split is fixed:

- `geelato-core / geelato-orm`
  - keep SPI contracts only
  - keep context objects and runtime resolvers only
  - do not hold platform default tenant, permission, or field-fill rules
- `geelato-web-platform`
  - holds platform default implementations
  - holds platform business rules

If your project needs different behavior, implement or replace the SPI at the host or platform layer instead of putting those rules back into the lower-level modules.

## Shortest Integration Path

If you only want to add one platform rule quickly, follow these four steps.

### Step 1: Pick The Entry Point

Use this mapping:

- MQL query: `MqlQueryFilterInjector`
- Fluent DSL query: `FluentQueryFilterInjector`
- MQL save: `MqlSaveFieldValueFiller`
- Fluent DSL save: `FluentSaveFieldValueFiller`
- entity save flow: `EntitySaveFieldValueFiller`

A practical rule of thumb:

- if the rule should affect frontend or platform common APIs, start from MQL
- if the rule should affect backend Java services, start from Fluent DSL
- if the rule should affect object-save parsing directly, use entity save

### Step 2: Implement One SPI Bean

Create a Spring Bean in the host project instead of editing lower-level modules first.

### Step 3: Keep Only One Enabled Implementation Per SPI Type

SPI types here are designed as "at most one enabled implementation", not "merge all matching beans together".

### Step 4: Verify With A Real Query Or Save Flow

Do not stop at successful Spring startup. Trigger a real query or save request and confirm the rule actually enters the execution chain.

## Query Filter SPI

Query filter SPI injects platform-level constraints into query flows, for example:

- tenant isolation
- data permission rules
- organization isolation
- default valid-record filtering

### Entry 1: MQL Query

MQL query uses:

- `cn.geelato.core.mql.spi.MqlQueryFilterInjector`
- `cn.geelato.core.mql.spi.support.MqlQueryFilterRuntimeResolver`

The corresponding flow is:

- `JsonTextQueryParser`
  - parses `QueryCommand`
  - calls `MqlQueryFilterRuntimeResolver.injectIfAvailable(command)`

Interface:

```java
public interface MqlQueryFilterInjector {
    boolean isEnabled();
    void inject(QueryCommand command);
}
```

Typical use cases:

- page list queries
- platform generic data APIs
- low-code configuration scenarios

### Entry 2: Fluent DSL Query

Fluent DSL query uses:

- `cn.geelato.orm.spi.FluentQueryFilterInjector`
- `cn.geelato.orm.spi.support.FluentQueryFilterRuntimeResolver`

The corresponding flow is:

- `QueryCommandAdapter`
  - adapts `MetaQuery` into `QueryCommand`
  - calls `FluentQueryFilterRuntimeResolver.injectIfAvailable(command, query)`

Interface:

```java
public interface FluentQueryFilterInjector {
    boolean isEnabled();
    void inject(QueryCommand command, MetaQuery query);
}
```

Typical use cases:

- backend Java service queries
- query flows started by `MetaFactory.query(...)`

### Platform Default Implementations

Current platform defaults are in `geelato-web-platform`:

- `PlatformMqlQueryFilterInjector`
- `PlatformFluentQueryFilterInjector`
- `PlatformQueryFilterSupport`

`PlatformQueryFilterSupport` holds the default tenant and permission rules. Those defaults are no longer embedded in lower-level modules.

## Save Field Fill SPI

Save field fill SPI fills default values during save flows, for example:

- creator
- create time
- updater
- update time
- tenant code
- organization fields

This capability is intentionally split into three SPI groups instead of one overly broad interface.

### Entry 1: MQL Save

- `cn.geelato.core.mql.spi.MqlSaveFieldValueFiller`
- `cn.geelato.core.mql.spi.support.MqlSaveFieldValueFillRuntimeResolver`

Invocation entry:

- `JsonTextSaveParser`

Interface:

```java
public interface MqlSaveFieldValueFiller {
    boolean isEnabled();
    void fill(MqlSaveFieldValueFillContext context);
}
```

Typical use cases:

- saves initiated by frontend MQL requests
- platform generic save APIs

### Entry 2: Fluent DSL Save

- `cn.geelato.orm.spi.FluentSaveFieldValueFiller`
- `cn.geelato.orm.spi.support.FluentSaveFieldValueFillRuntimeResolver`

Invocation entry:

- `SaveCommandAdapter`

Interface:

```java
public interface FluentSaveFieldValueFiller {
    boolean isEnabled();
    void fill(FluentSaveFieldValueFillContext context);
}
```

Typical use cases:

- backend Java service saves triggered by `MetaFactory.insert/update(...)`

### Entry 3: Entity Save Flow

- `cn.geelato.core.meta.spi.EntitySaveFieldValueFiller`
- `cn.geelato.core.meta.spi.support.EntitySaveFieldValueFillRuntimeResolver`

Invocation entry:

- `EntitySaveParser`

Interface:

```java
public interface EntitySaveFieldValueFiller {
    boolean isEnabled();
    void fill(EntitySaveFieldValueFillContext context);
}
```

Typical use cases:

- host projects that use direct entity-save parsing flows

### Platform Default Implementations

Current platform defaults are in `geelato-web-platform`:

- `PlatformMqlSaveFieldValueFiller`
- `PlatformFluentSaveFieldValueFiller`
- `PlatformEntitySaveFieldValueFiller`
- `PlatformFieldValueFillSupport`

`PlatformFieldValueFillSupport` keeps the default field rules, while still preserving differences between each entry point.

## How To Implement An SPI

Here are implementation patterns close to real usage.

### Scenario 1: Add Tenant Filtering For MQL Query

Typical cases:

- frontend list queries should always be tenant-isolated
- platform generic APIs should always append permission conditions

Minimal example:

```java
@Component
public class DemoMqlQueryFilterInjector implements MqlQueryFilterInjector {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void inject(QueryCommand command) {
        // Inject platform-level query conditions into QueryCommand here
    }
}
```

### Scenario 2: Add Platform Filters For Fluent DSL Query

Typical cases:

- backend Java service queries should automatically include tenant, permission, or organization constraints

Minimal example:

```java
@Component
public class DemoFluentQueryFilterInjector implements FluentQueryFilterInjector {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void inject(QueryCommand command, MetaQuery query) {
        // Inject platform-level conditions into the Fluent DSL query here
    }
}
```

### Scenario 3: Fill Default Fields For Fluent DSL Save

Typical cases:

- backend Java service saves should automatically fill creator, update time, or tenant code

Minimal example:

```java
@Component
public class DemoFluentSaveFieldValueFiller implements FluentSaveFieldValueFiller {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(FluentSaveFieldValueFillContext context) {
        // Fill default values for the save flow here
    }
}
```

### Scenario 4: Fill Default Fields For MQL Save

Typical cases:

- frontend or protocol-side saves should get unified default fields

Minimal example:

```java
@Component
public class DemoMqlSaveFieldValueFiller implements MqlSaveFieldValueFiller {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(MqlSaveFieldValueFillContext context) {
        // Fill default values for the MQL save flow here
    }
}
```

## Shared Runtime Rules

Both query filter SPI and save field fill SPI follow the same runtime rules:

1. when `0` implementations are found: skip
2. when `1` implementation is found: execute only if `isEnabled()` returns `true`
3. when multiple implementations are found: throw `IllegalStateException`

This matters a lot for troubleshooting.

The design intention is:

- no implicit fallback
- keep enablement visible
- avoid hidden stacking of multiple platform rules

That means you should not enable more than one of the same SPI type, such as:

- two `MqlQueryFilterInjector` implementations
- two `FluentQueryFilterInjector` implementations
- two `MqlSaveFieldValueFiller` implementations
- two `FluentSaveFieldValueFiller` implementations

If your project really has multiple candidates, merge the decision at the host layer and keep only one enabled implementation.

## Recommended Implementation Order

For a new project, this order usually works best:

1. identify which execution flow the rule should affect
2. choose the correct SPI interface
3. implement one minimal Bean
4. confirm there is only one enabled implementation of that SPI type
5. verify through a real query or save flow

When something fails, this sequence helps you quickly judge whether:

- the wrong SPI was chosen
- the Bean was not registered
- `isEnabled()` returned `false`
- multiple implementations were registered
- the real business flow never touched the entry point you expected

## Step-By-Step Troubleshooting

If an SPI "does not take effect", check in this order:

1. confirm whether the current request uses MQL, Fluent DSL, or entity save flow
2. confirm that your implementation matches that entry point
3. confirm the container contains only one bean of the same SPI type
4. confirm `isEnabled()` returns `true`
5. trigger a real query or save flow once
6. if you see `Multiple ... beans found`, inspect duplicate registrations first

For save field filling, also note:

- `SaveDefaultValueFiller / DefaultSaveDefaultValueFiller` now act as a compatibility layer
- the current primary extension entries are:
  - `MqlSaveFieldValueFiller`
  - `FluentSaveFieldValueFiller`
  - `EntitySaveFieldValueFiller`
- `BaseEntityMetaObjectHandler` currently remains as-is and is not the main entry of this SPI chain

## Extension Notes

1. do not put platform rules back into `geelato-core / geelato-orm`
2. query filters and field filling should stay framework-level reusable rules instead of scattered business patches
3. if a rule belongs to one entry point only, implement that specific SPI instead of forcing a unified mega-interface
4. if the logic is local to one API or one service, prefer the business layer instead of overusing SPI

## Relationship With Older Capability

`SaveDefaultValueFiller / DefaultSaveDefaultValueFiller` still exist in `geelato-orm`, but they are now compatibility-only and no longer the recommended primary extension point for `SaveCommandAdapter`.

For current save-flow extensions, prefer:

- `MqlSaveFieldValueFiller`
- `FluentSaveFieldValueFiller`
- `EntitySaveFieldValueFiller`

## Suggested Reading

- [ORM Overview](../orm/overview.md)
- [ORM / Datasource Extension](../orm/datasource-extension.md)
- [Fluent DSL Guide](../orm/fluent-dsl.md)
- [MQL Usage Guide](../mql/usage.md)
