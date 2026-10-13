package cn.geelato.script.core;

import lombok.Getter;
import lombok.Setter;

/**
 * parseOnly 语法预检产出的问题定位（不执行脚本）。
 */
@Getter
@Setter
public class ScriptIssue {

    private String code;
    private String message;
    private Integer line;
    private Integer col;
}
