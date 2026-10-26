package cn.geelato.script.core;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 一次调试会话的对外坐标（由 {@link ScriptDebugService#start} 返回）。
 * <p>
 * {@code host} 恒为 inspector 绑定的回环地址（127.0.0.1），{@code port} 为其监听端口，
 * {@code path} 为 inspector ws 路径（= sessionId）。这些坐标仅供同源代理（持 Context 进程
 * 或其可信网关）内部连接；面向浏览器的返回值须由上层剥离 host/port，仅暴露代理路径。
 */
@Getter
@Builder
@ToString
public class DebugSession {

    private final String sessionId;
    /** inspector ws 主机（进程级端口，只绑回环）。 */
    private final String host;
    /** inspector ws 端口（start 时分配）。 */
    private final int port;
    /** inspector ws 路径（缺省即 sessionId）。 */
    private final String path;
    /** 会话归属标识（供代理鉴权，不下发浏览器）。 */
    private final String owner;
    /** 实际使用的引擎版本。 */
    private final String engineVersion;
}
