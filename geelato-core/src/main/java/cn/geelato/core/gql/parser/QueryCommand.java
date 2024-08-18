package cn.geelato.core.gql.parser;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.map.HashedMap;

import java.util.Map;

/**
 * @author geemeta
 */
@Setter
@Getter
public class QueryCommand extends BaseCommand<QueryCommand> {

    /**
     * -- GETTER --
     *  是查询单条记录还是多条记录，默认值为false，即查询单条记录
     */
    private boolean queryForList = false;
    private int pageNum = -1;
    private int pageSize = -1;
    /**
     * 查询字段重命名
     */
    private HashedMap alias = new HashedMap();
    /**
     * 查询语句
     */
    private String selectSql;
    private String groupBy;
    private String orderBy;
    private FilterGroup having;
    private String ACL;  //access control  list

    public QueryCommand() {
        setCommandType(CommandType.Query);
    }
    public boolean isPagingQuery() {
        return pageNum > 0 && pageSize > 0;
    }


}
