# SSE Subscription Push

This page describes how the platform supports both:

- topic-based SSE subscription
- all-topic SSE subscription

The goal is to let clients subscribe to one business topic precisely, or observe all pushed topics from one aggregated stream.

## Current Capability

The platform already exposes a topic subscription endpoint:

- `GET /subscribe/{topic}`

It also reserves an all-topic endpoint:

- `GET /subscribe/topic/all`

The latter needs to be fully implemented so that it can return a working `SseEmitter` instead of staying as a placeholder.

## Design Goals

### Topic Subscription

Keep and complete:

- `GET /subscribe/{topic}`

Requirements:

- returns `text/event-stream`
- validates non-empty `topic`
- cleans up the subscription automatically on lifecycle end

### All-Topic Subscription

Implement:

- `GET /subscribe/topic/all`

Clients can consume it directly:

```javascript
const source = new EventSource('/subscribe/topic/all');
```

This allows one connection to receive events from all topics.

## Core Implementation Points

### Controller Layer

The controller should explicitly declare:

```java
produces = "text/event-stream"
```

and expose:

- topic subscription: `/subscribe/{topic}`
- all-topic subscription: `/subscribe/topic/all`

### Manager Layer

`SseEmitterManager` should add a global subscriber collection such as:

- `allSubscribers`

and support:

- `subscribeAll()`
- lifecycle cleanup callbacks
- removal of failed subscribers on send errors

### Broadcast Strategy

When `sendToTopic(topic, message)` is called, the message should be delivered to:

- subscribers of the given `topic`
- all-topic subscribers

This preserves current topic-based behavior while adding aggregated subscription naturally.

## Recommended Structure

### `SseEmitterManager`

Responsible for:

- managing topic -> emitters mapping
- managing the all-topic subscriber set
- registering `onCompletion`, `onTimeout`, and `onError`
- reclaiming invalid subscribers on send failure

### `SseHelper`

Provides lightweight wrapping methods such as:

- `subscribe(String topic)`
- `subscribeAll()`

to keep the calling style consistent.

### `SseController`

Exposes HTTP endpoints:

- `GET /subscribe/{topic}`
- `GET /subscribe/topic/all`

and returns `SseEmitter`.

## API Notes

### Topic Subscription

- path: `GET /subscribe/{topic}`
- return type: `text/event-stream`
- fit for listening to one business topic such as orders, notifications, or job status

Example:

```javascript
const source = new EventSource('/subscribe/news');
```

### All-Topic Subscription

- path: `GET /subscribe/topic/all`
- return type: `text/event-stream`
- fit for aggregated notifications, monitoring, or debugging

Example:

```javascript
const source = new EventSource('/subscribe/topic/all');
```

## Verification Suggestions

Even without running the full site, these checks are still recommended.

### Manager-Level Verification

Validate `SseEmitterManager` by:

- creating one topic subscriber
- creating one all-topic subscriber
- calling `sendToTopic(topic, message)`
- confirming that both subscription paths receive the delivery

### Code Review Checklist

- lifecycle callbacks clean up connections correctly
- send failure removes only the invalid subscriber
- empty collections are cleaned up to avoid memory growth

## Compatibility

This capability does not change the existing topic subscription path.

It only adds:

- all-topic subscription
- more complete cleanup and broadcast logic

So it is a compatible enhancement rather than a breaking change.
