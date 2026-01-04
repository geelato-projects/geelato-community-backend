package cn.geelato.core.sql.provider;

import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import com.alibaba.fastjson2.JSONArray;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

enum ConditionOperator implements ConditionAppender {
    EQ {
        @Override
        public void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter) {
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
            if ("JSON".equals(fm.getColumnMeta().getDataType())) {
                String[] parts;
                if (Pattern.matches("^\\[(\".+\")(,(\".+\"))*]$", filter.getValue())) {
                    JSONArray jsonArray= JSONArray.parse(filter.getValue());
                    parts=jsonArray.toArray(String.class);
                } else {
                    parts =  filter.getValue().split(",");
                }
                sb.append("(  ");
                for (int i=0;i<parts.length;i++){
                    sb.append(String.format(" JSON_CONTAINS( %s->'$','%s') >0", fm.getColumnName(), "\"" + parts[i] + "\""));
                    if(i<parts.length-1) {
                        sb.append("  or  ");
                    }
                }
                sb.append("  )");
            } else {
                String[] parts;
                if (Pattern.matches("^\\[(\".+\")(,(\".+\"))*]$", filter.getValue())) {
                    JSONArray jsonArray = JSONArray.parse(filter.getValue());
                    parts = jsonArray.toArray(String.class);
                } else {
                    parts = filter.getValue().split(",");
                }
                sb.append("(  ");
                for (int i = 0; i < parts.length; i++) {
                    sb.append(String.format(" FIND_IN_SET( '%s',%s) >0",  parts[i] , fm.getColumnName()));
                    if (i < parts.length - 1) {
                        sb.append("  or  ");
                    }
                }
                sb.append("  )");
            }
        }
    };

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
