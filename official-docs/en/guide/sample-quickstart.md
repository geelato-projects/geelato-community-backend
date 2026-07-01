# Sample Quickstart

`geelato-sample-quickstart` is the official minimal sample application for framework consumers.

## Why It Matters

It proves that the framework foundation can run without pulling in the heavier platform extensions.

## What It Includes

- `geelato-framework-starter`
- H2 in-memory datasource
- a minimal Spring Boot application entry
- one sample runtime endpoint

## What It Deliberately Excludes

- message center
- market extensions
- schedule extensions
- auth-specific platform extensions
- platform upload runtime assumptions

## How to Use It

Use this sample as the first verification point when:

- testing starter consumption
- validating datasource and ORM auto-configuration
- diagnosing startup-chain issues without unrelated platform modules

## Source of Truth

The current operational instructions are maintained in:

- `../geelato-hello-example/geelato-sample-quickstart/README.md`

This official page keeps the product-facing explanation, while the sample README keeps the module-local operational notes.


