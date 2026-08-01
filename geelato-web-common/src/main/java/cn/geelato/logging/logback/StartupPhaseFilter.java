package cn.geelato.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * 仅在启动期放行日志的 appender 级过滤器。
 *
 * <p>挂载到启动日志 appender（startupLogFile）：启动期返回 ACCEPT 写入文件，
 * 进入运行期后返回 DENY，使启动日志文件不再接收任何日志。</p>
 *
 * <p>返回 ACCEPT 而非 NEUTRAL，是为了在该 appender 上以阶段为准、与级别过滤互不干扰；
 * appender 上若同时配置了 {@link ch.qos.logback.classic.filter.ThresholdFilter}，
 * 由于本过滤器返回 ACCEPT 会短路后续过滤，故启动日志 appender 不应再叠加级别过滤。</p>
 */
public class StartupPhaseFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        return StartupPhaseManager.isStartupPhase() ? FilterReply.ACCEPT : FilterReply.DENY;
    }
}
