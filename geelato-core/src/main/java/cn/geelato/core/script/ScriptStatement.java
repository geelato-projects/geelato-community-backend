package cn.geelato.core.script;

import java.util.List;

/**
 * 模板中的一段语句，如：一个javascript function、或一段sql
 *
 * @author geemeta
 */
public class ScriptStatement {
    private String id;
    private List<String> content;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getContent() {
        return content;
    }

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

    public void setContent(List<String> content) {
        this.content = content;
    }
}
