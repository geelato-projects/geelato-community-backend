package cn.geelato.web.platform.audit.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段级变更明细（detail_json 数组元素）。
 *
 * <p>每个变化的字段一条：记录字段名、中文名（title）、原始前后值、状态码翻译后的前后展示值、是否敏感字段。
 * 新增操作只记 newValue；删除操作只记 oldValue。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditFieldChange {

    /** 字段名（Java 属性名） */
    private String field;

    /** 列名（数据库列名） */
    private String column;

    /** 字段中文名（取自字段级 @Title） */
    private String title;

    /** 变更前原始值 */
    private Object oldValue;

    /** 变更后原始值 */
    private Object newValue;

    /** 变更前展示值（状态码经字典/@Title(description) 翻译后的中文，无映射则同 oldValue） */
    private Object oldDisplay;

    /** 变更后展示值（同上） */
    private Object newDisplay;

    /** 是否敏感字段（已脱敏处理） */
    private boolean sensitive;
}
