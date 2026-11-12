package cn.geelato.web.platform.srv.mv.engine.risingwave;

import cn.geelato.web.platform.srv.mv.engine.MaterializedViewEngine.CheckResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RisingWave 物化视图 DDL 解析与校验器。
 * <p>把三步 DDL 文本解析为结构（source / CDC 摄取表 / 物化视图），并逐项校验：
 * 标识符与摄取绑定是否被越界修改、列定义是否合法、查询体是否只引用已摄取的 CDC 表、
 * 源库（MySQL）方言是否被 RisingWave 支持并给出改写建议。</p>
 * <p>所有校验只读文本，不连接引擎、不执行任何 DDL。</p>
 */
public final class RisingWaveDdlParser {

    /** DDL 中密码的展示掩码；执行前由引擎替换为真实密码 */
    public static final String PASSWORD_MASK = "******";

    /** RisingWave 支持的列类型（用户改写 CDC 摄取表 DDL 时校验用） */
    private static final Set<String> RW_TYPES = Set.of(
            "smallint", "integer", "bigint", "numeric", "real", "double precision",
            "boolean", "bytea", "varchar", "text", "date", "timestamp", "timestamptz",
            "time", "jsonb");

    /** 源库方言 → RisingWave（PG 族）方言改写建议 */
    private static final Map<String, String> DIALECT_REWRITES = new LinkedHashMap<>();

    static {
        DIALECT_REWRITES.put("IFNULL", "COALESCE(a, b)");
        DIALECT_REWRITES.put("GROUP_CONCAT", "STRING_AGG(x, ',')");
        DIALECT_REWRITES.put("DATE_FORMAT", "TO_CHAR(d, f)");
        DIALECT_REWRITES.put("UNIX_TIMESTAMP", "EXTRACT(EPOCH FROM t)");
        DIALECT_REWRITES.put("CURDATE", "CURRENT_DATE");
        DIALECT_REWRITES.put("CURTIME", "CURRENT_TIME");
        DIALECT_REWRITES.put("RAND", "RANDOM()");
        DIALECT_REWRITES.put("SUBSTRING_INDEX", "SPLIT_PART(s, d, n)");
        DIALECT_REWRITES.put("FIND_IN_SET", "POSITION(x IN l) > 0");
    }

    private static final Set<String> SQL_KEYWORDS = Set.of(
            "select", "from", "join", "where", "on", "group", "order", "by", "having",
            "limit", "offset", "union", "all", "as", "and", "or", "not", "in", "is",
            "null", "distinct", "left", "right", "inner", "outer", "cross", "with",
            "case", "when", "then", "else", "end", "using", "lateral", "values");

    private static final Set<String> CONSTRAINT_KEYWORDS = Set.of(
            "not", "null", "primary", "default", "unique", "check", "references",
            "collate", "generated", "comment", "constraint");

    private RisingWaveDdlParser() {
    }

    // ------------------------------------------------------------------ 解析结构

    /** CREATE SOURCE 解析结果 */
    public record SourceDdl(String name, String connector, String hostname, String port,
                            String username, String password, String databaseName, String serverId) {
    }

    /** CDC 摄取表的一列 */
    public record ColumnDef(String name, String type) {
    }

    /** CREATE TABLE ... FROM source TABLE 'db.tbl' 解析结果 */
    public record CdcTableDdlParsed(String tableName, List<ColumnDef> columns, List<String> primaryKeys,
                                     String sourceName, String sourceDatabase, String sourceTable) {
    }

    /** CREATE MATERIALIZED VIEW 解析结果 */
    public record MvDdlParsed(String viewName, String body) {
    }

    /** DDL 无法解析时抛出，消息直接作为校验提示 */
    public static class DdlParseException extends IllegalArgumentException {
        public DdlParseException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ 校验入口

    /**
     * 校验第①步 CDC source DDL。
     *
     * @param ddl              DDL 文本
     * @param expectedName     期望的 source 标识符
     * @param expectedDatabase 期望的源库名
     */
    public static List<CheckResult> validateSource(String ddl, String expectedName, String expectedDatabase) {
        List<CheckResult> items = new ArrayList<>();
        SourceDdl parsed;
        try {
            parsed = parseSourceDdl(ddl);
        } catch (DdlParseException e) {
            items.add(new CheckResult("source-syntax", false, "无法解析 CDC source DDL：" + e.getMessage()));
            return items;
        }
        items.add(new CheckResult("source-syntax", true, "CREATE SOURCE 语句格式正确"));
        boolean nameOk = expectedName != null && expectedName.equalsIgnoreCase(parsed.name());
        items.add(new CheckResult("source-name", nameOk,
                nameOk ? "source 标识符：" + parsed.name()
                        : "source 标识符不可修改，应为 " + expectedName + "（当前：" + parsed.name() + "）"));
        boolean connectorOk = "mysql-cdc".equalsIgnoreCase(parsed.connector());
        items.add(new CheckResult("source-connector", connectorOk,
                connectorOk ? "connector=" + parsed.connector()
                        : "connector 必须为 mysql-cdc（当前：" + parsed.connector() + "）"));
        List<String> missing = new ArrayList<>();
        if (isBlank(parsed.hostname())) missing.add("hostname");
        if (isBlank(parsed.port())) missing.add("port");
        if (isBlank(parsed.username())) missing.add("username");
        if (isBlank(parsed.password())) missing.add("password");
        if (isBlank(parsed.databaseName())) missing.add("database.name");
        boolean portOk = true;
        if (!isBlank(parsed.port())) {
            try {
                int port = Integer.parseInt(parsed.port().trim());
                portOk = port > 0 && port <= 65535;
            } catch (NumberFormatException e) {
                portOk = false;
            }
        }
        boolean paramsOk = missing.isEmpty() && portOk;
        StringBuilder message = new StringBuilder();
        if (!missing.isEmpty()) {
            message.append("缺少连接参数：").append(String.join("、", missing)).append("；");
        }
        if (!portOk) {
            message.append("port 必须为 1-65535 的整数（当前：").append(parsed.port()).append("）；");
        }
        if (message.length() == 0) {
            message.append("连接参数齐全：").append(parsed.hostname()).append(":").append(parsed.port())
                    .append("/").append(parsed.databaseName());
        }
        items.add(new CheckResult("source-params", paramsOk, message.toString()));
        boolean dbOk = expectedDatabase == null || expectedDatabase.equalsIgnoreCase(parsed.databaseName());
        items.add(new CheckResult("source-database-consistency", dbOk,
                dbOk ? "database.name 与源库连接登记一致：" + parsed.databaseName()
                        : "database.name（" + parsed.databaseName() + "）与源库连接登记（" + expectedDatabase
                        + "）不一致：将以本 DDL 为准，并需与第②步 TABLE '库.表' 的库名保持一致"));
        boolean serverIdOk = true;
        String serverIdMessage;
        if (isBlank(parsed.serverId())) {
            serverIdMessage = "未指定 server.id：将随机会话 ID 建立复制通道，多 source 共存同一 MySQL 时可能冲突";
        } else {
            serverIdOk = parsed.serverId().trim().matches("^[1-9][0-9]*$");
            serverIdMessage = serverIdOk
                    ? "server.id=" + parsed.serverId().trim() + "（复制通道唯一标识，须全 MySQL 集群唯一）"
                    : "server.id 必须为正整数（当前：" + parsed.serverId() + "）";
        }
        items.add(new CheckResult("source-server-id", serverIdOk, serverIdMessage));
        return items;
    }

    /**
     * 校验第②步 CDC 摄取表 DDL。
     *
     * @param ddl                DDL 文本
     * @param expectedTableName  期望的 CDC 表标识符
     * @param expectedSourceName 期望的 source 标识符
     * @param expectedDatabase   期望的源库名
     * @param expectedOriginTable 期望的源表名
     * @param sourceColumns      源表列名集合（可空，为空时跳过列名存在性校验）
     */
    public static List<CheckResult> validateCdcTable(String ddl, String expectedTableName,
                                                      String expectedSourceName, String expectedDatabase,
                                                      String expectedOriginTable, Collection<String> sourceColumns) {
        List<CheckResult> items = new ArrayList<>();
        CdcTableDdlParsed parsed;
        try {
            parsed = parseCdcTableDdl(ddl);
        } catch (DdlParseException e) {
            items.add(new CheckResult("cdc-syntax", false, "无法解析 CDC 摄取表 DDL：" + e.getMessage()));
            return items;
        }
        items.add(new CheckResult("cdc-syntax", true, "CREATE TABLE ... FROM source TABLE '库.表' 语句格式正确"));
        boolean tableOk = expectedTableName != null && expectedTableName.equalsIgnoreCase(parsed.tableName());
        items.add(new CheckResult("cdc-table-name", tableOk,
                tableOk ? "CDC 摄取表标识符：" + parsed.tableName()
                        : "CDC 摄取表标识符不可修改，应为 " + expectedTableName + "（当前：" + parsed.tableName() + "）"));
        boolean sourceOk = expectedSourceName != null && expectedSourceName.equalsIgnoreCase(parsed.sourceName());
        boolean databaseOk = expectedDatabase == null || expectedDatabase.equalsIgnoreCase(parsed.sourceDatabase());
        boolean tableBindOk = expectedOriginTable != null && expectedOriginTable.equalsIgnoreCase(parsed.sourceTable());
        boolean bindingOk = sourceOk && databaseOk && tableBindOk;
        StringBuilder bindingMessage = new StringBuilder();
        if (!sourceOk) {
            bindingMessage.append("FROM 的 source 不可修改，应为 ").append(expectedSourceName)
                    .append("（当前：").append(parsed.sourceName()).append("）；");
        }
        if (!databaseOk) {
            bindingMessage.append("绑定的库名应为 ").append(expectedDatabase)
                    .append("（当前：").append(parsed.sourceDatabase()).append("）；");
        }
        if (!tableBindOk) {
            bindingMessage.append("绑定的源表应为 ").append(expectedOriginTable)
                    .append("（当前：").append(parsed.sourceTable()).append("）；");
        }
        if (bindingMessage.length() == 0) {
            bindingMessage.append("摄取绑定正确：").append(parsed.sourceDatabase()).append(".")
                    .append(parsed.sourceTable()).append(" ← ").append(parsed.sourceName());
        }
        items.add(new CheckResult("cdc-binding", bindingOk, bindingMessage.toString()));

        List<String> duplicate = new ArrayList<>();
        List<String> unsupportedType = new ArrayList<>();
        List<String> unknownColumn = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ColumnDef column : parsed.columns()) {
            if (!seen.add(column.name().toLowerCase(Locale.ROOT))) {
                duplicate.add(column.name());
            }
            if (!RW_TYPES.contains(baseType(column.type()))) {
                unsupportedType.add(column.name() + "(" + column.type() + ")");
            }
            if (sourceColumns != null && !sourceColumns.isEmpty()) {
                boolean found = sourceColumns.stream()
                        .anyMatch(c -> c.equalsIgnoreCase(column.name()));
                if (!found) {
                    unknownColumn.add(column.name());
                }
            }
        }
        List<String> missingColumns = new ArrayList<>();
        if (sourceColumns != null && !sourceColumns.isEmpty()) {
            for (String sourceColumn : sourceColumns) {
                boolean found = parsed.columns().stream()
                        .anyMatch(c -> c.name().equalsIgnoreCase(sourceColumn));
                if (!found) {
                    missingColumns.add(sourceColumn);
                }
            }
        }
        boolean columnsOk = duplicate.isEmpty() && unsupportedType.isEmpty() && unknownColumn.isEmpty();
        StringBuilder columnMessage = new StringBuilder();
        if (!duplicate.isEmpty()) {
            columnMessage.append("列名重复：").append(String.join("、", duplicate)).append("；");
        }
        if (!unsupportedType.isEmpty()) {
            columnMessage.append("RisingWave 不支持的列类型：").append(String.join("、", unsupportedType))
                    .append("（可用类型：").append(String.join("、", RW_TYPES)).append("）；");
        }
        if (!unknownColumn.isEmpty()) {
            columnMessage.append("列在源表中不存在：").append(String.join("、", unknownColumn)).append("；");
        }
        if (columnMessage.length() == 0) {
            columnMessage.append("列定义合法，共 ").append(parsed.columns().size()).append(" 列");
        }
        if (!missingColumns.isEmpty()) {
            columnMessage.append("注意：源表列 ").append(String.join("、", missingColumns))
                    .append(" 未出现在 CDC 摄取表中，这些列将无法被查询");
        }
        items.add(new CheckResult("cdc-columns", columnsOk, columnMessage.toString()));

        boolean pkOk = !parsed.primaryKeys().isEmpty();
        StringBuilder pkMessage = new StringBuilder();
        if (pkOk) {
            pkMessage.append("主键：").append(String.join("、", parsed.primaryKeys()));
        } else {
            pkMessage.append("缺少 PRIMARY KEY：mysql-cdc 摄取要求主键（upsert 键），请在列定义末尾补充 PRIMARY KEY (...)");
        }
        items.add(new CheckResult("cdc-primary-key", pkOk, pkMessage.toString()));
        return items;
    }

    /**
     * 校验第③步物化视图 DDL。
     *
     * @param ddl            DDL 文本
     * @param expectedViewName 期望的物化视图标识符
     * @param cdcTables      已摄取的 CDC 表名集合
     */
    public static List<CheckResult> validateMv(String ddl, String expectedViewName, Collection<String> cdcTables) {
        List<CheckResult> items = new ArrayList<>();
        MvDdlParsed parsed;
        try {
            parsed = parseMvDdl(ddl);
        } catch (DdlParseException e) {
            items.add(new CheckResult("mv-statement", false, "无法解析物化视图 DDL：" + e.getMessage()));
            return items;
        }
        items.add(new CheckResult("mv-statement", true, "CREATE MATERIALIZED VIEW ... AS <查询> 语句格式正确"));
        boolean nameOk = expectedViewName != null && expectedViewName.equalsIgnoreCase(parsed.viewName());
        items.add(new CheckResult("mv-name", nameOk,
                nameOk ? "物化视图标识符：" + parsed.viewName()
                        : "物化视图标识符不可修改，应为 " + expectedViewName + "（当前：" + parsed.viewName() + "）"));
        Set<String> referenced = extractTableReferences(parsed.body());
        List<String> notCdc = new ArrayList<>();
        for (String reference : referenced) {
            boolean found = cdcTables != null && cdcTables.stream()
                    .anyMatch(t -> t.equalsIgnoreCase(reference));
            if (!found) {
                notCdc.add(reference);
            }
        }
        boolean tablesOk = notCdc.isEmpty();
        items.add(new CheckResult("mv-tables", tablesOk,
                tablesOk ? "查询体仅引用已摄取的 CDC 表：" + referenced
                        : "查询体引用了未摄取的表 " + notCdc
                        + "：mysql-cdc 只能摄取物理表，请改为 CDC 表名 " + cdcTables));
        items.addAll(dialectIssues(parsed.body()));
        return items;
    }

    // ------------------------------------------------------------------ 方言检查

    /**
     * 源库（MySQL）方言与 RisingWave 的差异提示，命中即给出改写建议。
     */
    public static List<CheckResult> dialectIssues(String sql) {
        List<CheckResult> items = new ArrayList<>();
        if (isBlank(sql)) {
            return items;
        }
        String masked = maskLiterals(sql);
        for (Map.Entry<String, String> entry : DIALECT_REWRITES.entrySet()) {
            if (containsFunctionCall(masked, entry.getKey())) {
                items.add(new CheckResult("mv-dialect", false,
                        "使用了 MySQL 函数 " + entry.getKey() + "，RisingWave 不支持，建议改写为 " + entry.getValue()));
            }
        }
        if (containsThreeArgIf(masked)) {
            items.add(new CheckResult("mv-dialect", false,
                    "使用了 MySQL 三参数 IF(条件, 真值, 假值)，RisingWave 不支持，建议改写为 CASE WHEN 条件 THEN 真值 ELSE 假值 END"));
        }
        Matcher limitMatcher = Pattern.compile("\\bLIMIT\\s+(\\d+)\\s*,\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(masked);
        if (limitMatcher.find()) {
            items.add(new CheckResult("mv-dialect", false,
                    "使用了 MySQL 的 LIMIT 偏移,条数 写法（LIMIT " + limitMatcher.group(1) + "," + limitMatcher.group(2)
                            + "），RisingWave 不支持，建议改写为 LIMIT " + limitMatcher.group(2)
                            + " OFFSET " + limitMatcher.group(1)));
        }
        return items;
    }

    // ------------------------------------------------------------------ 解析实现

    public static SourceDdl parseSourceDdl(String ddl) {
        String s = normalizeStatement(ddl);
        require(s, s.toUpperCase(Locale.ROOT).startsWith("CREATE SOURCE"),
                "必须以 CREATE SOURCE 开头（当前：" + preview(s) + "）");
        int pos = skipWhitespace(s, "CREATE SOURCE".length());
        if (s.regionMatches(true, pos, "IF NOT EXISTS", 0, "IF NOT EXISTS".length())) {
            pos = skipWhitespace(s, pos + "IF NOT EXISTS".length());
        }
        Ident name = readIdentifier(s, pos);
        require(name, "缺少 source 标识符");
        pos = skipWhitespace(s, name.end());
        int withIndex = indexOfKeyword(s, "with", pos);
        require(withIndex > 0, "缺少 WITH ( ... ) 连接参数");
        int open = s.indexOf('(', withIndex);
        require(open > 0, "WITH 后缺少参数括号");
        int close = matchParen(s, open);
        require(close > 0, "WITH 参数括号未闭合");
        Map<String, String> params = parseWithParams(s.substring(open + 1, close));
        return new SourceDdl(name.name(), params.get("connector"), params.get("hostname"), params.get("port"),
                params.get("username"), params.get("password"), params.get("database.name"), params.get("server.id"));
    }

    public static CdcTableDdlParsed parseCdcTableDdl(String ddl) {
        String s = normalizeStatement(ddl);
        require(s, s.toUpperCase(Locale.ROOT).startsWith("CREATE TABLE"),
                "必须以 CREATE TABLE 开头（当前：" + preview(s) + "）");
        int pos = skipWhitespace(s, "CREATE TABLE".length());
        if (s.regionMatches(true, pos, "IF NOT EXISTS", 0, "IF NOT EXISTS".length())) {
            pos = skipWhitespace(s, pos + "IF NOT EXISTS".length());
        }
        Ident tableIdent = readIdentifier(s, pos);
        require(tableIdent, "缺少 CDC 摄取表标识符");
        pos = skipWhitespace(s, tableIdent.end());
        require(pos < s.length() && s.charAt(pos) == '(', "缺少列定义括号 (...)");
        int close = matchParen(s, pos);
        require(close > 0, "列定义括号未闭合");
        List<ColumnDef> columns = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        for (String definition : splitTopLevel(s.substring(pos + 1, close))) {
            String item = definition.trim();
            if (item.isEmpty()) {
                continue;
            }
            if (item.toUpperCase(Locale.ROOT).startsWith("PRIMARY KEY")) {
                int open = item.indexOf('(');
                int end = open > 0 ? matchParen(item, open) : -1;
                if (open > 0 && end > 0) {
                    for (String key : splitTopLevel(item.substring(open + 1, end))) {
                        Ident keyIdent = readIdentifier(key.trim(), 0);
                        if (keyIdent != null) {
                            primaryKeys.add(keyIdent.name());
                        }
                    }
                }
                continue;
            }
            Ident column = readIdentifier(item, 0);
            require(column, "无法解析列定义：" + preview(item));
            String rest = item.substring(column.end()).trim();
            int constraintAt = indexOfConstraintKeyword(rest);
            String type = constraintAt < 0 ? rest : rest.substring(0, constraintAt);
            type = type.trim();
            require(!type.isEmpty(), "列 " + column.name() + " 缺少类型");
            columns.add(new ColumnDef(column.name(), type));
            if (rest.toUpperCase(Locale.ROOT).contains("PRIMARY KEY")) {
                primaryKeys.add(column.name());
            }
        }
        String tail = s.substring(close + 1).trim();
        // 可选 WITH ( ... )：如 snapshot 等摄取参数，位于列定义与 FROM 之间
        Matcher withMatcher = Pattern.compile("^WITH\\s*\\(", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(tail);
        if (withMatcher.find()) {
            int withOpen = withMatcher.end() - 1;
            int withClose = matchParen(tail, withOpen);
            require(withClose > 0, "WITH 参数括号未闭合");
            Map<String, String> withParams = parseWithParams(tail.substring(withOpen + 1, withClose));
            String snapshot = withParams.get("snapshot");
            require(snapshot == null || "true".equalsIgnoreCase(snapshot.trim()) || "false".equalsIgnoreCase(snapshot.trim()),
                    "WITH 中 snapshot 取值必须为 true/false（当前：" + snapshot + "）");
            tail = tail.substring(withClose + 1).trim();
        }
        Matcher matcher = Pattern.compile(
                "^FROM\\s+(\"[^\"]+\"|`[^`]+`|[A-Za-z0-9_$]+)\\s+TABLE\\s+'([^']*)'",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(tail);
        require(matcher.find(), "缺少 FROM <source> TABLE '库.表' 摄取绑定（当前：" + preview(tail) + "）");
        Ident source = readIdentifier(matcher.group(1), 0);
        String bound = matcher.group(2);
        int dot = bound.lastIndexOf('.');
        String database = dot < 0 ? "" : bound.substring(0, dot);
        String table = dot < 0 ? bound : bound.substring(dot + 1);
        return new CdcTableDdlParsed(tableIdent.name(), columns, primaryKeys, source.name(), database, table);
    }

    public static MvDdlParsed parseMvDdl(String ddl) {
        String s = normalizeStatement(ddl);
        String prefix = "CREATE MATERIALIZED VIEW";
        require(s, s.toUpperCase(Locale.ROOT).startsWith(prefix),
                "必须以 CREATE MATERIALIZED VIEW 开头（当前：" + preview(s) + "）");
        int pos = skipWhitespace(s, prefix.length());
        Ident name = readIdentifier(s, pos);
        require(name, "缺少物化视图标识符");
        pos = skipWhitespace(s, name.end());
        // 可选 WITH ( ... )：如 source_rate_limit 等回填控制参数，位于视图名与 AS 之间
        if (s.regionMatches(true, pos, "WITH", 0, "WITH".length()) && !isIdentifierPart(charAt(s, pos + "WITH".length()))) {
            int open = s.indexOf('(', pos);
            require(open > 0, "WITH 后缺少参数括号");
            int close = matchParen(s, open);
            require(close > 0, "WITH 参数括号未闭合");
            pos = skipWhitespace(s, close + 1);
        }
        require(s.regionMatches(true, pos, "AS", 0, 2) && !isIdentifierPart(charAt(s, pos + 2)),
                "缺少 AS 查询体");
        String body = s.substring(pos + 2).trim();
        require(!body.isEmpty(), "AS 后缺少查询体");
        return new MvDdlParsed(name.name(), body);
    }

    /**
     * 提取查询体中 FROM / JOIN 后引用的表名（含 "库.表" 的表名部分）。
     */
    public static Set<String> extractTableReferences(String sql) {
        Set<String> references = new LinkedHashSet<>();
        if (isBlank(sql)) {
            return references;
        }
        String s = stripComments(sql);
        int i = 0;
        int n = s.length();
        String lastKeyword = "";
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\'') {
                i = skipQuoted(s, i, '\'');
                lastKeyword = "";
                continue;
            }
            if (c == '"' || c == '`') {
                Ident ident = readIdentifier(s, i);
                if (ident == null) {
                    i++;
                    continue;
                }
                if (isTablePosition(lastKeyword)) {
                    references.add(qualifiedName(s, ident));
                    lastKeyword = "";
                }
                i = ident.end();
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int end = i;
                while (end < n && (Character.isLetterOrDigit(s.charAt(end)) || s.charAt(end) == '_' || s.charAt(end) == '$')) {
                    end++;
                }
                String word = s.substring(i, end);
                String lower = word.toLowerCase(Locale.ROOT);
                if (SQL_KEYWORDS.contains(lower)) {
                    lastKeyword = lower;
                } else if (isTablePosition(lastKeyword)) {
                    Ident ident = new Ident(word, end);
                    references.add(qualifiedName(s, ident));
                    lastKeyword = "";
                } else {
                    lastKeyword = "";
                }
                i = end;
                continue;
            }
            if (c == '(') {
                lastKeyword = "";
            } else if (!Character.isWhitespace(c) && c != '.') {
                lastKeyword = "";
            }
            i++;
        }
        return references;
    }

    // ------------------------------------------------------------------ 内部工具

    private record Ident(String name, int end) {
    }

    private static boolean isTablePosition(String keyword) {
        return "from".equals(keyword) || "join".equals(keyword);
    }

    /** 读取 FROM/JOIN 后的限定名（db.table 取表名部分） */
    private static String qualifiedName(String sql, Ident ident) {
        int pos = skipWhitespace(sql, ident.end());
        if (pos < sql.length() && sql.charAt(pos) == '.') {
            pos = skipWhitespace(sql, pos + 1);
            Ident qualified = readIdentifier(sql, pos);
            if (qualified != null) {
                return qualified.name();
            }
        }
        return ident.name();
    }

    private static Map<String, String> parseWithParams(String body) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String item : splitTopLevel(body)) {
            String definition = item.trim();
            if (definition.isEmpty()) {
                continue;
            }
            int eq = definition.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = definition.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = definition.substring(eq + 1).trim();
            if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
                value = value.substring(1, value.length() - 1).replace("''", "'");
            }
            params.put(key, value);
        }
        return params;
    }

    /** 顶层逗号切分（忽略括号内与字符串内的逗号） */
    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\'') {
                i = skipQuoted(text, i, '\'');
                continue;
            }
            if (c == '"' || c == '`') {
                i = skipQuoted(text, i, c);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
            i++;
        }
        parts.add(text.substring(start));
        return parts;
    }

    /** 去注释、去掉结尾分号后的语句文本 */
    private static String normalizeStatement(String ddl) {
        if (ddl == null) {
            return "";
        }
        String s = stripComments(ddl).trim();
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        require(s, !containsStatementSeparator(stripComments(ddl)),
                "只允许单条语句，不允许多语句堆叠");
        return s;
    }

    /** 判断除结尾分号外是否还存在语句分隔符 */
    private static boolean containsStatementSeparator(String sql) {
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'') {
                i = skipQuoted(sql, i, '\'');
                continue;
            }
            if (c == '"' || c == '`') {
                i = skipQuoted(sql, i, c);
                continue;
            }
            if (c == ';') {
                return !sql.substring(i + 1).trim().isEmpty();
            }
            i++;
        }
        return false;
    }

    /** 去掉行注释与块注释，保留字符串字面量内容 */
    public static String stripComments(String sql) {
        if (sql == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                int end = skipQuoted(sql, i, c);
                out.append(sql, i, end);
                i = end;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** 把字符串字面量内容替换为空格，避免方言检查误伤字面量中的函数名 */
    private static String maskLiterals(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'') {
                int end = skipQuoted(sql, i, '\'');
                out.append(' ');
                for (int k = i + 1; k < end - 1; k++) {
                    out.append(sql.charAt(k) == '\n' ? '\n' : ' ');
                }
                out.append(' ');
                i = end;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean containsFunctionCall(String sql, String functionName) {
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(functionName) + "\\s*\\(", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(sql).find();
    }

    /** 识别 MySQL 三参数 IF(cond, a, b)：括号内有 2 个顶层逗号 */
    private static boolean containsThreeArgIf(String sql) {
        Pattern pattern = Pattern.compile("\\bIF\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            int open = matcher.end() - 1;
            int close = matchParen(sql, open);
            if (close <= 0) {
                continue;
            }
            int commas = 0;
            for (String part : splitTopLevel(sql.substring(open + 1, close))) {
                if (!part.trim().isEmpty()) {
                    commas++;
                }
            }
            if (commas == 3) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfKeyword(String sql, String keyword, int from) {
        int i = from;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'') {
                i = skipQuoted(sql, i, '\'');
                continue;
            }
            if (c == '"' || c == '`') {
                i = skipQuoted(sql, i, c);
                continue;
            }
            if (Character.isLetter(c) && sql.regionMatches(true, i, keyword, 0, keyword.length())
                    && !isIdentifierPart(charAt(sql, i - 1)) && !isIdentifierPart(charAt(sql, i + keyword.length()))) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /** 列定义中约束关键字的位置（类型名之后） */
    private static int indexOfConstraintKeyword(String text) {
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\'') {
                i = skipQuoted(text, i, '\'');
                continue;
            }
            if (Character.isLetter(c)) {
                int end = i;
                while (end < n && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_')) {
                    end++;
                }
                String word = text.substring(i, end).toLowerCase(Locale.ROOT);
                if (CONSTRAINT_KEYWORDS.contains(word)) {
                    return i;
                }
                i = end;
                continue;
            }
            i++;
        }
        return -1;
    }

    private static Ident readIdentifier(String text, int from) {
        if (text == null) {
            return null;
        }
        int i = from;
        int n = text.length();
        while (i < n && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= n) {
            return null;
        }
        char c = text.charAt(i);
        if (c == '"' || c == '`') {
            int end = skipQuoted(text, i, c);
            if (end <= i + 1) {
                return null;
            }
            String name = text.substring(i + 1, end - 1);
            if (c == '"') {
                name = name.replace("\"\"", "\"");
            } else {
                name = name.replace("``", "`");
            }
            return new Ident(name, end);
        }
        if (Character.isLetter(c) || c == '_') {
            int end = i;
            while (end < n && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_' || text.charAt(end) == '$')) {
                end++;
            }
            return new Ident(text.substring(i, end), end);
        }
        return null;
    }

    private static int skipQuoted(String text, int start, char quote) {
        int i = start + 1;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && quote != '`') {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < n && text.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return n;
    }

    /** 括号匹配，返回右括号下标；未闭合返回 -1 */
    private static int matchParen(String text, int openIndex) {
        int depth = 0;
        int i = openIndex;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\'') {
                i = skipQuoted(text, i, '\'');
                continue;
            }
            if (c == '"' || c == '`') {
                i = skipQuoted(text, i, c);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static char charAt(String text, int index) {
        return index >= 0 && index < text.length() ? text.charAt(index) : ' ';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /** 取类型的基础部分（去掉长度/精度参数） */
    static String baseType(String type) {
        if (type == null) {
            return "";
        }
        String t = type.trim().toLowerCase(Locale.ROOT);
        int paren = t.indexOf('(');
        if (paren > 0) {
            t = t.substring(0, paren).trim();
        }
        return t;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new DdlParseException(message);
        }
    }

    /** 语法片段 + 条件：不满足时抛解析异常 */
    private static void require(String fragment, boolean condition, String message) {
        if (!condition) {
            throw new DdlParseException(message);
        }
    }

    /** 解析结果非空校验 */
    private static void require(Object parsed, String message) {
        if (parsed == null) {
            throw new DdlParseException(message);
        }
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }
}
