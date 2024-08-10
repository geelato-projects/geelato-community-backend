package cn.geelato.web.platform.m.excel.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 * @description: 存储特色元数据计算方式
 * @date 2023/10/16 14:36
 */
@Setter
@Getter
public class ConditionMeta {
    //取值计算方式
    private String evaluation;
    //变量值
    private String variable;
    //数据字典，字典编码
    private String dictCode;
    //主键，表格名称
    private String tableName;
    // 主键，目标字段
    private String goalName;
    //主键，字段名称
    private List<String> columnNames = new ArrayList<>();
    //变量对应的业务数据集合
    private List<String> values;

}
