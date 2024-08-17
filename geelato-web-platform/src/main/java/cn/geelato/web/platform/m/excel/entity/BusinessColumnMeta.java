package cn.geelato.web.platform.m.excel.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @description: 表头所在的位置，对应的数据类型
 */
@Getter
@Setter
public class BusinessColumnMeta {
    private int index;
    private BusinessTypeData businessTypeData;
}