package cn.geelato.script.core;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class ScriptError {

    /** SYNTAX / RUNTIME / TIMEOUT / UNKNOWN。 */
    private String type;
    private String message;
    private Integer line;
    private Integer col;
    /** polyglot 调用栈（每帧 "方法 (源名:行号)"，上限 50 帧；行号为用户脚本行号。 */
    private List<String> stack;

    public static ScriptError of(String type, String message) {
        ScriptError e = new ScriptError();
        e.setType(type);
        e.setMessage(message);
        return e;
    }

    public static ScriptError of(String type, String message, Integer line, Integer col) {
        ScriptError e = of(type, message);
        e.setLine(line);
        e.setCol(col);
        return e;
    }
}
