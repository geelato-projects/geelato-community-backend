# Message Module

File:

- `properties/message.properties`

## Purpose

This file contains:

- message scheduling settings
- message datasource settings
- RabbitMQ connection settings

## Key Areas

- `geelato.message.schedule.*`
- `spring.datasource.message.*`
- `spring.datasource.message.hikari.*`
- `spring.rabbitmq.*`

## Notes

- set `GEELATO_MESSAGE_JDBCURL` explicitly when the message module uses its own database
- replace the default RabbitMQ guest credentials in production
- tune processing interval and batch size according to throughput needs
