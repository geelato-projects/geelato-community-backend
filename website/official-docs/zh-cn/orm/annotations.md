# ORM 注解说明

这一页说明 Geelato Framework 当前在服务端开发中推荐使用的 ORM 注解，以及这些注解在实体声明中的职责边界。

## 作用概览

ORM 注解主要用于把 Java 类声明成框架可识别的元数据实体，并控制字段与数据库列、标题信息、非持久化属性之间的映射关系。

它解决的是“实体如何被框架识别”的问题，而不是“数据如何查询和写入”的问题。后者由 [Fluent DSL](fluent-dsl.md) 负责。

## 核心注解

### `@Entity`

`@Entity` 用于把类标注为实体对象，并映射到数据库表。

常见场景：

- 新增一个框架元数据实体
- 需要让平台扫描并识别该类
- 需要显式指定实体映射的表名

常用属性：

- `name`：指定数据库表名；未显式指定时，通常按类名做驼峰转蛇形推导

示例：

```java
@Entity(name = "platform_org")
public class Demo {
    private String name;
    private String code;
    private String pid;
    private String type;
    private String category;
    private int status;
    private String description;
}
```

说明：

- 被 `@Entity` 标记的类会被框架识别为实体
- 类中的普通属性默认参与字段映射
- 列名默认按属性名做驼峰转蛇形转换

### `@Col`

`@Col` 用于给属性声明更明确的列映射或列约束。

常见场景：

- 数据库列名与 Java 属性名不一致
- 需要声明字符长度、精度、小数位等约束

常用属性：

- `name`：数据库列名
- `charMaxlength`：字符类型最大长度
- `precision`：数值精度
- `scale`：数值小数位

示例：

```java
@Entity(name = "platform_file")
@Title(title = "文件")
public class FileInfo extends BaseEntity {
    private String name;

    @Col(name = "saved_name")
    @Title(title = "保存名称", description = "存储在磁盘的文件名称")
    private String savedName;

    @Col(name = "path")
    @Title(title = "相对路径", description = "一般相对于文件存储根目录。")
    private String relativePath;

    private int size;

    @Col(name = "type")
    @Title(title = "文件类型", description = "文件后缀")
    private String fileType;
}
```

带约束的示例：

```java
@Entity(name = "platform_permission")
public class Permission extends BaseEntity {

    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;

    private String name;
    private String code;
    private String type;
    private String object;

    @Title(title = "规则")
    @Col(name = "rule", charMaxlength = 1024)
    private String rule;

    private String description;

    @Transient
    private boolean isDefault;
}
```

### `@Title`

`@Title` 用于给实体或字段补充标题和说明，方便元数据管理、界面展示和文档理解。

常见场景：

- 需要为实体声明中文名称
- 需要为字段补充业务语义说明

常用属性：

- `title`：标题
- `description`：补充描述

示例：

```java
@Entity(name = "platform_file")
@Title(title = "文件")
public class FileInfo extends BaseEntity {
    @Title(title = "文件名称")
    private String name;

    @Col(name = "saved_name")
    @Title(title = "保存名称", description = "存储在磁盘的文件名称")
    private String savedName;
}
```

### `@Transient`

`@Transient` 用于标记不参与实体字段解析的属性。

这些属性仍然可以参与 Java 侧业务逻辑，但不会映射到数据库列。

常见场景：

- 属性只用于运行时拼装或计算
- 属性用于临时展示，不需要落库
- 属性是外部服务或其他聚合对象的承载字段

示例：

```java
@Entity(name = "platform_user")
public class User extends BaseEntity {
    private String name;
    private String loginName;
    private String password;

    @Transient
    private boolean isOnline;

    @Transient
    private String temporaryData;
}
```

## 继承建议

框架侧通常建议业务实体继承 `BaseEntity`，以复用基础通用字段。

典型字段包括：

```java
public class BaseEntity {
    protected String id;
    protected String tenantCode;
    protected String creator;
    protected String creatorName;
    protected String updater;
    protected String updaterName;
    protected Date createAt;
    protected Date updateAt;
}
```

这类字段与 Fluent DSL 写入链路里的默认字段填充能力天然配合更好。

## 最佳实践

### 命名规范

- 表名使用小写加下划线，例如 `platform_user`
- 列名使用小写加下划线，例如 `login_name`
- Java 属性使用驼峰命名，例如 `loginName`
- 实体类使用大驼峰命名，例如 `UserInfo`

### 注解使用建议

- 每个可持久化实体都应显式加 `@Entity`
- 只有在列名或列约束与默认推导不一致时才使用 `@Col`
- 建议为实体和关键业务字段补 `@Title`
- 只把真正不需要落库的字段标为 `@Transient`

### 与 Fluent DSL 的配合方式

- 注解层负责声明实体元数据
- Fluent DSL 层负责基于这些元数据执行查询与写入
- 如果字段、表名、标题信息声明不准确，Fluent DSL 在查询、关联和写入时也会受到影响

## 完整示例

```java
@Entity(name = "platform_user")
@Title(title = "用户")
public class User extends BaseEntity {
    @Title(title = "用户名")
    private String name;

    @Title(title = "登录名")
    private String loginName;

    @Title(title = "密码")
    private String password;

    @Col(name = "phone_number")
    @Title(title = "手机号")
    private String phoneNumber;

    @Col(name = "email")
    @Title(title = "邮箱")
    private String email;

    @Title(title = "状态")
    private int status;

    @Transient
    private List<Role> roles;
}
```

## 推荐继续阅读

- [Fluent DSL 指引](fluent-dsl.md)
- [ORM 总览](overview.md)
- [核心模块说明](../reference/core-modules.md)
