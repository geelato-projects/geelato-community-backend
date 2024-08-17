package cn.geelato.core.script;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 模板中的一段语句，如：一个javascript function、或一段sql
 *
 * @author geemeta
 */
@Setter
@Getter
public class ScriptStatement {
    private String id;
    private List<String> content;

    public String getContentString() {
        if (content == null || content.isEmpty()) {
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
