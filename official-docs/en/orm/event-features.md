# ORM Event Features

This page explains the built-in ORM event mechanism in Geelato Framework and how developers can use it to add their own custom logic around save and delete flows.

The current event capability lives in:

- `cn.geelato.core.orm.event`

It is not a standalone message bus and not just a thin wrapper over Spring ApplicationEvent. It is a lightweight hook mechanism inside the ORM execution chain itself.

## What Problem It Solves

The purpose of the ORM event mechanism is to decouple generic CRUD execution from business-specific side effects.

It is suitable for scenarios such as:

- adding extra validation or values before save
- blocking or guarding delete before execution
- synchronizing a mirror table after save
- writing audit logs, sending notifications, or refreshing cache after save
- cleaning downstream state, maintaining side indexes, or doing async compensation after delete

With this model, developers do not need to modify `Dao` directly or duplicate the CRUD flow. They only need to attach listeners.

## Current Event Types

The current ORM event system has two major groups:

- save events
- delete events

Each group has two timing points:

- `Before`
- `After`

So the current extension points are:

- `BeforeSaveEventListener`
- `AfterSaveEventListener`
- `BeforeDeleteEventListener`
- `AfterDeleteEventListener`

## Trigger Points

Events are triggered inside the actual `Dao` save, batch save, multi save, delete, and multi delete flows.

That means whether the write operation starts from:

- `Dao.save(...)`
- `Dao.batchSave(...)`
- `Dao.multiSave(...)`
- `Dao.delete(...)`
- `Dao.multiDelete(...)`

or from a higher-level Fluent DSL path that eventually lands in these ORM write paths, the event mechanism is still part of the execution chain.

## Execution Model

### Before Events

`Before` events run synchronously.

That means they run:

- before the real SQL execution
- with the ability to modify `BoundSql`
- with the ability to validate
- with the ability to throw exceptions and stop the main flow

If a before-listener throws an exception, the current save or delete operation fails immediately.

So `Before` events are best for:

- normalization
- SQL adjustment
- permission or state validation
- hard pre-check interception

### After Events

`After` events run asynchronously.

The current implementation uses a fixed thread pool:

- save event thread name prefix: `save-event-*`
- delete event thread name prefix: `delete-event-*`

The default pool size is:

- `4`

So `After` events mean:

- the main SQL has already finished
- the listener is scheduled asynchronously
- listener exceptions are logged, but they do not break the main flow

This makes them suitable for:

- audit logging
- mirror-table synchronization
- cache refresh
- non-critical notifications
- async side processing

## What the Event Context Contains

### Save Event Context `SaveEventContext`

The save-event context currently contains:

- `Dao dao`
- `SessionCtx sessionCtx`
- `IdEntity entity`
- `BoundSql boundSql`
- `SaveCommand command`
- `Map<String, Object> resultValueMap`
- `String eventId`
- `long startTime`

Meaning:

- `dao`: lets the listener reuse ORM execution capability
- `sessionCtx`: shared session-level context of the save flow
- `entity`: direct entity object when the entry path is entity save
- `boundSql`: the final SQL and parameters for this write
- `command`: ORM save command information such as entity name and values
- `resultValueMap`: the result values after the main save finishes
- `eventId`: unique ID for log correlation
- `startTime`: event start timestamp for timing and diagnostics

### Delete Event Context `DeleteEventContext`

The delete-event context currently contains:

- `Dao dao`
- `SessionCtx sessionCtx`
- `BoundSql boundSql`
- `DeleteCommand command`
- `int affectedRows`
- `String eventId`
- `long startTime`

It is mainly used for:

- viewing or adjusting delete SQL
- reading delete-command metadata
- checking affected row count after delete
- tracing this delete event in logs or compensation flows

## How to Read the Listener Interfaces

### `SaveEventListener`

The base save-listener interface defines:

- `beforeSave(SaveEventContext context)`
- `afterSave(SaveEventContext context)`
- `supports(...)`
- `enabled(...)`

Important detail:

- `supports(...)` returns `false` by default
- `enabled(...)` returns `false` by default

So if a developer does not override these methods explicitly, the listener may be registered but still never execute.

### `DeleteEventListener`

The base delete-listener interface defines:

- `beforeDelete(DeleteEventContext context)`
- `afterDelete(DeleteEventContext context)`
- `supports(...)`
- `enabled(...)`

Again:

- listeners are not enabled automatically by default
- they must explicitly declare both support and enabled behavior

## How Listeners Are Registered

The current model does not rely on Spring auto-discovery. It uses static event managers.

Save events use:

- `SaveEventManager.registerBefore(...)`
- `SaveEventManager.registerAfter(...)`
- `SaveEventManager.registerBeforeIfAbsent(...)`
- `SaveEventManager.registerAfterIfAbsent(...)`

Delete events use:

- `DeleteEventManager.registerBefore(...)`
- `DeleteEventManager.registerAfter(...)`
- `DeleteEventManager.registerBeforeIfAbsent(...)`
- `DeleteEventManager.registerAfterIfAbsent(...)`

The managers also support:

- unregistering listeners
- clearing listeners
- replacing the executor with `setExecutor(...)`

This means the current event model is:

- globally registered
- process-local
- bounded by the current JVM

## Built-In Example: Readonly Shadow Table Listener

The current save-event manager registers one built-in example listener:

- `ReadonlyShadowTableListener`

Its purpose is to mirror save SQL into a corresponding:

- `*_readonly`

shadow table.

For example:

- `insert into xxx (...)`
- `update xxx set ...`

can be rewritten as:

- `insert into xxx_readonly (...)`
- `update xxx_readonly set ...`

However, the internal switch is currently disabled by default:

- `READONLY_EVENT_ENABLED = false`

So it works more as a reference implementation that shows how the event mechanism can support:

- readonly mirror synchronization
- side-write behavior
- data shadow maintenance

## What Developers Can Customize

### Scenario 1: Validate Before Save

If you want to validate domain rules before the write happens, implement:

- `BeforeSaveEventListener`

Typical uses:

- blocking updates in certain states
- validating cross-field constraints
- enriching values before persistence

Example:

```java
public class CustomerBeforeSaveListener implements BeforeSaveEventListener {
    @Override
    public void beforeSave(SaveEventContext context) {
        if ("crm_customer".equalsIgnoreCase(context.getCommand().getEntityName())) {
            Object code = context.getCommand().getValueMap().get("code");
            if (code == null || String.valueOf(code).isBlank()) {
                throw new IllegalArgumentException("customer code must not be empty");
            }
        }
    }

    @Override
    public void afterSave(SaveEventContext context) {
    }

    @Override
    public boolean supports(SaveEventContext context) {
        return context.getCommand() != null;
    }

    @Override
    public boolean enabled(SaveEventContext context) {
        return true;
    }
}
```

### Scenario 2: Async Side Processing After Save

If you want non-blocking side effects after the main save succeeds, implement:

- `AfterSaveEventListener`

Typical uses:

- writing audit logs
- refreshing cache
- pushing change notifications
- synchronizing search indexes

Example:

```java
public class CustomerAfterSaveListener implements AfterSaveEventListener {
    @Override
    public void beforeSave(SaveEventContext context) {
    }

    @Override
    public void afterSave(SaveEventContext context) {
        if ("crm_customer".equalsIgnoreCase(context.getCommand().getEntityName())) {
            System.out.println("customer changed, eventId=" + context.getEventId());
        }
    }

    @Override
    public boolean supports(SaveEventContext context) {
        return context.getCommand() != null;
    }

    @Override
    public boolean enabled(SaveEventContext context) {
        return true;
    }
}
```

### Scenario 3: Block or Guard Delete

If you want to protect delete operations before execution, implement:

- `BeforeDeleteEventListener`

Typical uses:

- blocking deletion of built-in system data
- checking downstream references
- rewriting physical delete into a guarded strategy

### Scenario 4: Cleanup After Delete

If you want to do cleanup after delete, implement:

- `AfterDeleteEventListener`

Typical uses:

- cache cleanup
- index cleanup
- side-table cleanup
- delete audit logging

## Recommended Integration Style

It is better to centralize listener registration in one explicit startup location instead of registering listeners in many scattered places.

Examples:

- application startup initialization
- one dedicated ORM configuration class
- one shared base module initialization entry

This helps keep:

- registration order predictable
- duplicate registration under control
- enable/disable behavior easier across environments

## Usage Notes

### 1. `Before` Can Break the Main Flow

If a before-listener fails, the main save or delete fails too.

So `Before` should be used for:

- hard constraints
- mandatory validation
- must-succeed preconditions

### 2. `After` Is Not for Strong Transaction Semantics

`After` listeners run asynchronously, so they should not be the only place for logic that must stay strongly consistent with the main transaction.

If the logic must remain fully aligned with the main write transaction, it is better to:

- handle it before execution
- or orchestrate it explicitly in the service layer transaction flow

### 3. Mind Thread and Context Boundaries

Because `After` listeners run in a thread pool, developers should not assume they can freely rely on:

- thread-local state
- web request context
- security context that was never explicitly propagated

If the listener needs such information, it should read it from the event context or make it explicit before the event is dispatched.

### 4. Mind Duplicate Registration

Although `register*IfAbsent(...)` exists, duplicate behavior can still happen if different modules create different listener instances and register them repeatedly.

A better approach is:

- centralize listener instances
- centralize registration

## Summary

The current ORM event mechanism in Geelato Framework is a built-in extension point around `Dao` save and delete flows:

- before-events run synchronously and fit validation or interception
- after-events run asynchronously and fit notifications or side processing
- context objects expose SQL, command, result, and session information to listeners
- developers can use the listener abstraction to implement custom audit, mirror, cache, index, and validation logic

So it works especially well as:

- a unified ORM extension mechanism
- a reusable domain hook shared across business modules
- a customization entry that avoids direct intrusion into core CRUD implementation

## Suggested Reading

- [ORM Overview](overview.md)
- [Fluent DSL Guide](fluent-dsl.md)
- [Core Modules](../reference/core-modules.md)
