package cn.geelato.core.sql.provider;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * gfn_* 自定义函数的方言改写注册表。
 *
 * <p>MQL 内置函数经 {@code GFunction} 解析为 {@code gfn_xxx(...)} 文本后，默认走
 * {@code geelato.gfn_xxx} 存储函数路径（依赖各数据库预建存储函数）。本注册表允许将
 * <b>已知语义</b>的内置函数按方言改写为等价 SQL 表达式，消除对存储函数的依赖；
 * 未注册或改写器返回 null 时保留存储函数路径（{@code geelato.} 前缀在
 * PostgreSQL/SQL Server/Oracle 上均为合法 schema 限定语法，但需按方言自行安装函数脚本）。
 *
 * <p>上下文约束：改写器声明支持的调用上下文
 * （CONDITION 过滤条件 / SELECT 查询列 / SET 更新设值），上下文不匹配时直接保留原函数路径。
 */
public final class CustomFunctionRewrites {

    /** 函数调用上下文。 */
    public enum Context {
        /** 过滤条件（WHERE 片段中的函数表达式） */
        CONDITION,
        /** 查询列（SELECT 片段中的函数表达式） */
        SELECT,
        /** 更新/插入设值（SET 片段中的函数表达式） */
        SET
    }

    /** 单个函数的方言改写器。 */
    public interface FunctionRewrite {

        /** 是否支持该调用上下文。 */
        boolean supportContext(Context ctx);

        /**
         * 方言改写。
         *
         * @param dbType   归一后的方言（mysql/postgresql/sqlserver/oracle/...）
         * @param funcExpr 形如 {@code gfn_xxx(p1,p2)} 的函数表达式
         * @return 改写后的 SQL 表达式；null 表示该方言无改写，保留存储函数路径
         */
        String rewrite(String dbType, String funcExpr);
    }

    private static final Map<String, FunctionRewrite> REGISTRY = new HashMap<>();

    static {
        REGISTRY.put("gfn_increment", new IncrementRewrite());
        REGISTRY.put("gfn_findinset", new FindinsetRewrite());
    }

    private CustomFunctionRewrites() {
    }

    /**
     * 尝试改写。无注册项 / 上下文不支持 / 方言无改写时返回 null。
     */
    public static String rewrite(String dbType, String funcExpr, Context ctx) {
        if (dbType == null || funcExpr == null || ctx == null) {
            return null;
        }
        String name = extractFunctionName(funcExpr);
        if (name == null) {
            return null;
        }
        FunctionRewrite rewrite = REGISTRY.get(name.toLowerCase(Locale.ROOT));
        if (rewrite == null || !rewrite.supportContext(ctx)) {
            return null;
        }
        return rewrite.rewrite(dbType, funcExpr);
    }

    /** 注册或覆盖一个函数改写器（供宿主扩展）。 */
    public static void register(String functionName, FunctionRewrite rewrite) {
        if (functionName != null && rewrite != null) {
            REGISTRY.put(functionName.toLowerCase(Locale.ROOT), rewrite);
        }
    }

    private static String extractFunctionName(String funcExpr) {
        int idx = funcExpr.indexOf("(");
        if (idx <= 0) {
            return null;
        }
        return funcExpr.substring(0, idx).trim();
    }

    /**
     * {@code gfn_increment(val, step)} → {@code (val + step)}。
     * 语义为数值自增（函数体 {@code val + step}），纯算术四方言通用，全上下文可用。
     */
    static final class IncrementRewrite implements FunctionRewrite {
        @Override
        public boolean supportContext(Context ctx) {
            return true;
        }

        @Override
        public String rewrite(String dbType, String funcExpr) {
            String[] ps = parseParams(funcExpr);
            if (ps == null) {
                return null;
            }
            return "(" + ps[0].trim() + " + " + ps[1].trim() + ")";
        }
    }

    /**
     * {@code gfn_findinset(val, set)} 的条件上下文改写——保留 FIND_IN_SET 的
     * <b>数值位置语义</b>（1 起始命中位置，未命中 0），外层操作符（如 {@code = ?}、
     * {@code > 0}）的行为不变：
     * <ul>
     *   <li>mysql：不改写（保留 {@code geelato.gfn_findinset} 存储函数路径，存量行为零变化）。</li>
     *   <li>postgresql：{@code position((',' || val || ',') in (',' || set || ','))}</li>
     *   <li>sqlserver：{@code CHARINDEX(CONCAT(',', val, ','), CONCAT(',', set, ','))}</li>
     *   <li>oracle：{@code INSTR(',' || set || ',', ',' || val || ',')}</li>
     *   <li>其他方言：不改写。</li>
     * </ul>
     */
    static final class FindinsetRewrite implements FunctionRewrite {
        @Override
        public boolean supportContext(Context ctx) {
            return ctx == Context.CONDITION;
        }

        @Override
        public String rewrite(String dbType, String funcExpr) {
            String[] ps = parseParams(funcExpr);
            if (ps == null) {
                return null;
            }
            String val = ps[0].trim();
            String set = ps[1].trim();
            if ("postgresql".equalsIgnoreCase(dbType)) {
                return String.format("position((',' || %s || ',') in (',' || %s || ','))", val, set);
            }
            if ("sqlserver".equalsIgnoreCase(dbType)) {
                return String.format("CHARINDEX(CONCAT(',', %s, ','), CONCAT(',', %s, ','))", val, set);
            }
            if ("oracle".equalsIgnoreCase(dbType)) {
                return String.format("INSTR(',' || %s || ',', ',' || %s || ',')", set, val);
            }
            return null;
        }
    }

    /**
     * 按单引号感知规则拆分函数参数（单引号字符串内的逗号不切分）。
     */
    static String[] parseParams(String funcExpr) {
        int open = funcExpr.indexOf('(');
        int close = funcExpr.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        String inner = funcExpr.substring(open + 1, close);
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\'') {
                inSingle = !inSingle;
                cur.append(c);
                continue;
            }
            if (c == ',' && !inSingle) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }
}
