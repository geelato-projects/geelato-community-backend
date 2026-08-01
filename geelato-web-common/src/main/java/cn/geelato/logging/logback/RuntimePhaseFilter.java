package cn.geelato.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * 仅在运行期放行日志的 appender 级过滤器。
 *
 * <p>挂载到运行错误日志 appender（runtimeLogFile）：启动期返回 DENY（不写入），
 * 进入运行期后返回 NEUTRAL，交由 appender 上后续的 {@link ch.qos.logback.classic.filter.ThresholdFilter}
 * （默认 WARN）按级别进一步过滤。</p>
 *
 * <p>必须配置在 ThresholdFilter 之前，这样运行期才能正确按 WARN+ 级别写入，
 * 启动期则无论级别如何都不写入运行错误日志。</p>
 */
public class RuntimePhaseFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        return StartupPhaseManager.isStartupPhase() ? FilterReply.DENY : FilterReply.NEUTRAL;
    }
}
