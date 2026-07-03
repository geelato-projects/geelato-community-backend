# Monitor Module

File:

- `properties/monitor.properties`

## Purpose

This file contains auxiliary suite monitoring settings.

## Key Areas

- `geelato.monitor.auxiliary-suites.enabled`
- `geelato.monitor.auxiliary-suites.poll-interval-seconds`
- `geelato.monitor.auxiliary-suites.connect-timeout-seconds`
- `geelato.monitor.auxiliary-suites.read-timeout-seconds`
- `geelato.monitor.auxiliary-suites.suites-json`

## Notes

- tune polling frequency according to the number of monitored targets
- keep `suites-json` readable and maintainable
- use stable health endpoints for external targets
