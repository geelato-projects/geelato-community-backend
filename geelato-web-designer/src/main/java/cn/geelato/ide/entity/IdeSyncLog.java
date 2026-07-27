package cn.geelato.ide.entity;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.utils.DateUtils;
import cn.geelato.core.meta.model.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

/**
 * IDE 同步与操作审计日志。
 * <p>
 * 记录所有 pull/push/dry-run/CRUD 操作（who/what/before/after/result），
 * 与 BaseEntity 共用审计字段（但本实体的审计字段含义略不同，故自定义）。
 *
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "ide_sync_log")
@Title(title = "IDE同步审计")
public class IdeSyncLog extends BaseEntity {

    @Title(title = "脚本主键")
    @Col(name = "script_id", nullable = false, charMaxlength = 32)
    private String scriptId;

    @Title(title = "脚本业务编码")
    @Col(name = "script_code", nullable = false, charMaxlength = 128)
    private String scriptCode;

    @Title(title = "操作类型", description = "PULL / PUSH / DRYRUN / CREATE / UPDATE / DELETE / PUBLISH")
    @Col(name = "action", nullable = false, charMaxlength = 16)
    private String action;

    @Title(title = "方向", description = "FILE_TO_DB / DB_TO_FILE（仅 PULL/PUSH 有）")
    @Col(name = "direction", charMaxlength = 16)
    private String direction;

    @Title(title = "操作前哈希")
    @Col(name = "before_hash", charMaxlength = 64)
    private String beforeHash;

    @Title(title = "操作后哈希")
    @Col(name = "after_hash", charMaxlength = 64)
    private String afterHash;

    @Title(title = "操作前版本")
    @Col(name = "before_version")
    private Integer beforeVersion;

    @Title(title = "操作后版本")
    @Col(name = "after_version")
    private Integer afterVersion;

    @Title(title = "操作人 userId")
    @Col(name = "operator", nullable = false, charMaxlength = 32)
    private String operator;

    @Title(title = "操作人名称")
    @Col(name = "operator_name", charMaxlength = 64)
    private String operatorName;

    @Title(title = "结果", description = "SUCCESS / FAIL / REJECTED")
    @Col(name = "result", nullable = false, charMaxlength = 16)
    private String result;

    @Title(title = "结果说明")
    @Col(name = "message", charMaxlength = 512)
    private String message;

    @Title(title = "耗时(毫秒)")
    @Col(name = "duration_ms")
    private Long durationMs;

    @Title(title = "客户端IP")
    @Col(name = "client_ip", charMaxlength = 64)
    private String clientIp;

    @Title(title = "User-Agent")
    @Col(name = "user_agent", charMaxlength = 256)
    private String userAgent;
}
