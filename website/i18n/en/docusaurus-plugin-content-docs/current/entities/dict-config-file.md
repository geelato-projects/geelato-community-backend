---
title: Dictionary, Config & Files
sidebar_label: Dictionary, Config & Files
---

# Dictionary, Config & Files

This page has two sections: dictionary and system config (Dict, DictItem, SysConfig, Qrcode), and files and attachments (FileInfo, Attach, Compress, Resources). The three attachment entities inherit all their fields from the attachment base class Attachment (see [Overview](overview.md#attachment-attachment-domain-base-class)).

## Dictionary and System Config

### Dict

Data dictionary definition: one dictionary holds a group of business enumeration items for form/page dropdowns and similar scenarios. Extends `BaseSortableEntity`, implements `EntityEnableAble`; common fields are listed in [Overview](overview.md#common-base-class-fields).

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id |
| dictName | dict_name | String | Dictionary name |
| dictNameEn | dict_name_en | String | Dictionary English name |
| dictCode | dict_code | String | Dictionary code |
| dictColor | dict_color | String | Dictionary color |
| dictRemark | dict_remark | String | Dictionary remark |
| enableStatus | enable_status | int | Enable status: 1 enabled, 0 disabled |
| extraName | extra_name | String | Extension field |
| extraValueType | extra_value_type | String | Extension field value type |
| extraContent | extra_content | String | Extension field content |
| dictItems | — | Set\<DictItem\> | `@Transient`, not persisted; set of dictionary items |

### DictItem

Dictionary item definition, with pid hierarchy. Extends `BaseSortableEntity`, implements `EntityEnableAble`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id |
| pid | pid | String | Parent item id, forming the item hierarchy |
| dictId | dict_id | String | Dictionary id, referencing Dict |
| itemCode | item_code | String | Item code |
| itemName | item_name | String | Item text |
| itemNameEn | item_name_en | String | English name |
| itemColor | item_color | String | Item color |
| itemTag | item_tag | String | Item tag |
| itemRemark | item_remark | String | Description |
| enableStatus | enable_status | int | Enable status: 1 enabled, 0 disabled |
| itemExtra | item_extra | String | Extension field |
| children | — | List\<DictItem\> | `@Transient`, not persisted; child item tree |

### SysConfig

Application parameter configuration: key-value pairs for system/application settings, with support for encrypted values. Class title is "配置" (config). Extends `BaseSortableEntity`, implements `EntityEnableAble`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id, stored as null when empty (global config) |
| keyType | key_type | String | Key type |
| configKey | config_key | String | Config key |
| valueType | value_type | String | Value type; `encrypt` marks an encrypted value |
| configValue | config_value | String | Config value |
| enableStatus | enable_status | int | Enable status: 1 enabled, 0 disabled |
| remark | remark | String | Remark |
| purpose | purpose | String | Purpose |
| encrypted | encrypted | boolean | Whether encrypted, derived by `afterSet` from valueType=encrypt, defaults to false |
| sm2Key | sm2_key | String | Public/private key (SM2) |
| configAssist | — | String | `@Transient`, not persisted |

### Qrcode

QR code style and content configuration: logo, shape, size, colors, dot/eye shapes, error tolerance, etc. Extends `BaseEntity`, implements `EntityEnableAble`, no catalog specified.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id |
| title | title | String | Title |
| code | code | String | Code |
| enableStatus | enable_status | int | Status: 1 enabled, 0 disabled |
| description | description | String | Description |
| logo | logo | String | logo base64 |
| logoShape | logo_shape | String | Shape: original/rectangle/circle/rounded rectangle |
| logoSize | logo_size | String | Size: 2, 4, 7, 11 |
| logoPosition | logo_position | String | Position: center/bottom-right |
| logoProjection | logo_projection | Boolean | Projection: true/false |
| width | width | Integer | Width |
| height | height | Integer | Height |
| lucency | lucency | Boolean | Transparent background |
| codeColor | code_color | String | Code color |
| backgroundColor | background_color | String | Code background color |
| codeDotShape | code_dot_shape | String | Dot shape |
| codeEyeShape | code_eye_shape | String | Eye shape |
| codeInEyeColor | code_in_eye_color | String | Inner eye color (source @Title mislabeled "outer eye color"; documented by column-name semantics) |
| codeOutEyeColor | code_out_eye_color | String | Outer eye color (source @Title mislabeled "inner eye color"; documented by column-name semantics) |
| codePadding | code_padding | Integer | Code padding |
| faultToleranceRate | fault_tolerance_rate | String | Error tolerance rate |
| codeVersion | code_version | String | Code version |
| pictureFormat | picture_format | String | Image format: png/jpg/jpeg/gif |

## Files and Attachments

### FileInfo

File record. The source notes that file versions and file relations are not yet introduced. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| name | name | String | Name |
| savedName | saved_name | String | Saved name, the file name on disk |
| relativePath | path | String | Relative path, usually relative to the file storage root directory (property relativePath, column `path`) |
| size | size | int | Size |
| fileType | type | String | File type (file suffix) |
| description | description | String | Description |

### Attach

Attachment table, no own fields; all fields are inherited from the attachment base class Attachment: pid/appId/name/type/genre/size/path/objectId/formIds/invalidTime/batchNo/resolution etc., see [Overview](overview.md#attachment-attachment-domain-base-class). Extends `Attachment`.

### Compress

Compressed-archive file table, no own fields; all fields inherited from Attachment. Extends `Attachment`, no catalog specified.

### Resources

Static resource file table, no own fields; all fields inherited from Attachment. Extends `Attachment`.

## Recommended Reading

- [File Processing](../file-processing/upload.md) — the upload pipeline for files and attachments
- [System Config](../system-config/overview.md) — the module configuration items carried by SysConfig
