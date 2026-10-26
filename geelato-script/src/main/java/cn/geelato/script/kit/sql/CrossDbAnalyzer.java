package cn.geelato.script.kit.sql;

import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlDelete;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlUpdate;
import org.apache.calcite.sql.dialect.MysqlSqlDialect;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.validate.SqlConformanceEnum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 跨库语句分析器：Calcite 解析（Lex.MYSQL + LENIENT）→ 位置感知提取未限定表引用
 * （FROM/JOIN/子查询/DML 目标表，排除 CTE 别名与已带库名的限定名）→ 逐表定位分类：
 * LOCAL（执行库本库/未登记）、PREFIX（同实例异库，前缀改写）、FEDERATE（跨实例/异构，联邦执行）。
 * 解析失败或多语句原样透传；连接登记与配置不一致由 resolver 硬失败。
 */
public class CrossDbAnalyzer {

    /** 联邦挂载时执行库的保留挂载名。 */
    public static final String LOCAL_MOUNT = "local";

    private final TableLocationResolver resolver;

    public CrossDbAnalyzer(TableLocationResolver resolver) {
        this.resolver = resolver;
    }

    public enum Action {LOCAL, PREFIX, FEDERATE}

    private record Site(SqlIdentifier id, Object container, int index) {
    }

    public static final class RefDecision {
        private final String name;
        private final TableLocation location;
        private final Action action;
        private final List<Site> sites;

        private RefDecision(String name, TableLocation location, Action action, List<Site> sites) {
            this.name = name;
            this.location = location;
            this.action = action;
            this.sites = sites;
        }

        public String name() {
            return name;
        }

        public TableLocation location() {
            return location;
        }

        public Action action() {
            return action;
        }

        public List<Site> sites() {
            return sites;
        }
    }

    public enum Route {DIRECT, PREFIX, FEDERATE}

    public static final class Analysis {
        private final String originalSql;
        private final NamedParamSupport.Masked masked;
        private final SqlNode root;
        private final String skipReason;
        private final ExecutingTarget target;
        private final Map<String, RefDecision> decisions;
        private final boolean write;

        private Analysis(String originalSql, NamedParamSupport.Masked masked, SqlNode root,
                         String skipReason, ExecutingTarget target, Map<String, RefDecision> decisions, boolean write) {
            this.originalSql = originalSql;
            this.masked = masked;
            this.root = root;
            this.skipReason = skipReason;
            this.target = target;
            this.decisions = decisions;
            this.write = write;
        }

        public boolean parseable() {
            return root != null;
        }

        public String skipReason() {
            return skipReason;
        }

        public ExecutingTarget target() {
            return target;
        }

        public boolean write() {
            return write;
        }

        public Map<String, RefDecision> decisions() {
            return decisions;
        }

        public Route route() {
            for (RefDecision d : decisions.values()) {
                if (d.action == Action.FEDERATE) {
                    return Route.FEDERATE;
                }
            }
            for (RefDecision d : decisions.values()) {
                if (d.action == Action.PREFIX) {
                    return Route.PREFIX;
                }
            }
            return Route.DIRECT;
        }

        /** 联邦挂载清单：挂载名 → 连接位置（null=执行库本库）。 */
        public Map<String, TableLocation> mounts() {
            Map<String, TableLocation> mounts = new LinkedHashMap<>();
            for (RefDecision d : decisions.values()) {
                if (d.action == Action.LOCAL) {
                    continue;
                }
                if (d.action == Action.PREFIX || d.action == Action.FEDERATE) {
                    mounts.putIfAbsent(d.location.connectId(), d.location);
                }
            }
            return mounts;
        }

        /**
         * 同实例前缀改写后的 SQL：按 SqlParserPos 在掩码文本上切片替换表名区间，
         * 语句其余部分（关键字大小写/注释/hint/格式）逐字符保持原文，再还原 :name 命名参数。
         */
        public String prefixSql() {
            String text = masked.sql();
            List<Span> spans = new ArrayList<>();
            for (RefDecision d : decisions.values()) {
                if (d.action != Action.PREFIX) {
                    continue;
                }
                String replacement = renderQualified(target, d.location.qualifier(), d.name);
                for (Site site : d.sites()) {
                    spans.add(posToSpan(text, site.id().getParserPosition(), d.name, replacement));
                }
            }
            spans.sort(java.util.Comparator.comparingInt(Span::start));
            for (int i = 1; i < spans.size(); i++) {
                if (spans.get(i).start() < spans.get(i - 1).end()) {
                    throw new IllegalStateException("表名改写区间重叠: " + text.substring(spans.get(i).start(), spans.get(i).end()));
                }
            }
            StringBuilder out = new StringBuilder(text.length() + 32);
            int cursor = 0;
            for (Span span : spans) {
                out.append(text, cursor, span.start()).append(span.replacement());
                cursor = span.end();
            }
            out.append(text.substring(cursor));
            return NamedParamSupport.restore(out.toString(), masked.params());
        }

        private record Span(int start, int end, String replacement) {
        }

        /** Calcite 行/列(1-based) → 掩码文本字符闭开区间；endColumnNum 排他/包含双约定自动校验，均不匹配硬失败。 */
        private static Span posToSpan(String text, SqlParserPos pos, String name, String replacement) {
            int start = offset(text, pos.getLineNum(), pos.getColumnNum());
            int colEnd = offset(text, pos.getEndLineNum(), pos.getEndColumnNum());
            // 排他约定: end=colEnd；包含约定: end=colEnd+1（1-based 末列字符在 0-based 下标 colEnd）
            for (int end : new int[]{colEnd, colEnd + 1}) {
                if (end > start && end <= text.length() && text.substring(start, end).equalsIgnoreCase(name)) {
                    return new Span(start, end, replacement);
                }
            }
            throw new IllegalStateException("表名原文定位失败: table=" + name + " pos=" + pos.getLineNum()
                    + ":" + pos.getColumnNum() + "-" + pos.getEndLineNum() + ":" + pos.getEndColumnNum()
                    + " 附近文本=" + snippet(text, start));
        }

        private static int offset(String text, int line, int col) {
            int idx = 0;
            for (int l = 1; l < line; l++) {
                int nl = text.indexOf('\n', idx);
                if (nl < 0) {
                    throw new IllegalStateException("位置越界: line=" + line + " col=" + col);
                }
                idx = nl + 1;
            }
            return idx + Math.max(0, col - 1);
        }

        private static String snippet(String text, int start) {
            return text.substring(Math.max(0, start - 20), Math.min(text.length(), start + 40));
        }

        /** 方言限定名：MySQL 恒反引号（保留登记大小写）；PG/其他纯小写段裸拼（避免引号改变大小写折叠），其余双引号。 */
        static String renderQualified(ExecutingTarget target, String qualifier, String name) {
            if (target.mysqlFamily()) {
                return "`" + qualifier.replace("`", "``") + "`.`" + name.replace("`", "``") + "`";
            }
            return quoteIfNeeded(qualifier) + "." + quoteIfNeeded(name);
        }

        private static String quoteIfNeeded(String part) {
            return part.matches("[a-z_][a-z0-9_]*") ? part : "\"" + part.replace("\"", "\"\"") + "\"";
        }

        /** 联邦改写后的 SQL（:name 已掩码为 ?，按序绑定）；所有表限定为 挂载名.表 两段式。 */
        public String federatedSql() {
            for (RefDecision d : decisions.values()) {
                String mount = d.action == Action.LOCAL ? LOCAL_MOUNT : d.location.connectId();
                replaceSites(d, List.of(mount, d.name));
            }
            return root.toSqlString(MysqlSqlDialect.DEFAULT).getSql();
        }

        public NamedParamSupport.Masked masked() {
            return masked;
        }

        public String originalSql() {
            return originalSql;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            for (RefDecision d : decisions.values()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(d.name).append("→").append(d.action);
                if (d.location != null) {
                    sb.append('(').append(d.location.connectId());
                    if (d.location.host() != null) {
                        sb.append('@').append(d.location.host()).append(':').append(d.location.port());
                    }
                    sb.append(')');
                }
            }
            return sb.toString();
        }

        private void replaceSites(RefDecision d, List<String> parts) {
            for (Site site : d.sites) {
                SqlIdentifier qualified = new SqlIdentifier(parts, site.id().getParserPosition());
                if (site.container() instanceof SqlCall call) {
                    call.setOperand(site.index(), qualified);
                } else if (site.container() instanceof SqlNodeList list) {
                    list.set(site.index(), qualified);
                }
            }
        }
    }

    public Analysis analyze(String sql, ExecutingTarget target, boolean write) {
        if (target == null) {
            return skipped(sql, "无法识别执行库身份（连接池 jdbc url 不可得）", write);
        }
        Optional<NamedParamSupport.Masked> masked = NamedParamSupport.mask(sql);
        if (masked.isEmpty()) {
            return skipped(sql, "含裸位置参数 ? 或掩码失败", write);
        }
        SqlNodeList statements;
        try {
            SqlParser parser = SqlParser.create(masked.get().sql(), SqlParser.config()
                    .withLex(Lex.MYSQL).withConformance(SqlConformanceEnum.LENIENT));
            statements = parser.parseStmtList();
        } catch (Exception e) {
            return skipped(sql, "SQL 解析失败（" + e.getMessage() + "）", write);
        }
        if (statements.size() != 1) {
            return skipped(sql, "多语句/空语句不改写", write);
        }
        SqlNode root = statements.get(0);
        Walker walker = new Walker();
        if (!walker.collectStatement(root)) {
            return skipped(sql, "不支持的语句类型 " + root.getKind(), write);
        }
        Map<String, RefDecision> decisions = new LinkedHashMap<>();
        for (Walker.TableRef ref : walker.refs.values()) {
            TableLocation location = resolver.resolve(ref.name());
            decisions.put(ref.name(), new RefDecision(ref.name(), location,
                    classify(location, target), ref.sites()));
        }
        return new Analysis(sql, masked.get(), root, null, target, decisions, write);
    }

    private Action classify(TableLocation location, ExecutingTarget target) {
        if (location == null) {
            return Action.LOCAL;
        }
        if (!target.sameServer(location)) {
            return Action.FEDERATE;
        }
        if (location.mysqlFamily() != target.mysqlFamily() || location.postgresFamily() != target.postgresFamily()) {
            return Action.FEDERATE;
        }
        if (location.postgresFamily()
                && (location.catalog() == null || target.db() == null
                || !location.catalog().trim().equalsIgnoreCase(target.db().trim()))) {
            // PG 同实例不同 database 无法用 schema 前缀直达，走联邦
            return Action.FEDERATE;
        }
        return target.sameQualifier(location) ? Action.LOCAL : Action.PREFIX;
    }

    private Analysis skipped(String sql, String reason, boolean write) {
        return new Analysis(sql, null, null, reason, null, Map.of(), write);
    }

    /** 位置感知遍历：记录表引用的 (标识符, 父容器, 序号) 供原地替换。 */
    private static final class Walker {
        private final Map<String, TableRef> refs = new LinkedHashMap<>();

        private record TableRef(String name, List<Site> sites) {
        }

        boolean collectStatement(SqlNode node) {
            switch (node.getKind()) {
                case SELECT:
                    walkSelect((SqlSelect) node, new HashSet<>());
                    return true;
                case ORDER_BY:
                    // Calcite 将带 ORDER BY/LIMIT 的查询包装为 ORDER_BY(查询, 排序, offset, fetch)
                case WITH:
                    walkExpr(node, new HashSet<>());
                    return true;
                case INSERT: {
                    SqlInsert insert = (SqlInsert) node;
                    collectTarget(insert.getTargetTable(), insert);
                    walkExpr(node, new HashSet<>());
                    return true;
                }
                case UPDATE: {
                    SqlUpdate update = (SqlUpdate) node;
                    collectTarget(update.getTargetTable(), update);
                    walkExpr(node, new HashSet<>());
                    return true;
                }
                case DELETE: {
                    SqlDelete delete = (SqlDelete) node;
                    collectTarget(delete.getTargetTable(), delete);
                    walkExpr(node, new HashSet<>());
                    return true;
                }
                default:
                    return false;
            }
        }

        private void collectTarget(SqlNode target, SqlCall statement) {
            if (target instanceof SqlIdentifier id && id.names.size() == 1) {
                record(id, statement, indexOf(statement, id));
            }
        }

        private void walkSelect(SqlSelect select, Set<String> ctes) {
            walkFromItem(select.getFrom(), ctes, select);
            walkExpr(select.getSelectList(), ctes);
            walkExpr(select.getWhere(), ctes);
            walkExpr(select.getGroup(), ctes);
            walkExpr(select.getHaving(), ctes);
            walkExpr(select.getWindowList(), ctes);
            walkExpr(select.getOrderList(), ctes);
            walkExpr(select.getOffset(), ctes);
            walkExpr(select.getFetch(), ctes);
        }

        private void walkFromItem(SqlNode item, Set<String> ctes, Object container) {
            if (item == null) {
                return;
            }
            if (item instanceof SqlIdentifier id) {
                if (id.names.size() == 1 && !ctes.contains(id.names.get(0).toUpperCase(Locale.ROOT))) {
                    record(id, container, indexOf(container, item));
                }
                return;
            }
            if (item instanceof SqlNodeList list) {
                for (SqlNode node : list) {
                    walkFromItem(node, ctes, list);
                }
                return;
            }
            if (item instanceof SqlSelect select) {
                walkSelect(select, ctes);
                return;
            }
            if (item instanceof SqlJoin join) {
                walkFromItem(join.getLeft(), ctes, join);
                walkFromItem(join.getRight(), ctes, join);
                walkExpr(join.getCondition(), ctes);
                return;
            }
            if (item instanceof SqlCall call && call.getKind() == SqlKind.AS) {
                walkFromItem(call.operand(0), ctes, call);
                return;
            }
            walkExpr(item, ctes);
        }

        private void walkExpr(SqlNode node, Set<String> ctes) {
            if (node == null) {
                return;
            }
            if (node instanceof SqlSelect select) {
                walkSelect(select, ctes);
                return;
            }
            if (node instanceof SqlNodeList list) {
                for (SqlNode child : list) {
                    walkExpr(child, ctes);
                }
                return;
            }
            if (!(node instanceof SqlCall call)) {
                return;
            }
            if (call.getKind() == SqlKind.WITH) {
                Set<String> inner = new HashSet<>(ctes);
                SqlNodeList items = (SqlNodeList) call.operand(0);
                for (SqlNode withItem : items) {
                    if (withItem instanceof SqlCall itemCall && itemCall.getKind() == SqlKind.WITH_ITEM) {
                        if (itemCall.operand(0) instanceof SqlIdentifier cteName) {
                            inner.add(cteName.names.get(0).toUpperCase(Locale.ROOT));
                        }
                        for (int i = 1; i < itemCall.operandCount(); i++) {
                            walkExpr(itemCall.operand(i), inner);
                        }
                    }
                }
                walkExpr(call.operand(1), inner);
                return;
            }
            for (SqlNode operand : call.getOperandList()) {
                walkExpr(operand, ctes);
            }
        }

        private void record(SqlIdentifier id, Object container, int index) {
            String name = id.names.get(0);
            TableRef ref = refs.computeIfAbsent(name, k -> new TableRef(k, new ArrayList<>()));
            ref.sites().add(new Site(id, container, index));
        }

        private static int indexOf(Object container, SqlNode node) {
            if (container instanceof SqlCall call) {
                return call.getOperandList().indexOf(node);
            }
            if (container instanceof SqlNodeList list) {
                return list.indexOf(node);
            }
            return -1;
        }
    }
}
