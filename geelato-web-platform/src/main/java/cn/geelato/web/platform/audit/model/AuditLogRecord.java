package cn.geelato.web.platform.audit.model;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 平台业务审计日志实体。
 *
 * <p>以「业务动作」为核心建模：记录谁（含委托代理身份）对哪个业务对象执行了什么业务动作、产生了什么影响。
 * {@link #summary} 是面向用户的人话摘要；{@link #detailJson} 是可展开的字段级前后变化明细。
 *
 * <p>不继承 BaseEntity 的业务编号约定，独立声明 create/update 审计字段以贴合表结构。
 */
@Getter
@Setter
@Entity(name = "platform_audit_log", catalog = "platform")
@Title(title = "平台业务审计日志")
public class AuditLogRecord extends BaseEntity {

    @Title(title = "链路追踪ID")
    @Col(name = "trace_id", charMaxlength = 64)
    private String traceId;

    @Title(title = "请求ID")
    @Col(name = "request_id", charMaxlength = 64)
    private String requestId;

    @Title(title = "操作时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "operate_at")
    private Date operateAt;

    @Title(title = "捕获层")
    @Col(name = "capture_layer", charMaxlength = 16)
    private String captureLayer;

    @Title(title = "动作类型")
    @Col(name = "oper_type", charMaxlength = 32)
    private String operType;

    @Title(title = "业务动作名")
    @Col(name = "oper_name", charMaxlength = 128)
    private String operName;

    @Title(title = "业务类型")
    @Col(name = "biz_type", charMaxlength = 64)
    private String bizType;

    @Title(title = "实体中文名")
    @Col(name = "entity_title", charMaxlength = 128)
    private String entityTitle;

    @Title(title = "实体名")
    @Col(name = "entity_name", charMaxlength = 64)
    private String entityName;

    @Title(title = "表名")
    @Col(name = "table_name", charMaxlength = 64)
    private String tableName;

    @Title(title = "业务对象主键")
    @Col(name = "target_id", charMaxlength = 64)
    private String targetId;

    @Title(title = "业务对象名称")
    @Col(name = "target_name", charMaxlength = 256)
    private String targetName;

    @Title(title = "实际操作人ID")
    @Col(name = "actor_id", charMaxlength = 64)
    private String actorId;

    @Title(title = "实际操作人名称")
    @Col(name = "actor_name", charMaxlength = 64)
    private String actorName;

    @Title(title = "操作人类型")
    @Col(name = "actor_type", charMaxlength = 16)
    private String actorType;

    @Title(title = "委托人ID")
    @Col(name = "delegator_id", charMaxlength = 64)
    private String delegatorId;

    @Title(title = "委托人名称")
    @Col(name = "delegator_name", charMaxlength = 64)
    private String delegatorName;

    @Title(title = "租户编码")
    @Col(name = "tenant_code", charMaxlength = 64)
    private String tenantCode;

    @Title(title = "组织ID")
    @Col(name = "org_id", charMaxlength = 32)
    private String orgId;

    @Title(title = "部门ID")
    @Col(name = "dept_id", charMaxlength = 32)
    private String deptId;

    @Title(title = "业务单元ID")
    @Col(name = "bu_id", charMaxlength = 32)
    private String buId;

    @Title(title = "客户端ID")
    @Col(name = "client_id", charMaxlength = 64)
    private String clientId;

    @Title(title = "会话ID")
    @Col(name = "session_id", charMaxlength = 64)
    private String sessionId;

    @Title(title = "操作IP")
    @Col(name = "ip", charMaxlength = 64)
    private String ip;

    @Title(title = "User-Agent")
    @Col(name = "user_agent", charMaxlength = 255)
    private String userAgent;

    @Title(title = "触发方法")
    @Col(name = "method", charMaxlength = 255)
    private String method;

    @Title(title = "业务摘要")
    @Col(name = "summary", charMaxlength = 1024)
    private String summary;

    @Title(title = "数据明细")
    @Col(name = "detail_json")
    private String detailJson;

    @Title(title = "扩展信息")
    @Col(name = "metadata")
    private String metadata;

    @Title(title = "耗时(毫秒)")
    @Col(name = "duration_ms")
    private Integer durationMs;
}
