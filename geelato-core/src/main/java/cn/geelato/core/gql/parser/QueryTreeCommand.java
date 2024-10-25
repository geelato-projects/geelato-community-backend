package cn.geelato.core.gql.parser;

import cn.geelato.core.gql.filter.FilterGroup;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.map.HashedMap;

import java.util.Map;

/**
 * @author geemeta
 */
@Setter
@Getter
public class QueryTreeCommand extends BaseCommand<QueryTreeCommand> {

    /**
     * -- GETTER --
     *  是查询单条记录还是多条记录，默认值为false，即查询单条记录
     */
    private boolean queryForList = false;
    /**
     * @param pageNum，第几页，从1开始。
     */
    private int pageNum = -1;
    /**
     * @param pageSize 每页最大展示记录数，pageSize(客户端请求参数中)=limit(in mysql)
     */
    private int pageSize = -1;

    /**
     * 查询字段重命名
     */
    private Map alias = new HashedMap(10);

    private String groupBy;
    //orderBy中的列，应该出现在group by子句中
    private String orderBy;
    private FilterGroup having;

    public QueryTreeCommand() {
        setCommandType(CommandType.Query);
    }

    public boolean isPagingQuery() {
        return pageNum > 0 && pageSize > 0;
    }


}
