package cn.geelato.web.platform.srv.excel.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * 复杂导入 - 列表定义（重复区域）
 * <p>
 * 对应前端 ComplexListData，用于定位Excel中按行重复的列表区域。
 *
 * @author diabl
 */
@Getter
@Setter
public class ComplexListData {
    // 列表名称（中文）
    private String title;
    // 列表英文
    private String fieldName;
    // 开始列
    private String startColumn;
    // 结束列
    private String endColumn;
    // 开始行（1-based）
    private int startRow;
    // 备注
    private String remark;
}
