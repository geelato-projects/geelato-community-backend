# PlatformWebRuntime

`PlatformWebRuntime` is the runtime-focused application layer for the web platform split.

## Positioning

It represents the subset that should stay available for business execution scenarios, such as:

- runtime-facing HTTP endpoints
- runtime deployment wiring
- business execution support
- selected operational capabilities

## What Belongs Here

- runtime authentication entry points
- file and attachment runtime access
- message, notice, resolve, OCR, AI, and SSE runtime-facing features
- shared runtime infrastructure and startup chain

## What Should Stay Out

- metadata definition management
- table and model design tooling
- script design and editing
- package and publish tooling
- admin-side design governance

## Runtime Rule

The runtime application should be deployable without requiring the full designer-side capability set.
