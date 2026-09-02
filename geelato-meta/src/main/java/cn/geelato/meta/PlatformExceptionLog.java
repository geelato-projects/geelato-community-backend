package cn.geelato.meta;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 平台错误日志。
 * <p>由全局异常处理器（PlatformExceptionHandler）异步写入：每条记录的 id 即反馈凭据（logTag），
 * 运维凭 {@code GET /api/exceptionLog/byTag/{logTag}} 精确查询详细异常（技术消息 + 完整堆栈）。</p>
 *
 * <p>catalog="platform-log"：默认经 catalog-mapping 映射主库；生产如需独立日志库，
 * 配置 {@code geelato.datasource.dynamic.catalog-mapping.platform-log=<connectId>} 整体迁移。</p>
 */
@Getter
@Setter
@Entity(name = "platform_exception_log", catalog = "platform-log")
@Title(title = "平台错误日志")
public class PlatformExceptionLog extends BaseEntity {

    @Title(title = "发生时间")
    @Col(name = "happened_time")
    private Date happenedTime;

    @Title(title = "错误类对象")
    @Col(name = "exception_class", charMaxlength = 255)
    private String exceptionClass;

    @Title(title = "错误堆栈")
    @Col(name = "exception_stacktrace")
    private String exceptionStacktrace;

    @Title(title = "所属应用")
    @Col(name = "app_id", charMaxlength = 255)
    private String appId;

    @Title(title = "错误编码")
    @Col(name = "exception_code", charMaxlength = 255)
    private String exceptionCode;

    /** 覆写父类以对齐表列长度 varchar(64)。 */
    @Title(title = "租户编码")
    @Col(name = "tenant_code", charMaxlength = 64)
    private String tenantCode;
}
