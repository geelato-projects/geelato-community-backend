package cn.geelato.mail.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件过滤器实体（用户级隔离；条件 JSON 数组 + 动作 JSON 对象）。
 *
 * 表名 mail_filter。条件 AND 语义、动作执行（move/label/markRead/markStar/archive/delete）
 * 在 MailFilterService；收信钩子按 sort_order 升序逐条执行启用的过滤器。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_filter", catalog = "mail")
@Title(title = "邮件过滤器")
public class MailFilter extends BaseSortableEntity {

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 过滤器名称 */
    @Title(title = "过滤器名称")
    @Col(name = "name", nullable = false, charMaxlength = 128)
    private String name;

    /** 是否启用 */
    @Title(title = "是否启用")
    @Col(name = "enabled", nullable = false, dataType = "int")
    private int enabled;

    /** 条件JSON数组 [{field,operator,value}] */
    @Title(title = "条件JSON")
    @Col(name = "conditions_json", dataType = "text")
    private String conditionsJson;

    /** 动作JSON对象 {move,label,markRead,markStar,archive,delete,autoReply} */
    @Title(title = "动作JSON")
    @Col(name = "action_json", dataType = "text")
    private String actionJson;

    /** 排序号（收信钩子按此顺序执行） */
    @Title(title = "排序号")
    @Col(name = "sort_order", nullable = false, dataType = "int")
    private int sortOrder;

    /** 创建时标记是否需应用到既有邮件 */
    @Title(title = "应用到既有邮件标记")
    @Col(name = "apply_to_existing", nullable = false, dataType = "int")
    private int applyToExisting;
}
