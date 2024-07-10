package cn.geelato.orm.gql.parser;

/**
 * @author diabl
 * @description: 分页查询参数
 * @date 2024/3/29 13:57
 */
public class PageQueryRequest {
    private int pageNum;
    private int pageSize;
    private String orderBy;

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
}
