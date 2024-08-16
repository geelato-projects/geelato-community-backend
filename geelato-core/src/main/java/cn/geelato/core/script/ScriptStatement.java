package cn.geelato.core.script;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author geemeta
 * @description 模板中的一段语句，如：一个javascript function、或一段sql
 */
@Getter
@Setter
public class ScriptStatement {
    private String id;
    private List<String> content;

    public String getContentString() {
        if (content == null || content.size() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(content.size());
        for (String str : content) {
            sb.append(str);
            sb.append("\r\n");
        }
        return sb.toString().trim();
    }
}
