package cn.geelato.web.platform.srv.arco.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author diabl
 * Arco Design select
 */
@Setter
@Getter
public class SelectOptionData<E> implements Serializable {
    private Boolean disabled = false;// 是否禁用
    private E data;// 存放可能存在的数据
    private Object other;// 存放可能存在的其他数据
    private Object value;// 选项值
    private String label;// 选项内容
    private String enLabel;// 英文选项内容
}
