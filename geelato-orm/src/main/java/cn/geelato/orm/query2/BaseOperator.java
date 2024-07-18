package cn.geelato.orm.query2;

import cn.geelato.core.gql.parser.BaseCommand;

public abstract class BaseOperator extends BaseCommand {

    protected String sql;

    abstract void buildSql();
}
