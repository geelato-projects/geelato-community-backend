package cn.geelato.web.platform.arco.select;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author diabl
 * @description: Arco Design select
 * @date 2023/6/19 10:56
 */
@Setter
@Getter
public class SelectOptionData<E> implements Serializable {
    private Boolean disabled = false;//是否禁用
    private String value;//选项值
    private String label;//选项内容
    private E data;// 存放可能存在的数据

}
