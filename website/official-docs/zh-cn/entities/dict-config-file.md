---
title: 字典配置与文件
sidebar_label: 字典配置与文件
---

# 字典配置与文件

本页分两节：字典与系统配置（Dict、DictItem、SysConfig、Qrcode）、文件与附件（FileInfo、Attach、Compress、Resources）。文件与附件三个实体全部字段继承附件基类 Attachment（见[概览](overview.md#attachment附件域基类)）。

## 字典与系统配置

### Dict 数据字典

数据字典定义：一个字典一组业务枚举项，供表单/页面下拉等场景取用。继承 `BaseSortableEntity`，实现 `EntityEnableAble`，公共字段见[概览](overview.md#公共基类字段)。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 Id |
| dictName | dict_name | String | 字典名称 |
| dictNameEn | dict_name_en | String | 字典英文名称 |
| dictCode | dict_code | String | 字典编码 |
| dictColor | dict_color | String | 字典颜色 |
| dictRemark | dict_remark | String | 字典备注 |
| enableStatus | enable_status | int | 启用状态：1 启用、0 停用 |
| extraName | extra_name | String | 扩展字段 |
| extraValueType | extra_value_type | String | 扩展字段值类型 |
| extraContent | extra_content | String | 扩展字段内容 |
| dictItems | — | Set\<DictItem\> | `@Transient` 非落库，字典项集合 |

### DictItem 数据字典项

字典项定义，支持 pid 层级。继承 `BaseSortableEntity`，实现 `EntityEnableAble`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 Id |
| pid | pid | String | 父项 id，构成字典项层级 |
| dictId | dict_id | String | 字典 ID，关联 Dict |
| itemCode | item_code | String | 字典项编码 |
| itemName | item_name | String | 字典项文本 |
| itemNameEn | item_name_en | String | 英文名称 |
| itemColor | item_color | String | 字典项颜色 |
| itemTag | item_tag | String | 字典项标签 |
| itemRemark | item_remark | String | 描述 |
| enableStatus | enable_status | int | 启用状态：1 启用、0 停用 |
| itemExtra | item_extra | String | 扩展字段 |
| children | — | List\<DictItem\> | `@Transient` 非落库，子项树 |

### SysConfig 系统配置

应用参数配置：键值对形态的系统/应用配置，支持加密值。类级标题为"配置"。继承 `BaseSortableEntity`，实现 `EntityEnableAble`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 ID，为空时存 null（全局配置） |
| keyType | key_type | String | 键类型 |
| configKey | config_key | String | 配置键 |
| valueType | value_type | String | 值类型；为 `encrypt` 时该条配置为加密值 |
| configValue | config_value | String | 配置值 |
| enableStatus | enable_status | int | 启用状态：1 启用、0 停用 |
| remark | remark | String | 备注 |
| purpose | purpose | String | 用途 |
| encrypted | encrypted | boolean | 是否加密，由 `afterSet` 按 valueType=encrypt 推导，默认 false |
| sm2Key | sm2_key | String | 公私钥（SM2） |
| configAssist | — | String | `@Transient` 非落库 |

### Qrcode 二维码配置

二维码样式与内容配置：logo、形状、尺寸、颜色、码点/码眼、容错率等。继承 `BaseEntity`，实现 `EntityEnableAble`，未标注 catalog。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 id |
| title | title | String | 标题 |
| code | code | String | 编码 |
| enableStatus | enable_status | int | 状态：1 启用、0 未启用 |
| description | description | String | 描述 |
| logo | logo | String | logo base64 |
| logoShape | logo_shape | String | 形状：原图/矩形/圆形/圆角矩形 |
| logoSize | logo_size | String | 尺寸：2、4、7、11 |
| logoPosition | logo_position | String | 位置：中间/右下角 |
| logoProjection | logo_projection | Boolean | 投影：true/false |
| width | width | Integer | 宽度 |
| height | height | Integer | 高度 |
| lucency | lucency | Boolean | 背景是否透明 |
| codeColor | code_color | String | 码颜色 |
| backgroundColor | background_color | String | 码背景颜色 |
| codeDotShape | code_dot_shape | String | 码点形状 |
| codeEyeShape | code_eye_shape | String | 码眼形状 |
| codeInEyeColor | code_in_eye_color | String | 码内眼颜色（源码 @Title 误写"码外眼颜色"，按列名语义撰写） |
| codeOutEyeColor | code_out_eye_color | String | 码外眼颜色（源码 @Title 误写"码内眼颜色"，按列名语义撰写） |
| codePadding | code_padding | Integer | 码边距 |
| faultToleranceRate | fault_tolerance_rate | String | 容错率 |
| codeVersion | code_version | String | 码版本 |
| pictureFormat | picture_format | String | 图片格式：png/jpg/jpeg/gif |

## 文件与附件

### FileInfo 文件

文件记录。源码注明未引入：文件版本、文件关系。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| name | name | String | 名称 |
| savedName | saved_name | String | 保存名称，存储在磁盘的文件名称 |
| relativePath | path | String | 相对路径，一般相对于文件存储根目录（属性名 relativePath，列名 `path`） |
| size | size | int | 大小 |
| fileType | type | String | 文件类型（文件后缀） |
| description | description | String | 描述 |

### Attach 附件

附件表，无自有字段，全部字段继承附件基类 Attachment：pid/appId/name/type/genre/size/path/objectId/formIds/invalidTime/batchNo/resolution 等见[概览](overview.md#attachment附件域基类)。继承 `Attachment`。

### Compress 压缩文件

压缩包文件表，无自有字段，全部字段继承 Attachment。继承 `Attachment`，未标注 catalog。

### Resources 资源信息

静态资源文件表，无自有字段，全部字段继承 Attachment。继承 `Attachment`。

## 推荐继续阅读

- [文件处理](../file-processing/upload.md)——文件/附件的上传链路
- [系统配置](../system-config/overview.md)——SysConfig 承载的各模块配置项
