package cn.geelato.orm.gql.parser;

import org.apache.commons.collections.map.HashedMap;

import java.util.Map;

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


    public boolean isQueryForList() {
        return queryForList;
    }

    public void setQueryForList(boolean queryForList) {
        this.queryForList = queryForList;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }


    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public FilterGroup getHaving() {
        return having;
    }

    public void setHaving(FilterGroup having) {
        this.having = having;
    }

    public Map getAlias() {
        return alias;
    }

    public void setAlias(Map alias) {
        this.alias = alias;
    }

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }
}
