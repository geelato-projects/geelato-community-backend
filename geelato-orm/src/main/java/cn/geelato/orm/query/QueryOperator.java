package cn.geelato.orm.query;


import cn.geelato.core.gql.execute.BoundPageSql;
import cn.geelato.core.gql.execute.BoundSql;
import cn.geelato.core.gql.parser.BaseCommand;
import cn.geelato.core.gql.parser.CommandType;
import cn.geelato.core.gql.parser.QueryCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.management.Query;
import java.util.List;
import java.util.Map;

@Component
public class QueryOperator extends BaseOperator<QueryOP> implements QueryOP {
    private QueryCommand queryCommand;
    public QueryOperator(String tableOrView){
        super(tableOrView);
        this.t=this;
    }

    @Override
    protected void parseCommand() {
        queryCommand=new QueryCommand();
        queryCommand.setOriginalWhere(baseCommand.getOriginalWhere());
        queryCommand.setCommandType(CommandType.Query);
        queryCommand.setEntityName(baseCommand.getEntityName());
    }


    @Override
    public <T> List<T> pageQueryList() {
        return null;
    }

    @Override
    public List<Map<String, Object>> queryForMapList() {
        parseCommand();
        BoundPageSql boundPageSql= sqlManager.generatePageQuerySql(this.queryCommand);
        return dao.queryForMapList(boundPageSql);
    }

    public QueryOperator select(String... columns){
        this.baseCommand.setFields(columns);
        return this;
    }
}
