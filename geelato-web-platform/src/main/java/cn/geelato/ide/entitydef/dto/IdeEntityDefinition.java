package cn.geelato.ide.entitydef.dto;

import lombok.Data;

import java.util.List;

/**
 * IDE 实体完整定义。
 * <p>
 * 与 .geelato/entities/*.geelato.json 一一对应；push 时整体提交给 /ide/entity/define。
 * MVP 不含外键/check/索引/视图。
 *
 * @author geelato
 */
@Data
public class IdeEntityDefinition {

    /** 实体名（业务编码），正则 ^(?!platform_)[a-z][a-z0-9_]*$ */
    private String entityName;

    /** 物理表名（默认与 entityName 一致） */
    private String tableName;

    /** 中文标题 */
    private String title;

    /** 数据库连接 id（platform_dev_db_connect.id），必填 */
    private String connectId;

    /** 数据库类型：mysql（MVP 仅支持） */
    private String dbType = "mysql";

    /** 数据库 schema */
    private String tableSchema;

    /** 描述 */
    private String description;

    /** 应用 id（可选，多应用隔离用） */
    private String appId;

    /** 字段列表（至少 1 个，至少 1 个 primaryKey=true） */
    private List<IdeEntityFieldDefinition> columns;
}
