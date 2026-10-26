package cn.geelato.script.kit.sql;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * SqlKit 跨库门面：路由（DIRECT 直连 / PREFIX 同实例前缀改写 / FEDERATE 联邦）与联邦执行编排。
 * 透传规则（原样执行、不静默）：执行库身份不可识别、含裸 ?、解析失败、多语句、未登记表、已限定名、CTE 别名。
 * 硬失败：连接登记与配置不一致、联邦挂载无池、写语句涉及跨实例/异构源。
 */
@Slf4j
public class CrossDbSupport {

    private final CrossDbAnalyzer analyzer;
    private final CalciteGateway gateway;

    public CrossDbSupport(TableLocationResolver resolver, ConnectPoolProvider poolProvider) {
        this.analyzer = new CrossDbAnalyzer(resolver);
        this.gateway = new CalciteGateway(poolProvider);
    }

    /** sql=实际执行语句（PREFIX=改写后，FEDERATE=原文，联邦改写在网关内部）；originalSql=用户原文。 */
    public record Routed(String sql, String originalSql, CrossDbAnalyzer.Route route, String summary,
                         CrossDbAnalyzer.Analysis analysis) {
    }

    public Routed route(String sql, DataSource executingDataSource, boolean write) {
        ExecutingTarget target = ExecutingTarget.of(executingDataSource);
        if (target == null) {
            return new Routed(sql, sql, CrossDbAnalyzer.Route.DIRECT, "跨库路由跳过: 无法识别执行库身份", null);
        }
        CrossDbAnalyzer.Analysis analysis = analyzer.analyze(sql, target, write);
        if (!analysis.parseable()) {
            return new Routed(sql, sql, CrossDbAnalyzer.Route.DIRECT, "跨库路由跳过: " + analysis.skipReason(), null);
        }
        CrossDbAnalyzer.Route route = analysis.route();
        if (route == CrossDbAnalyzer.Route.FEDERATE && write) {
            throw new IllegalArgumentException("写语句不支持联邦执行（跨实例/异构源无跨库事务保证）: "
                    + federatedDetail(analysis, target) + "；请将写语句拆分为单库执行");
        }
        if (route == CrossDbAnalyzer.Route.DIRECT) {
            return new Routed(sql, sql, route, null, analysis);
        }
        String effective = route == CrossDbAnalyzer.Route.PREFIX ? analysis.prefixSql() : sql;
        return new Routed(effective, sql, route, analysis.summary(), analysis);
    }

    public List<Map<String, Object>> executeFederated(Routed routed, DataSource executingDataSource,
                                                      Map<String, Object> params, int maxRows, int timeoutMs,
                                                      String execId) {
        if (routed.route() != CrossDbAnalyzer.Route.FEDERATE || routed.analysis() == null) {
            throw new IllegalStateException("非联邦路由不可走联邦执行: " + routed.route());
        }
        return gateway.query(routed.analysis(), executingDataSource, params, maxRows, timeoutMs, execId);
    }

    /** kit 侧 trace/日志回调：仅非 DIRECT 或带跳过原因时产生条目，纯本地语句零噪音。 */
    public String traceSummary(Routed routed) {
        if (routed.summary() == null) {
            return null;
        }
        if (routed.route() == CrossDbAnalyzer.Route.DIRECT) {
            return routed.summary();
        }
        return "route=" + routed.route() + " " + routed.summary();
    }

    private static String federatedDetail(CrossDbAnalyzer.Analysis analysis, ExecutingTarget target) {
        StringBuilder sb = new StringBuilder("执行库 ").append(target.host()).append(':').append(target.port());
        for (CrossDbAnalyzer.RefDecision d : analysis.decisions().values()) {
            if (d.action() == CrossDbAnalyzer.Action.FEDERATE) {
                sb.append("; 表 ").append(d.name()).append(" 位于连接 ").append(d.location().connectId())
                        .append('(').append(d.location().host()).append(':').append(d.location().port())
                        .append(')');
            }
        }
        return sb.toString();
    }
}
