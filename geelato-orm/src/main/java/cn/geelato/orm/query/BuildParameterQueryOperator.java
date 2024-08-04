package cn.geelato.orm.query;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;

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

}
