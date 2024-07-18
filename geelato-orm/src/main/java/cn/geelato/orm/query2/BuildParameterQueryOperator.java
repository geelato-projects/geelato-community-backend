package cn.geelato.orm.query2;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.orm.querydsl.QueryResultOperator;
import cn.geelato.orm.querydsl.ResultWrapper;

import javax.management.Query;
import java.util.ArrayList;
import java.util.List;

public class BuildParameterQueryOperator extends QueryOperator {

    MetaManager metaManager=MetaManager.singleInstance();
    private QueryOperatorParameter queryOperatorParameter;

    public BuildParameterQueryOperator(String entityName) {
        super(entityName);
        EntityMeta entityMeta= metaManager.getByEntityName(entityName);
        buildQueryOperatorParameter(entityMeta);
    }

    private void buildQueryOperatorParameter(EntityMeta entityMeta) {
        queryOperatorParameter=new QueryOperatorParameter();
        queryOperatorParameter.setFrom(entityMeta.getTableName());
    }

    @Override
    public QueryOperator select(String... columns) {
        queryOperatorParameter.setSelect(List.of(columns));
        return this;
    }

    @Override
    public QueryOperator where(String... conditions) {
        queryOperatorParameter.setWhere(List.of(conditions));
        return this;
    }
}
