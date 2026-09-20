package cn.geelato.archive.entity;

import cn.geelato.archive.enums.ExecutionModeEnum;
import cn.geelato.archive.enums.ExecutorTypeEnum;
import cn.geelato.archive.enums.PolicyTypeEnum;
import cn.geelato.archive.enums.TargetTypeEnum;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 归档策略：声明"哪张表、按什么条件、归档到哪里、多快搬"，不包含执行逻辑。
 * <p>表级模型（与实体解耦）：策略通常由实体管理"开启归档"动作生成（实体名解析物理表名后插入），
 * 也可直接手工创建。sourceEntityName 仅作追溯回显，策略逻辑不依赖它。</p>
 */
@Getter
@Setter
@cn.geelato.lang.meta.Entity(name = "platform_archive_policy", catalog = "platform")
@Title(title = "归档策略")
public class ArchivePolicy extends BaseSortableEntity {

    @Title(title = "名称")
    @Col(name = "name", charMaxlength = 128)
    private String name;

    @Title(title = "编码", description = "唯一，预置模板幂等识别")
    @Col(name = "code", charMaxlength = 64)
    private String code;

    @Title(title = "描述")
    @Col(name = "description", charMaxlength = 512)
    private String description;

    @Title(title = "源表名", description = "表级归档对象；通常由实体管理开启归档时解析填入")
    @Col(name = "table_name", charMaxlength = 64)
    private String tableName;

    @Title(title = "来源实体名", description = "可选冗余，仅追溯回显，策略逻辑不依赖")
    @Col(name = "source_entity_name", charMaxlength = 128)
    private String sourceEntityName;

    @Title(title = "策略类型", description = "DEFAULT：create_at 窗口；CUSTOM：任意 WHERE 条件")
    @Col(name = "policy_type", charMaxlength = 16)
    private String policyType = PolicyTypeEnum.DEFAULT.name();

    @Title(title = "保留天数", description = "DEFAULT 策略用：create_at < 当前时间 - retention_days")
    @Col(name = "retention_days")
    private Integer retentionDays;

    @Title(title = "自定义条件", description = "CUSTOM 策略用：任意 WHERE 片段，引擎原样拼入选数查询")
    @Col(name = "where_condition", charMaxlength = 2000)
    private String whereCondition;

    @Title(title = "目标类型", description = "MYSQL/MONGODB/ELASTICSEARCH，一期仅 MYSQL")
    @Col(name = "target_type", charMaxlength = 32)
    private String targetType = TargetTypeEnum.MYSQL.name();

    @Title(title = "归档库连接标识", description = "dev_db_connect 体系；空=主库自身（同库归档）")
    @Col(name = "connect_id", charMaxlength = 64)
    private String connectId;

    @Title(title = "目标表名", description = "空则推导：跨库同名；同库 {源表}_archive")
    @Col(name = "target_table_name", charMaxlength = 64)
    private String targetTableName;

    @Title(title = "执行器", description = "一期固定 INLINE；外部执行器二期预留")
    @Col(name = "executor", charMaxlength = 32)
    private String executor = ExecutorTypeEnum.INLINE.name();

    @Title(title = "执行模式", description = "AUTO/SERVER/LOCAL_FILE/JDBC，默认 AUTO")
    @Col(name = "execution_mode", charMaxlength = 16)
    private String executionMode = ExecutionModeEnum.AUTO.name();

    @Title(title = "每批行数", description = "50~5000，SERVER/LOCAL_FILE 模式可放大")
    @Col(name = "batch_size")
    private Integer batchSize = 200;

    @Title(title = "批间隔毫秒", description = "批间停顿限流，给主库留 IO 余量")
    @Col(name = "batch_interval_ms")
    private Integer batchIntervalMs = 200;

    @Title(title = "单次运行行数上限", description = "渐进式归档，到量即收次日继续；0=不限")
    @Col(name = "max_rows_per_run")
    private Long maxRowsPerRun = 1_000_000L;

    @Title(title = "包含软删行", description = "del_status=1 的行一并归档（按同窗口/条件判定）")
    @Col(name = "include_deleted")
    private Boolean includeDeleted = Boolean.TRUE;

    @Title(title = "启用状态", description = "0：停用（默认，无启用策略绝不动数据）、1：启用")
    @Col(name = "enable_status")
    private Integer enableStatus = 0;

    @Title(title = "最近运行ID")
    @Col(name = "last_run_id", charMaxlength = 32)
    private String lastRunId;

    @Title(title = "最近运行状态")
    @Col(name = "last_run_status", charMaxlength = 16)
    private String lastRunStatus;

    public boolean isEnabled() {
        return enableStatus != null && enableStatus == ColumnDefault.ENABLE_STATUS_VALUE;
    }
}
