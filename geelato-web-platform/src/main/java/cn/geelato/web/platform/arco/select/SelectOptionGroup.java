package cn.geelato.web.platform.arco.select;

import java.io.Serializable;

/**
 * @author diabl
 * @description: Arco Design select group
 * @date 2023/6/19 10:56
 */
public class SelectOptionGroup implements Serializable {
    private final Boolean isGroup = true;//是否为选项组
    private String label;//选项组标题
    private SelectOptionData[] options;//选项组中的选项

    public Boolean getGroup() {
        return isGroup;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public SelectOptionData[] getOptions() {
        return options;
    }

    public void setOptions(SelectOptionData[] options) {
        this.options = options;
    }
}
