package cn.geelato.core.sql.provider;

import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;

public interface ConditionAppender {
    void appendFunction(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, String fm, FilterGroup.Filter filter);
    void appendField(MetaBaseSqlProvider<?> provider, StringBuilder sb, EntityMeta em, FieldMeta fm, FilterGroup.Filter filter);
}
