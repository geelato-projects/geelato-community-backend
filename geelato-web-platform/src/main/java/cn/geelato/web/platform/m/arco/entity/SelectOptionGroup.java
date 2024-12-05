package cn.geelato.web.platform.m.arco.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author diabl
 * @description: Arco Design select group
 */
@Setter
@Getter
public class SelectOptionGroup implements Serializable {
    private String isGroup;// 是否为选项组
    private String label;// 选项组标题
    private SelectOptionData[] options;// 选项组中的选项
    private Object other;// 其他数据
}
