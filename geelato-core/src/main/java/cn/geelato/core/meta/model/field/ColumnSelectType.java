package cn.geelato.core.meta.model.field;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @description: 字段可供选择的数据类型
 */
@Getter
@Setter
public class ColumnSelectType {
    private String group;// 分组名称
    private String label;// 标题
    private String value;// 内容
    private String mysql;// 数据类型
    private Boolean disabled = false;// 是否禁用
    private Boolean fixed = false;// 是否固定长度 字符串类型
    private Long extent; // 默认长度 字符串类型
    private Long seqNo;

    private Class java;// Java数据类型对象
    private DataTypeRadius radius;// 取值范围
}
