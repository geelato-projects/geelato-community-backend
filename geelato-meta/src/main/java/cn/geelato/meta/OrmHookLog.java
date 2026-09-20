package cn.geelato.meta;

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
 * ORM 钩子执行发件箱，每次触发一行。
 * <p>
 * 事务提交后（afterCommit 回调）异步写入（ready），立即尝试执行一次；
 * 失败由调度器异步扫描、抢占（CAS→processing）执行：
 * 成功→success，失败→指数退避重试→死信（dead，可手动重放）。
 * <p>
 * 执行时按 hook_id 回读<b>当前</b>配置（配置被删/禁用则死信）——
 * 本表只存触发上下文快照（事件、载荷），不复制动作配置，避免大字段逐行膨胀；
 * 修改配置对未完成的重试立即生效，修复配置后重放即按新配置执行。
 * <p>
 * 本表读写全部走 JdbcTemplate 直 SQL（不经 ORM save），从根上避免事件递归。
 *
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_orm_hook_log", catalog = "platform")
@Title(title = "ORM钩子执行发件箱")
public class OrmHookLog extends BaseEntity {

    @Title(title = "钩子配置ID")
    @Col(name = "hook_id", charMaxlength = 32, nullable = false)
    private String hookId;

    @Title(title = "触发事件ID", description = "ORM 事件的 eventId")
    @Col(name = "event_id", charMaxlength = 36)
    private String eventId;

    @Title(title = "实体名称")
    @Col(name = "entity_name", charMaxlength = 64, nullable = false)
    private String entityName;

    @Title(title = "事件类型", description = "insert | update | delete")
    @Col(name = "event_type", charMaxlength = 16, nullable = false)
    private String eventType;

    @Title(title = "操作类型", description = "Insert | Update | Delete")
    @Col(name = "op_type", charMaxlength = 16)
    private String opType;

    @Title(title = "动作类型", description = "触发时刻的信息性快照：script | http；实际执行按当前配置")
    @Col(name = "action_type", charMaxlength = 16, nullable = false)
    private String actionType;

    @Title(title = "触发载荷", description = "JSON：新值快照 values、会话信息 session、eventId 等，传给动作脚本")
    @Col(name = "payload_json")
    private String payloadJson;

    @Title(title = "执行状态", description = "ready | processing | success | dead")
    @Col(name = "status", charMaxlength = 16)
    private String status;

    @Title(title = "重试次数")
    @Col(name = "retry_count")
    private int retryCount;

    @Title(title = "下次重试时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "next_retry_at")
    private Date nextRetryAt;

    @Title(title = "错误信息")
    @Col(name = "error_msg", charMaxlength = 512)
    private String errorMsg;
}
