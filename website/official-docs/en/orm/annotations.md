# ORM Annotations

This page explains the ORM annotations currently recommended by Geelato Framework for backend entity declaration and metadata mapping.

## What They Are For

ORM annotations define how a Java class is recognized as a framework entity and how its fields relate to database columns, titles, and non-persistent properties.

They answer the question of how entity metadata is declared. Data access itself is covered by the [Fluent DSL Guide](fluent-dsl.md).

## Core Annotations

### `@Entity`

`@Entity` marks a class as a persistent entity and maps it to a database table.

Typical use cases:

- creating a new framework entity
- making the class discoverable by the metadata scanner
- explicitly binding the entity to a table name

Common property:

- `name`: the table name; when omitted, the framework usually derives it from the class name with camel-to-snake conversion

Example:

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

Notes:

- classes marked with `@Entity` are treated as framework entities
- regular fields participate in mapping by default
- field names are usually converted from camel case to snake case for column names

### `@Col`

`@Col` defines an explicit column mapping or column constraint for a field.

Typical use cases:

- the database column name does not match the Java field name
- the field needs explicit length, precision, or scale constraints

Common properties:

- `name`: database column name
- `charMaxlength`: max string length
- `precision`: numeric precision
- `scale`: numeric scale

Example:

```java
@Entity(name = "platform_file")
@Title(title = "File")
public class FileInfo extends BaseEntity {
    private String name;

    @Col(name = "saved_name")
    @Title(title = "Saved Name", description = "The file name stored on disk")
    private String savedName;

    @Col(name = "path")
    @Title(title = "Relative Path", description = "Usually relative to the file storage root")
    private String relativePath;

    private int size;

    @Col(name = "type")
    @Title(title = "File Type", description = "File extension")
    private String fileType;
}
```

Constraint example:

```java
@Entity(name = "platform_permission")
public class Permission extends BaseEntity {

    @Title(title = "App Id")
    @Col(name = "app_id")
    private String appId;

    private String name;
    private String code;
    private String type;
    private String object;

    @Title(title = "Rule")
    @Col(name = "rule", charMaxlength = 1024)
    private String rule;

    private String description;

    @Transient
    private boolean isDefault;
}
```

### `@Title`

`@Title` adds a business-facing title and optional description to an entity or field.

Typical use cases:

- giving an entity a readable display name
- documenting important fields with domain meaning

Common properties:

- `title`: display title
- `description`: supplemental description

Example:

```java
@Entity(name = "platform_file")
@Title(title = "File")
public class FileInfo extends BaseEntity {
    @Title(title = "File Name")
    private String name;

    @Col(name = "saved_name")
    @Title(title = "Saved Name", description = "The file name stored on disk")
    private String savedName;
}
```

### `@Transient`

`@Transient` marks a property that should not be mapped to a database column.

These properties can still be used in Java-side logic, but they are not persisted as part of the entity metadata model.

Typical use cases:

- runtime-only calculated fields
- temporary display fields
- aggregation or external service payload fields

Example:

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

## Inheritance Recommendation

Backend entities are typically expected to extend `BaseEntity` so they can reuse the standard common fields.

Typical fields:

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

These fields also fit naturally with the default field filling behavior in the Fluent DSL write path.

## Best Practices

### Naming

- use lowercase snake case for table names, such as `platform_user`
- use lowercase snake case for column names, such as `login_name`
- use camel case for Java properties, such as `loginName`
- use PascalCase for entity classes, such as `UserInfo`

### Annotation Usage

- add `@Entity` to every persistent entity
- use `@Col` only when the mapping or constraints differ from the default convention
- add `@Title` to entities and important business fields
- use `@Transient` only for fields that truly should not be stored

### Working with Fluent DSL

- annotations define entity metadata
- Fluent DSL executes queries and writes against that metadata
- if the metadata is inaccurate, query, join, and write behavior can also become inaccurate

## Full Example

```java
@Entity(name = "platform_user")
@Title(title = "User")
public class User extends BaseEntity {
    @Title(title = "User Name")
    private String name;

    @Title(title = "Login Name")
    private String loginName;

    @Title(title = "Password")
    private String password;

    @Col(name = "phone_number")
    @Title(title = "Phone Number")
    private String phoneNumber;

    @Col(name = "email")
    @Title(title = "Email")
    private String email;

    @Title(title = "Status")
    private int status;

    @Transient
    private List<Role> roles;
}
```

## Suggested Next Reading

- [Fluent DSL Guide](fluent-dsl.md)
- [ORM Overview](overview.md)
- [Core Modules](../reference/core-modules.md)
