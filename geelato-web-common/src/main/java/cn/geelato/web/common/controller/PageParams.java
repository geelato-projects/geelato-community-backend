package cn.geelato.web.common.controller;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页参数类
 * 用于封装分页查询的页码和页面大小参数
 */
@Setter
@Getter
public class PageParams {
    private Integer pageNum;
    private Integer pageSize;
    
    public PageParams() {}
    
    public PageParams(Integer pageNum, Integer pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /**
     * 检查分页参数是否有效
     * @return 如果页码和页面大小都大于0则返回true
     */
    public boolean isValid() {
        return pageNum != null && pageSize != null && pageNum > 0 && pageSize > 0;
    }
    
    /**
     * 获取偏移量（用于数据库查询）
     * @return 偏移量
     */
    public int getOffset() {
        if (!isValid()) {
            return 0;
        }
        return (pageNum - 1) * pageSize;
    }
    
    /**
     * 获取限制数量（用于数据库查询）
     * @return 限制数量
     */
    public int getLimit() {
        return pageSize != null ? pageSize : 10;
    }
    
    /**
     * 获取当前页码（兼容性方法）
     * @return 当前页码
     */
    public long getCurrent() {
        return pageNum != null ? pageNum.longValue() : 1L;
    }
    
    /**
     * 获取页面大小（兼容性方法）
     * @return 页面大小
     */
    public long getSize() {
        return pageSize != null ? pageSize.longValue() : 10L;
    }
    
    @Override
    public String toString() {
        return "PageParams{" +
                "pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                '}';
    }
}