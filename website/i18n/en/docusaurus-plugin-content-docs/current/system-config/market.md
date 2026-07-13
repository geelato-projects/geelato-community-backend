# Market Module

File:

- `properties/market.properties`

## Purpose

This file contains the Market module datasource and debug logging settings.

## Key Areas

- `spring.datasource.market.*`
- `spring.datasource.market.hikari.*`
- `logging.level.cn.geelato.market`
- `logging.level.cn.geelato.orm`
- `logging.level.org.springframework.jdbc`

## Notes

- set `GEELATO_MARKET_JDBCURL` explicitly when Market uses a dedicated database
- avoid keeping DEBUG logging enabled in production for long periods
