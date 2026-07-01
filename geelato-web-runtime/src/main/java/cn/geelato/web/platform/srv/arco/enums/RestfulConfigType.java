package cn.geelato.web.platform.srv.arco.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
public enum RestfulConfigType {
    SQL("Structured Query Language", "sql"),
    JS("Javascript", "js");

    private final String label;// 选项内容
    private final String value;// 选项值

    RestfulConfigType(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
