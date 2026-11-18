---
title: API · Arco 组件
sidebar_label: Arco 组件
---
# Arco 组件（arco）

1 个 Controller，共 1 个端点。本域接口均为设计态接口。

## ArcoController ⚙ — `/arco`

Arco 表单组件支撑：按枚举码返回组件选择项数据。

### getSelectOptionData 获取选择项数据

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/arco/sod/{code}` |
| 参数 | `code`：path，string，枚举码（`ArcoEnum` 注册码，忽略大小写）。 |
| 返回 | `ApiResult<List<SelectOptionData>>`：data 为选择项列表（label/value 等）；枚举码不存在或对应枚举无效时返回空列表。 |
