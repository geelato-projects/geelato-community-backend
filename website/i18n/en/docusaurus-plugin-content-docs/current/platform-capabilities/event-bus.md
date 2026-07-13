# Event Bus

This page explains the event-bus capability in:

- `cn.geelato.web.common.event`

inside `geelato-web-common`.

The current model has four core classes:

- `BusinessEvent`
- `EventPublisher`
- `GlobalEventBus`
- `EventBusListener`

Its goal is to decouple "what business action happened" from "what follow-up work should run afterwards".

## What It Is For

The event bus is suitable for:

- async follow-up work after a business action completes
- decoupling core business logic from notification, audit, or side synchronization
- attaching extension behavior without polluting controllers or main services
- serving as a trigger point for SSE, notifications, and audit logging

Compared with ORM events, this model is more focused on:

- web or application-layer events
- domain action completion
- async extension after business operations

instead of only DAO save/delete hooks.

## Current Structure

### `BusinessEvent`

This is the abstract base class of all business events.

It provides:

- `getEventCode()`
- `handle()`
- `sourceClass`
- `sourceMethod`

So one event object is both:

- a business event carrier
- a holder of its default handling action

### `EventPublisher`

This is the unified publishing entry:

- `EventPublisher.publish(event)`

It checks initialization, logs event metadata, and delegates actual publishing to `GlobalEventBus`.

### `GlobalEventBus`

This is the wrapper around Spring event broadcasting.

It depends on:

- `ApplicationEventMulticaster`

and publishes through:

- `eventMulticaster.multicastEvent(event)`

### `EventBusListener`

This is the built-in global listener.

It uses:

- `@EventListener`
- `@Async("eventExecutor")`

to receive `BusinessEvent` and invoke:

- `event.handle()`

So the current default semantics are:

- business code publishes the event
- Spring broadcasts it
- the event is handled asynchronously in the event executor

## How To Define Your Own Event

The recommended way is:

1. create a class that extends `BusinessEvent`
2. add the business fields you need
3. implement `getEventCode()`
4. implement the actual consumption logic in `handle()`

Example:

```java
public class OrderCreatedEvent extends BusinessEvent {
    private final String orderId;
    private final String userId;

    public OrderCreatedEvent(Object source, String orderId, String userId) {
        super(source);
        this.orderId = orderId;
        this.userId = userId;
    }

    @Override
    public String getEventCode() {
        return "order.created";
    }

    @Override
    public void handle() {
        // audit, notification, push, side synchronization, etc.
    }
}
```

## How To Publish

Once the event is defined, publish it from business code:

```java
EventPublisher.publish(new OrderCreatedEvent(this, orderId, userId));
```

Typical publish points include:

- after a controller completes a key action
- after a service completes a domain action
- after business success when follow-up work can be asynchronous

## How Subscription Works

In the current abstraction, the most direct subscription model is not multiple custom listeners first. The default model is:

- publish the event
- let the bus broadcast it
- let the built-in listener call `event.handle()`
- let the event define its own default handling logic

This is simple and practical, but if one event later needs many fully independent subscribers, you may need to split responsibilities further or extend the listener model.

## How To Combine It With SSE

The event bus and SSE fit together very naturally.

A recommended chain is:

1. a business action happens
2. publish a `BusinessEvent`
3. in `handle()`, convert the event into an SSE message
4. send it through `SseHelper` or `SseEmitterManager`
5. let the frontend receive it through `EventSource('/subscribe/{topic}')` or `/subscribe/topic/all`

Example structure:

```java
public class OrderCreatedEvent extends BusinessEvent {
    private final String orderId;

    public OrderCreatedEvent(Object source, String orderId) {
        super(source);
        this.orderId = orderId;
    }

    @Override
    public String getEventCode() {
        return "order.created";
    }

    @Override
    public void handle() {
        // convert the business event into an SSE push
        // SseHelper.sendToTopic("order", new SseMessage(...));
    }
}
```

This keeps:

- controller or service code free from direct SSE details
- business events separated from frontend transport decisions
- future replacement of SSE with another transport easier

## Recommended Boundary

Use this distinction:

- event bus: describes what happened inside the backend
- SSE: describes what should be pushed to connected clients

So the recommended flow is:

- publish a business event first
- decide in the event handling stage whether it should become an SSE push

## Usage Notes

- keep `getEventCode()` stable and readable, such as `order.created`
- avoid putting overly heavy long-running logic into `handle()`
- add proper logging and fallback when the event logic calls external systems
- let SSE stay downstream of business events, not the other way around

## Summary

The current event bus is a business-event abstraction built on Spring `ApplicationEventMulticaster`:

- `BusinessEvent` defines the event contract
- `EventPublisher` is the unified publish entry
- `GlobalEventBus` broadcasts the event
- `EventBusListener` consumes it asynchronously and invokes `handle()`

Developers can use it to:

- define their own business events
- publish them asynchronously after business completion
- implement audit, notification, synchronization, and SSE push logic in `handle()`

That makes it a good fit for:

- application-layer event extension
- backend internal propagation
- backend trigger entry for server-push via SSE

## Suggested Reading

- [SSE Subscription Push](sse-subscription.md)
- [Traffic Tagging](traffic-tagging.md)
- [Authentication and Authorization](../authentication/security-authentication.md)
