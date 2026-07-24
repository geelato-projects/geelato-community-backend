package cn.geelato.web.platform.srv.excel.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * 复杂导入 - 表格定义（固定位置单元格）
 * <p>
 * 对应前端 ComplexCellData，用于定位Excel中固定位置的单元格值（如 A2=客户单号）。
 * 合并单元格用区间表示，如 A1（单格）、A1:D2（合并区域）。
 *
 * @author diabl
 */
@Getter
@Setter
public class ComplexCellData {
    // 字段名称（中文）
    private String name;
    // 字段英文
    private String fieldName;
    // 数值类型 (STRING/NUMBER/BOOLEAN/DATETIME)
    private String valueType;
    // 位置（如 A1，或区间 A1:D2）
    private String position;
    // 备注
    private String remark;
}
