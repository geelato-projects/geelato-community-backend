package cn.geelato.web.platform.arco.select;

import java.io.Serializable;

/**
 * @author diabl
 * @description: Arco Design select
 * @date 2023/6/19 10:56
 */
public class SelectOptionData<E> implements Serializable {
    private Boolean disabled = false;//是否禁用
    private String value;//选项值
    private String label;//选项内容
    private E data;// 存放可能存在的数据

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public E getData() {
        return data;
    }

    public void setData(E data) {
        this.data = data;
    }
}
