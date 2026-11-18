---
title: API · Arco Components
sidebar_label: Arco Components
---
# Arco Components (arco)

1 controller with 1 endpoint in total. All endpoints in this domain are design-time APIs.

## ArcoController ⚙ — `/arco`

Arco form component support: returns select option data for a registered enum code.

### getSelectOptionData Get select option data

| Item | Content |
| --- | --- |
| URL | `GET /api/arco/sod/{code}` |
| Params | `code`: path, string, enum code (registered in `ArcoEnum`, case-insensitive). |
| Returns | `ApiResult<List<SelectOptionData>>`: `data` is the select option list (label/value, etc.); an empty list is returned when the code does not exist or the enum is invalid. |
