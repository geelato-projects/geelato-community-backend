package cn.geelato.archive.entity;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Title;
import com.fasterxml.jackson.annotation.JsonFormat;
import cn.geelato.utils.DateUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 归档运行记录：每次策略执行一行，完整记录批次、行数、游标水位与错误上下文，
 * 是"确保归档不误删数据"的审计凭据。失败记录必须能精确诊断（表/批次/游标/期望与实际行数/堆栈）。
 */
@Getter
@Setter
@cn.geelato.lang.meta.Entity(name = "platform_archive_run", catalog = "platform")
@Title(title = "归档运行记录")
public class ArchiveRun extends BaseEntity {

    @Title(title = "策略ID")
    @Col(name = "policy_id", charMaxlength = 32)
    private String policyId;

    @Title(title = "状态", description = "running/success/failed/canceled")
    @Col(name = "status", charMaxlength = 16)
    private String status;

    @Title(title = "已归档行数")
    @Col(name = "archived_rows")
    private Long archivedRows = 0L;

    @Title(title = "批次数")
    @Col(name = "batches")
    private Integer batches = 0;

    @Title(title = "游标", description = "最后一批末位主键，id 游标升序推进")
    @Col(name = "cursor_id", charMaxlength = 64)
    private String cursorId;

    @Title(title = "时间水位", description = "run 开始时锁定的窗口边界（DEFAULT 策略），防长时间运行边界漂移")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "time_watermark")
    private Date timeWatermark;

    @Title(title = "实际执行模式", description = "AUTO 探测后实际生效的模式")
    @Col(name = "execution_mode", charMaxlength = 16)
    private String executionMode;

    @Title(title = "耗时毫秒")
    @Col(name = "cost_ms")
    private Long costMs = 0L;

    @Title(title = "错误详情", description = "失败时的结构化上下文：表名/批次序号/游标/期望与实际行数/异常堆栈")
    @Col(name = "error_json")
    private String errorJson;

    @Title(title = "开始时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "begin_at")
    private Date beginAt;

    @Title(title = "结束时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "end_at")
    private Date endAt;

    @Title(title = "简要信息", description = "成功时汇总、失败/中止时摘要")
    @Col(name = "message", charMaxlength = 1024)
    private String message;
}
