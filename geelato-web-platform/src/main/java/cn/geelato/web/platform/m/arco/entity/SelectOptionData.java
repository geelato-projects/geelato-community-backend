package cn.geelato.web.platform.m.arco.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author diabl
 * @description: Arco Design select
 */
@Setter
@Getter
public class SelectOptionData<E> implements Serializable {
    private Boolean disabled = false;// 是否禁用
    private String value;// 选项值
    private String label;// 选项内容
    private Object other;// 存放可能存在的其他数据
    private E data;// 存放可能存在的数据

}