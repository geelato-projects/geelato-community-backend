package cn.geelato.ide.dto;

import lombok.Data;

/**
 * IDE 脚本列表查询请求。
 *
 * @author geelato
 */
@Data
public class IdeScriptListRequest {
    private String code;
    private String name;
    private String groupName;
    private String language;
    private String status;
    private String envScope;
    private String keyword;
    private Integer page = 1;
    private Integer size = 20;
}
