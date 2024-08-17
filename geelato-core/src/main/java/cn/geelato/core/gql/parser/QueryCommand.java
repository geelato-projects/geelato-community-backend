package cn.geelato.core.gql.parser;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.map.HashedMap;

import java.util.Map;

/**
 * @author geemeta
 */
public class QueryCommand extends BaseCommand<QueryCommand> {

    /**
     * -- GETTER --
     *  是查询单条记录还是多条记录，默认值为false，即查询单条记录
     */
    @Setter
    @Getter
    private boolean queryForList = false;

    @Getter
    @Setter
    private int pageNum = -1;

    @Getter
    @Setter
    private int pageSize = -1;

    /**
     * 查询字段重命名
     */
    @Setter
    @Getter
    private Map alias = new HashedMap(10);

    /**
     * 查询语句
     */
    @Setter
    @Getter
    private String selectSql;
    @Setter
    @Getter
    private String groupBy;
    @Setter
    @Getter
    private String orderBy;
    @Setter
    @Getter
    private FilterGroup having;
    private String ACL;  //access control  list

    public QueryCommand() {
        setCommandType(CommandType.Query);
    }

    public boolean isPagingQuery() {
        return pageNum > 0 && pageSize > 0;
    }


}
