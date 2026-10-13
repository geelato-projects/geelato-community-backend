package cn.geelato.script.rest;

import lombok.Getter;
import lombok.Setter;

/** 语法预检请求体（不执行）。 */
@Getter
@Setter
public class ScriptParseRequest {

    private String lang;
    private String content;
    private String engineVersion;
}
