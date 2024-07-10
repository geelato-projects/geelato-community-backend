package cn.geelato.orm.querydsl;

import cn.geelato.core.meta.model.entity.EntityMeta;

public class ExecutableQueryOperator {
    private final EntityMeta entityMeta;

    protected QueryOperatorParameter parameter;

    public ExecutableQueryOperator(EntityMeta entityMeta){
        this.entityMeta=entityMeta;
    }
}
