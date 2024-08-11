package cn.geelato.web.platform.arco.select;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author diabl
 * @description: Arco Design select group
 * @date 2023/6/19 10:56
 */
@Setter
@Getter
public class SelectOptionGroup implements Serializable {
    private String label;//选项组标题
    private SelectOptionData[] options;//选项组中的选项

    public Boolean getGroup() {
        //是否为选项组
        return true;
    }

}
