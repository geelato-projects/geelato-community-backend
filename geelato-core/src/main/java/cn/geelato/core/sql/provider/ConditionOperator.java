package cn.geelato.core.sql.provider;

import cn.geelato.core.experiment.ExperimentFeatures;
import cn.geelato.core.experiment.ExperimentGate;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.meta.model.parser.FunctionParser;
import cn.geelato.core.meta.model.parser.FuzzymatchSupport;
import com.alibaba.fastjson2.JSONArray;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

enum ConditionOperator implements ConditionAppender {
    EQ {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
            sb.append("?");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            if ("JSON".equals(fm.getColumnMeta().getDataType())) {
                sb.append(String.format(" JSON_CONTAINS( %s->'$','%s') >0", fm.getColumnName(), "\"" + filter.getValue() + "\""));
            } else {
                provider.tryAppendKeywords(em, sb, fm);
                sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
                sb.append("?");
            }
        }
    },
    NEQ {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
            sb.append("?");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            if ("JSON".equals(fm.getColumnMeta().getDataType())) {
                sb.append(String.format(" JSON_CONTAINS( %s->'$','%s') >0", fm.getColumnName(), "\"" + filter.getValue() + "\""));
            } else {
                provider.tryAppendKeywords(em, sb, fm);
                sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
                sb.append("?");
            }
        }
    },
    LT {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
            sb.append("?");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            if ("JSON".equals(fm.getColumnMeta().getDataType())) {
                sb.append(String.format(" JSON_CONTAINS( %s->'$','%s') >0", fm.getColumnName(), "\"" + filter.getValue() + "\""));
            } else {
                provider.tryAppendKeywords(em, sb, fm);
                sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
                sb.append("?");
            }
        }
    },
    LTE {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
            sb.append("?");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            if ("JSON".equals(fm.getColumnMeta().getDataType())) {
                sb.append(String.format(" JSON_CONTAINS( %s->'$','%s') >0", fm.getColumnName(), "\"" + filter.getValue() + "\""));
            } else {
                provider.tryAppendKeywords(em, sb, fm);
                sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
                sb.append("?");
            }
        }
    },
    GT {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            if (tryAppendFuzzymatchRewrite(provider, sb, em, fm, filter)) {
                return;
            }
            appendFunctionOrField(provider, sb, em, fm);
            sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
            sb.append("?");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            if ("JSON".equals(fm.getColumnMeta().getDataType())) {
                sb.append(String.format(" JSON_CONTAINS( %s->'$','%s') >0", fm.getColumnName(), "\"" + filter.getValue() + "\""));
            } else {
                provider.tryAppendKeywords(em, sb, fm);
                sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
                sb.append("?");
            }
        }
    },
    GTE {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
            sb.append("?");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            if ("JSON".equals(fm.getColumnMeta().getDataType())) {
                sb.append(String.format(" JSON_CONTAINS( %s->'$','%s') >0", fm.getColumnName(), "\"" + filter.getValue() + "\""));
            } else {
                provider.tryAppendKeywords(em, sb, fm);
                sb.append(MetaBaseSqlProvider.convertToSignString(filter.getOperator()));
                sb.append("?");
            }
        }
    },
    START_WITH {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            sb.append(" like CONCAT('',?,'%')");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            provider.tryAppendKeywords(em, sb, fm);
            sb.append(" like CONCAT('',?,'%')");
        }
    },
    END_WITH {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            sb.append(" like CONCAT('%',?,'')");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            provider.tryAppendKeywords(em, sb, fm);
            sb.append(" like CONCAT('%',?,'')");
        }
    },
    CONTAINS {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            sb.append(" like CONCAT('%',?,'%')");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            provider.tryAppendKeywords(em, sb, fm);
            sb.append(" like CONCAT('%',?,'%')");
        }
    },
    IN {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            Object[] ary = filter.getValueAsArray();
            sb.append(" in(");
            sb.append(cn.geelato.utils.StringUtils.join(ary.length, "?", ","));
            sb.append(")");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            provider.tryAppendKeywords(em, sb, fm);
            Object[] ary = filter.getValueAsArray();
            sb.append(" in(");
            sb.append(cn.geelato.utils.StringUtils.join(ary.length, "?", ","));
            sb.append(")");
        }
    },
    NOTIN {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            Object[] ary = filter.getValueAsArray();
            sb.append(" not in(");
            sb.append(cn.geelato.utils.StringUtils.join(ary.length, "?", ","));
            sb.append(")");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            provider.tryAppendKeywords(em, sb, fm);
            Object[] ary = filter.getValueAsArray();
            sb.append(" not in(");
            sb.append(cn.geelato.utils.StringUtils.join(ary.length, "?", ","));
            sb.append(")");
        }
    },
    NIL {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            if ("1".equals(filter.getValue())) {
                sb.append(" is NULL");
            } else {
                sb.append(" is NOT NULL");
            }
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            provider.tryAppendKeywords(em, sb, fm);
            String v = StringUtils.hasText(filter.getValue()) ? filter.getValue() : "";
            if ("1".equals(v) || "true".equals(v)) {
                sb.append(" is NULL");
            } else {
                sb.append(" is NOT NULL");
            }
        }
    },
    BT {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            appendFunctionOrField(provider, sb, em, fm);
            JSONArray ja = JSONArray.parse(filter.getValue());
            String startTime = ja.get(0).toString();
            String endTime = ja.get(1).toString();
            sb.append(String.format("  between '%s' and '%s' ", startTime, endTime));
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            provider.tryAppendKeywords(em, sb, fm);
            JSONArray ja = JSONArray.parse(filter.getValue());
            String startTime = ja.get(0).toString();
            String endTime = ja.get(1).toString();
            sb.append(String.format("  between '%s' and '%s' ", startTime, endTime));
        }
    },
    FIS {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
            throw new RuntimeException("未实现Operator：fis");
        }
        @Override
        public void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter) {
            String[] parts;
            if (Pattern.matches("^\\[(\".+\")(,(\".+\"))*]$", filter.getValue())) {
                JSONArray jsonArray = JSONArray.parse(filter.getValue());
                parts = jsonArray.toArray(String.class);
            } else {
                parts = filter.getValue().split(",");
            }
            if ("JSON".equals(fm.getColumnMeta().getDataType())) {
                String dbType = provider.resolveEffectiveDbType(em);
                sb.append("(  ");
                for (int i = 0; i < parts.length; i++) {
                    sb.append(DialectConditionSql.renderJsonContains(fm.getColumnName(), parts[i], dbType));
                    if (i < parts.length - 1) {
                        sb.append("  or  ");
                    }
                }
                sb.append("  )");
            } else {
                String dbType = provider.resolveEffectiveDbType(em);
                sb.append(DialectConditionSql.renderSetMembership(parts, fm.getColumnName(), dbType));
            }
        }
    };

    /**
     * fuzzymatch 函数条件的等价改写（仅 gt 0 触发）：
     * {@code geelato.gfn_fuzzymatch(col,'kw') > 0} → {@code (col <> '' AND col REGEXP ?)}，
     * ? 绑定按 {@link FuzzymatchSupport#buildRegexPattern} 复刻原函数清洗的 pattern。
     * 内建 REGEXP 替代存储函数，消除逐行存储函数调用开销；MySQL 8.0.22+ 还会将内建函数条件
     * 下推到派生表（vt 视图）内层，存储函数条件永不享受该优化。
     * 不满足触发条件（非 fuzzymatch / 非 gt / 比较值非 0 / 参数不可确定性解析）时返回 false，
     * 走原 geelato.gfn_fuzzymatch 函数路径。
     */
    private static boolean tryAppendFuzzymatchRewrite(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
        // [experiment:search] 实验分支点 —— 毕业时删除本判定与 return false 分支，仅保留改写路径
        if (!ExperimentGate.isEnabled(ExperimentFeatures.SEARCH)) {
            return false;
        }
        if (!FuzzymatchSupport.isFuzzymatch(fm) || filter == null) {
            return false;
        }
        // 仅 fuzzymatch|gt:0（平台约定写法）触发；其他比较值保留原函数语义
        if (!"0".equals(filter.getValue())) {
            return false;
        }
        String[] ps = FuzzymatchSupport.parseParams(fm);
        if (ps == null) {
            return false;
        }
        // $self 按当前实体解析（括号组内函数条件不经 getMysqlFunction 归一，$self 原样保留）
        String colExpr = FuzzymatchSupport.resolveColumn(ps[0], em.getEntityName());
        if (colExpr == null) {
            return false;
        }
        if (em.getTableAlias() != null && !colExpr.contains(".")) {
            colExpr = em.getTableAlias() + "." + colExpr;
        }
        String pattern = FuzzymatchSupport.buildRegexPattern(ps[1]);
        String regexPredicate = renderRegexPredicate(provider.resolveEffectiveDbType(em), colExpr);
        sb.append("(").append(colExpr).append(" <> '' AND ").append(regexPredicate).append(")");
        // 绑定值经 rawValue 传递（recombine 中 rawValue 优先）；value 保持 "0" 不动，
        // 保证主查询与 count 两次生成均满足触发条件（幂等），也不改变非 0 比较值的原语义。
        filter.setValue(filter.getValue(), pattern);
        return true;
    }

    /**
     * 按方言生成正则匹配谓词（绑定 pattern）：
     * mysql {@code col REGEXP ?}（现状）；postgresql {@code col ~ ?}；
     * oracle {@code REGEXP_LIKE(col, ?)}；sqlserver 无原生正则，明确抛异常
     * （需安装 gfn_fuzzymatch 存储函数或关闭 [experiment:search] 实验开关）。
     */
    private static String renderRegexPredicate(String dbType, String colExpr) {
        if ("postgresql".equalsIgnoreCase(dbType)) {
            return colExpr + " ~ ?";
        }
        if ("oracle".equalsIgnoreCase(dbType)) {
            return "REGEXP_LIKE(" + colExpr + ", ?)";
        }
        if ("sqlserver".equalsIgnoreCase(dbType)) {
            throw new IllegalStateException(
                    "SQL Server 无原生正则匹配，gfn_fuzzymatch 条件改写不支持当前方言：" + dbType
                            + "，请安装 gfn_fuzzymatch 存储函数或关闭 [experiment:search] 实验开关");
        }
        return colExpr + " REGEXP ?";
    }

    private static void appendFunctionOrField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm) {        if (FunctionParser.isFunction(fm)) {
            String func;
            if (fm.startsWith("gfn_")) {
                func = fm;
            } else {
                String after = FunctionParser.reconstruct(fm, em.getEntityName());
                FunctionFieldValue fv = new FunctionFieldValue(after);
                func = fv.getMysqlFunction();
            }
            func = provider.rewriteFunction(func, em, CustomFunctionRewrites.Context.CONDITION);
            func = provider.qualifyFunction(func);
            if (em.getTableAlias() != null) {
                func = provider.decorateExpressionWithAlias(em, func);
            }
            sb.append(func);
            return;
        }
        FieldMeta fieldMeta = em.containsField(fm) ? em.getFieldMeta(fm) : null;
        if (fieldMeta != null) {
            provider.tryAppendKeywords(em, sb, fieldMeta);
        } else {
            if (em.getTableAlias() != null && !fm.contains(".")) {
                sb.append(em.getTableAlias());
                sb.append(".");
            }
            provider.tryAppendKeywords(sb, fm);
        }
    }

    static ConditionOperator from(FilterGroup.Operator operator) {
        return switch (operator) {
            case eq -> EQ;
            case neq -> NEQ;
            case lt -> LT;
            case lte -> LTE;
            case gt -> GT;
            case gte -> GTE;
            case startWith -> START_WITH;
            case endWith -> END_WITH;
            case contains -> CONTAINS;
            case in -> IN;
            case notin -> NOTIN;
            case nil -> NIL;
            case bt -> BT;
            case fis -> FIS;
        };
    }
}
