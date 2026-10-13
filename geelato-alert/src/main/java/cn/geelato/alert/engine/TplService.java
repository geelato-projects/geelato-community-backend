package cn.geelato.alert.engine;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.lang.ValueUnwrapper;
import org.graalvm.polyglot.Value;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 告警模板 helper kit（$tpl.*，无状态单例，host 对象天然不可在脚本中覆盖）：
 * 呈现组方法（fmtTime/fmtDuration/fmtAmount/ellipsis/countBy/sumBy/lineList/tableRows/htmlTable/now）
 * + 告警域字典（levelName/levelTag/domainName/workbenchUrl）。
 * 模板沙箱按 bea 设计仅注入 $ctx/$tpl/console，sql/http 结构性不可达。
 * 呈现逻辑内聚于本 kit：kit 只暴露给脚本使用，不供 Java 侧复用。
 */
@GraalService(name = "tpl", built = "false", descrption = "告警消息模板 helper 库（呈现函数 + 域字典，不可覆盖）")
public class TplService {

    private static final Map<String, String> LEVEL_NAMES = Map.of(
            "P0", "严重", "P1", "高", "P2", "预警", "P3", "提示");
    private static final Map<String, String> DOMAIN_NAMES = Map.of(
            "ODR", "订单跟进", "BL", "提单放单", "FIN", "资金收付",
            "CUS", "客户", "SLOT", "仓位商务", "QLT", "操作质量");
    private static final Map<String, String> CURRENCY_SYMBOLS = Map.of(
            "CNY", "¥", "USD", "$", "EUR", "€", "GBP", "£");
    private static final DateTimeFormatter TIME_FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_SHORT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter[] TIME_PARSERS = {
            TIME_FULL, DateTimeFormatter.ISO_LOCAL_DATE_TIME, DateTimeFormatter.ofPattern("yyyy-MM-dd")};

    private final String workbenchBaseUrl;

    public TplService(String workbenchBaseUrl) {
        this.workbenchBaseUrl = workbenchBaseUrl == null ? "" : workbenchBaseUrl.trim();
    }

    @GraalFunction(example = "$tpl.levelName('P0')", description = "级别码 -> 名称（P0 严重/P1 高/P2 预警/P3 提示）")
    public String levelName(String level) {
        return LEVEL_NAMES.getOrDefault(level, level == null ? "" : level);
    }

    @GraalFunction(example = "$tpl.levelTag('P0')", description = "级别标签 -> '[P0 严重]'（短文案用）")
    public String levelTag(String level) {
        String name = levelName(level);
        return "[" + (level == null ? "" : level) + (name.isBlank() ? "" : " " + name) + "]";
    }

    @GraalFunction(example = "$tpl.domainName('FIN')", description = "域码 -> 名称（六域）")
    public String domainName(String domain) {
        return DOMAIN_NAMES.getOrDefault(domain, domain == null ? "" : domain);
    }

    @GraalFunction(example = "$tpl.workbenchUrl($ctx.exception.id)", description = "异常工作台详情跳转链接（inapp/email/wecom 深链）")
    public String workbenchUrl(String exceptionId) {
        String base = workbenchBaseUrl.isBlank() ? "/fms/exception-center" : workbenchBaseUrl;
        return base.endsWith("/") ? base + exceptionId : base + "/" + exceptionId;
    }

    @GraalFunction(example = "$tpl.now()", description = "当前时刻（yyyy-MM-dd HH:mm:ss，缺省同 rule.time）")
    public String now() {
        return LocalDateTime.now().format(TIME_FULL);
    }

    @GraalFunction(example = "$tpl.fmtTime($ctx.exception.firstAt)", description = "时间格式化：缺省 MM-dd HH:mm；'FULL' 全格式；否则按 pattern")
    public String fmtTime(String time, Value fmt) {
        return fmtTime(time, ValueUnwrapper.toNullableString(fmt));
    }

    @GraalFunction(example = "$tpl.fmtDuration($ctx.exception.anchorElapsed)", description = "秒 -> 中文时长，如 '26 小时 12 分'")
    public String fmtDuration(Value seconds) {
        return fmtDuration(asNumber(ValueUnwrapper.toJavaObject(seconds)));
    }

    @GraalFunction(example = "$tpl.fmtAmount(125000.5, 'CNY')", description = "金额千分位 + 币种符号 -> '¥125,000.50'")
    public String fmtAmount(Value value, Value currency) {
        return fmtAmount(asNumber(ValueUnwrapper.toJavaObject(value)), ValueUnwrapper.toNullableString(currency));
    }

    @GraalFunction(example = "$tpl.ellipsis(text, 60)", description = "截断加省略号")
    public String ellipsis(String s, Value maxLen) {
        return ellipsis(s, (long) asNumber(ValueUnwrapper.toJavaObject(maxLen)));
    }

    @GraalFunction(example = "$tpl.countBy($ctx.alerts, 'level')", description = "按键计数 -> { P0: 2, P1: 5 }")
    public Object countBy(Value rows, String key) {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (Object row : ValueUnwrapper.toList(rows)) {
            if (row instanceof Map<?, ?> m) {
                String k = String.valueOf(m.get(key));
                counts.merge(k, 1L, (a, b) -> (Long) a + (Long) b);
            }
        }
        return cn.geelato.script.lang.GuestValues.toGuest(counts);
    }

    @GraalFunction(example = "$tpl.sumBy($ctx.alerts, 'detail.amountLocal')", description = "按路径求和（支持 a.b.c 路径）")
    public double sumBy(Value rows, String path) {
        double sum = 0;
        for (Object row : ValueUnwrapper.toList(rows)) {
            Object v = pathValue(row, path);
            if (v instanceof Number n) {
                sum += n.doubleValue();
            }
        }
        return sum;
    }

    @GraalFunction(example = "$tpl.lineList($ctx.alerts, a => a.bizNo + ' ' + a.hitBrief)", description = "清单逐行：对每项调 fn 返回一行文本，换行拼接")
    public String lineList(Value rows, Value fn) {
        List<String> lines = new ArrayList<>();
        for (Object row : ValueUnwrapper.toList(rows)) {
            Value r = fn.execute(row);
            lines.add(r == null || r.isNull() ? "" : r.asString());
        }
        return String.join("\n", lines);
    }

    @GraalFunction(example = "$tpl.tableRows($ctx.alerts, [{key:'bizNo',label:'单号'}])", description = "markdown 表格；列 fmt 支持 pattern/'DURATION'/'AMOUNT'")
    public String tableRows(Value rows, Value cols) {
        List<Object> columns = ValueUnwrapper.toList(cols);
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(joinLabels(columns, " | ")).append(" |\n");
        sb.append("|").append(" --- |".repeat(Math.max(1, columns.size()))).append("\n");
        for (Object row : ValueUnwrapper.toList(rows)) {
            List<String> cells = new ArrayList<>();
            for (Object col : columns) {
                cells.add(mdEscape(cellText(row, col)));
            }
            sb.append("| ").append(String.join(" | ", cells)).append(" |\n");
        }
        return sb.toString();
    }

    @GraalFunction(example = "$tpl.htmlTable($ctx.alerts, cols)", description = "HTML 表格（email 变体）")
    public String htmlTable(Value rows, Value cols) {
        List<Object> columns = ValueUnwrapper.toList(cols);
        StringBuilder sb = new StringBuilder("<table><thead><tr>");
        for (Object col : columns) {
            sb.append("<th>").append(htmlEscape(colLabel(col))).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        for (Object row : ValueUnwrapper.toList(rows)) {
            sb.append("<tr>");
            for (Object col : columns) {
                sb.append("<td>").append(htmlEscape(cellText(row, col))).append("</td>");
            }
            sb.append("</tr>");
        }
        return sb.append("</tbody></table>").toString();
    }

    // ==================== 呈现实现（仅本 kit 使用） ====================

    private String fmtTime(String time, String fmt) {
        if (time == null || time.isBlank()) {
            return "";
        }
        LocalDateTime t = parseTime(time);
        if (t == null) {
            return time;
        }
        DateTimeFormatter out = fmt == null || fmt.isBlank() ? TIME_SHORT
                : "FULL".equalsIgnoreCase(fmt) ? TIME_FULL : null;
        if (out == null) {
            try {
                out = DateTimeFormatter.ofPattern(fmt);
            } catch (IllegalArgumentException e) {
                return time;
            }
        }
        return t.format(out);
    }

    private static LocalDateTime parseTime(String time) {
        for (DateTimeFormatter f : TIME_PARSERS) {
            try {
                return LocalDateTime.parse(time, f);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String fmtDuration(double seconds) {
        long total = Math.max(0, Math.round(seconds));
        long h = total / 3600;
        long m = (total % 3600) / 60;
        if (h > 0) {
            return h + " 小时 " + m + " 分";
        }
        return m > 0 ? m + " 分" : total + " 秒";
    }

    private static String fmtAmount(double value, String currency) {
        String num = String.format(Locale.ROOT, "%,.2f", value);
        String symbol = currency == null || currency.isBlank() ? null : CURRENCY_SYMBOLS.get(currency.toUpperCase(Locale.ROOT));
        if (symbol != null) {
            return symbol + num;
        }
        return currency == null || currency.isBlank() ? num : currency.toUpperCase(Locale.ROOT) + " " + num;
    }

    private static String ellipsis(String s, long maxLen) {
        if (s == null) {
            return "";
        }
        return maxLen <= 0 || s.length() <= maxLen ? s : s.substring(0, (int) maxLen) + "…";
    }

    private static double asNumber(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /** 按 a.b.c 路径取值（行数据为 Map 链）。 */
    private static Object pathValue(Object row, String path) {
        Object current = row;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = m.get(key);
        }
        return current;
    }

    /** 单元格文本：DURATION/AMOUNT 特殊语义，其余 fmt 视为时间 pattern。 */
    private String cellText(Object row, Object col) {
        if (!(col instanceof Map<?, ?> c)) {
            return String.valueOf(pathValue(row, String.valueOf(col)));
        }
        Object raw = pathValue(row, String.valueOf(c.get("key")));
        String fmt = c.get("fmt") == null ? null : String.valueOf(c.get("fmt"));
        if (raw == null) {
            return "";
        }
        if ("DURATION".equalsIgnoreCase(fmt)) {
            return fmtDuration(asNumber(raw));
        }
        if ("AMOUNT".equalsIgnoreCase(fmt)) {
            return fmtAmount(asNumber(raw), "CNY");
        }
        return fmt == null || fmt.isBlank() ? String.valueOf(raw) : fmtTime(String.valueOf(raw), fmt);
    }

    private static String colLabel(Object col) {
        if (col instanceof Map<?, ?> c && c.get("label") != null) {
            return String.valueOf(c.get("label"));
        }
        return col instanceof Map<?, ?> c2 && c2.get("key") != null ? String.valueOf(c2.get("key")) : String.valueOf(col);
    }

    private static String joinLabels(List<Object> columns, String sep) {
        List<String> labels = new ArrayList<>();
        for (Object col : columns) {
            labels.add(colLabel(col));
        }
        return String.join(sep, labels);
    }

    private static String mdEscape(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }

    private static String htmlEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
