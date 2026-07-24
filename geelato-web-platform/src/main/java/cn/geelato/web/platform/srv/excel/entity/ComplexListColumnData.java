package cn.geelato.web.platform.srv.excel.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * 复杂导入 - 列表字段定义（列表区域内的列）
 * <p>
 * 对应前端 ComplexListColumnData，用于定位列表区域中每列的取值。
 * 列位置：单个列填 A；跨多列的合并单元格填区间 A:C。
 *
 * @author diabl
 */
@Getter
@Setter
public class ComplexListColumnData {
    // 所属列表英文（关联 ComplexListData.fieldName）
    private String listFieldName;
    // 字段名称（中文）
    private String name;
    // 字段英文
    private String fieldName;
    // 数据类型 (STRING/NUMBER/BOOLEAN/DATETIME)
    private String valueType;
    // 列位置（如 A，或区间 A:C）
    private String position;
    // 备注
    private String remark;
}
