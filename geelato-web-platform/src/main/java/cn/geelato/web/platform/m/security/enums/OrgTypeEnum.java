package cn.geelato.web.platform.m.security.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
public enum OrgTypeEnum {
    ROOT("根组织", "root"),
    COMPANY("公司", "company"),
    DEPT("部门", "department"),
    FACTORY("工厂", "factory"),
    OFFICE("办事处", "office");

    private final String label;// 选项内容
    private final String value;// 选项值

    OrgTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
