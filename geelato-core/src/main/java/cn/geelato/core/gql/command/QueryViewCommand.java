package cn.geelato.core.gql.command;

import cn.geelato.core.gql.filter.FilterGroup;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.map.HashedMap;

import java.util.Map;

@Setter
@Getter
public class QueryViewCommand extends BaseCommand<QueryViewCommand> {

    private boolean queryForList = true;
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
    private String orderBy;
    private FilterGroup having;
    private String viewName;

    public QueryViewCommand() {
        setCommandType(CommandType.Query);
    }

    public boolean isPagingQuery() {
        return pageNum > 0 && pageSize > 0;
    }


}
