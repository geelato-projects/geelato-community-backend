package cn.geelato.web.platform.srv.arco.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author diabl
 * Arco Design select group
 */
public class SelectOptionGroup implements Serializable {
    private boolean isGroup = true;// 是否为选项组
    @Setter
    @Getter
    private String label;// 选项组标题
    @Setter
    @Getter
    private SelectOptionData[] options;// 选项组中的选项
    @Setter
    @Getter
    private Object other;// 其他数据

    public boolean getIsGroup() {
        return this.isGroup;
    }

    public void setIsGroup(boolean isGroup) {
        this.isGroup = isGroup;
    }
}
