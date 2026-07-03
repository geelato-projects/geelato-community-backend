﻿# Geelato Framework Docs

This site is the first official documentation skeleton for `geelato-community`.

It consolidates the framework delivery path around `BOM + Starter + Sample + Runtime + Designer`, and turns scattered markdown notes into a product-facing entry point.

## What This Site Covers

- **Getting started** for framework consumers
- **Reference** for `geelato-framework-bom` and `geelato-framework-starter`
- **Runtime / Designer** split for the platform web layer
- **Unified authentication** and `lite-login` integration guidance
- **API dual track** with OpenAPI as the standard contract and `SrvExplain` as static supplementary documentation
- **Operations and governance** for long-term documentation maintenance

## Who It Serves

- Framework consumers who need a reliable onboarding path
- Platform maintainers who need boundary, lifecycle, and governance references
- API integrators who need discoverable contracts and examples

## Current Documentation Sources

The current first release reuses and organizes existing project assets:

- `docs/` for internal topic guides
- `SrvExplain/` for generated controller-level API descriptions
- `../geelato-hello-example/geelato-sample-quickstart/README.md` for the minimal framework sample

## First Reading Path

1. Read [Quick Start](guide/quick-start.md)
2. Read [BOM and Starter](reference/bom-and-starter.md)
3. Read [Sample Quickstart](guide/sample-quickstart.md)
4. If you need login integration, read [Unified Authentication](authentication/overview.md)
5. Read [API Reference](api/reference.md)

## Design Principle

This official site treats repository markdown as the source of truth and the website as the public publishing surface.


