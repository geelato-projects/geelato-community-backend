# 修复 EntityMeta 必填/唯一元数据丢失

## 根因
`geelato-core\src\main\java\cn\geelato\core\meta\model\column\ColumnMeta.java` 的 `ColumnMeta(Map<String,Object>)` 构造器(224-277 行)从 `platform_dev_column` 行 Map 装载时,遗漏了 `is_nullable`、`is_unique` 两个键,导致 DB 来源实体的 EntityMeta 中必填恒为 true(可空)、唯一恒为 false。

## 改动(单文件、两行)

在 `ColumnMeta(Map)` 构造器中(建议放在读取 `column_key` 的 247 行附近)补充:

```java
this.nullable = map.get("is_nullable") == null || Boolean.parseBoolean(map.get("is_nullable").toString());
this.uniqued = map.get("is_unique") != null && Boolean.parseBoolean(map.get("is_unique").toString());
```

- null 时取字段默认语义(nullable=true、uniqued=false),与类字段初始值一致
- 解析模式与同构造器既有 boolean 字段(column_key、auto_increment 等)一致
- 不调用 afterSet(),保持该构造器"仅做字段映射"的既定行为(注释中已声明)

## 验证
- 编译 geelato-core 模块
- 如可行,补一个最小单测:构造含 is_nullable=false / is_unique=true 的 Map,断言 ColumnMeta 的 isNullable()==false、isUniqued()==true(放在 geelato-core 已有测试目录,若无测试基础设施则以编译+人工核对为准)

## 影响面
- 受益:MetaManager.parseDBMeta() 加载的在线实体、视图实体,及下游 SimpleFieldMeta 发布、Excel 导入校验、代码生成、MCP 元数据工具
- Java 注解实体链路(MetaReflex.getEntityMeta(clazz))不受影响、无需改动