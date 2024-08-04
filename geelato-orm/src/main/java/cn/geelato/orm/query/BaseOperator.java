package cn.geelato.orm.query;

import cn.geelato.core.gql.parser.BaseCommand;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.sql.SqlManager;
import io.lettuce.core.dynamic.annotation.CommandNaming;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public abstract class BaseOperator<T extends QueryOP> {

    protected T t;

    @Autowired
    @Qualifier("dynamicDao")
    protected Dao dao;
    protected final SqlManager sqlManager = SqlManager.singleInstance();
    protected String tableOrView;

    protected BaseCommand baseCommand;

    public BaseOperator(String tableOrView){
        this.tableOrView=tableOrView;
        this.baseCommand=new BaseCommand<>();
        this.baseCommand.setEntityName(tableOrView);
    }

    protected abstract void parseCommand();


    public T where(String... conditions){
        this.baseCommand.setOriginalWhere(conditions[0]);
        return t;
    }


}
