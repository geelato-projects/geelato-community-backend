# Workflow Module

File:

- `properties/workflow.properties`

## Purpose

This file contains:

- workflow feature switch
- workflow datasource settings
- platform datasource bridge settings
- Druid monitor settings
- Activiti engine settings

## Key Areas

- `geelato.workflow`
- `spring.datasource.workflow.*`
- `spring.datasource.platform.*`
- `spring.datasource.druid.*`
- `spring.activiti.*`

## Notes

- disable `geelato.workflow` when workflow is not needed
- distinguish workflow DB from platform DB clearly
- replace the default Druid monitor username and password in production

## Suggested Reading

- [System Configuration](overview.md)
