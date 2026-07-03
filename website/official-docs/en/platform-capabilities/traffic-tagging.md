# Traffic Tagging

This page describes how the platform generates, resolves, and exposes a request-level `trafficTag` so it can later support gray release, filtering, forwarding, interception, and online-user observation.

The current plan starts with two common values:

- `default`
- `gray`

But the implementation should still allow extension to any valid tag string.

## Goals

Traffic tagging aims to:

- generate or recover a `trafficTag` for every request
- persist the tag in a cookie
- expose the tag through a response header
- keep the tag in server-side request context
- show the latest active `trafficTag` on the online-user card
- support filtering online users by `trafficTag`

## Rules

Current rules are:

- use both Cookie and Header as carriers
- allow manual override only in non-`product` environments
- sign the cookie value
- fall back and overwrite on signature validation failure
- provide tagging capability first, without implementing business-side traffic routing yet

## Recommended Structure

### `TrafficColoringProperties`

A dedicated configuration class should centralize:

- `enabled`
- `tagCookieName`
- `tagHeaderName`
- `overrideHeaderName`
- `overrideQueryName`
- `defaultTag`
- cookie path, domain, maxAge, httpOnly, secure
- `signingEnabled`
- `signingSecret`
- `mdcKey`
- `requestAttributeKey`

This keeps protocol details and runtime settings out of scattered business code.

### `TrafficTagContext`

Provide a request-scoped context holder for the current `trafficTag`, so it can be read consistently from:

- interceptors
- services
- async submission points

### `TrafficTagSigner`

Responsible for:

- generating signatures
- validating signatures
- validating tag format

A recommended choice is:

- `HmacSHA256`

with a strict tag pattern to prevent unsafe values from entering cookies, headers, or logs.

### `TrafficTagResolver`

Responsible for resolving, falling back, and issuing tags when a request arrives.

Recommended priority:

1. manual override in non-`product` environments
2. existing cookie with valid signature
3. default tag, then issue a new cookie

The final tag should be written to:

- `TrafficTagContext`
- request attribute
- MDC
- response header
- `Set-Cookie` when refresh or issue is needed

## Position in the Security Chain

The most suitable integration point is `DefaultSecurityInterceptor`.

### `preHandle`

Traffic tagging should run at the beginning of `preHandle`, before:

- the `@IgnoreVerify` early return path

This ensures even unauthenticated endpoints still receive a consistent `trafficTag`.

It should also guarantee:

- tagging failures only degrade gracefully and never block the normal security flow

### `afterCompletion`

At request completion, it should clear:

- `TrafficTagContext`
- the `trafficTag` entry in MDC

to prevent thread reuse from leaking context values across requests.

## Online User Display

Traffic tagging should also feed the online-user observation capability.

### Backend

Online user details should add:

- `trafficTag`

and Redis online-state records should store the tag from the most recent active request.

The target outcome is:

- each item returned by `/api/online/list` includes `trafficTag`

### Frontend

The online-user card should:

- display `trafficTag`
- use tag styles to distinguish `default` and `gray`
- provide filters for All / default / gray

Filtering only affects display and should not change polling or refresh behavior.

## Expected Interface Behavior

After a request passes through traffic tagging, the response should ideally include:

- `Set-Cookie: gl_traffic_tag=...`
- `X-Gl-Traffic-Tag: default|gray`

In a non-`product` environment, if the caller specifies:

- `X-Gl-Traffic-Override: gray`

or:

- `?glTrafficTag=gray`

the response should return:

- `X-Gl-Traffic-Tag: gray`

and write the matching cookie back.

## Verification Suggestions

### Static Checks

Focus on:

- tagging logic running before security early return
- cleanup in `afterCompletion`
- fallback to the default tag and cookie rewrite when signature validation fails

### Online-User Flow

Focus on:

- whether online-state updates include `trafficTag`
- whether `/api/online/list` returns `trafficTag`
- whether the frontend card can display and filter by `trafficTag`

## Current Boundary

At this stage, the goal is to provide a unified tagging capability and an observation capability, not to immediately implement gray-routing strategies inside every business service.

In other words, the current phase focuses on:

- how the tag is generated
- how the tag is exposed
- how the tag is stored
- how the tag is observed from the management side

Gateway routing, service forwarding, or business interception based on the tag can be extended in later phases.
