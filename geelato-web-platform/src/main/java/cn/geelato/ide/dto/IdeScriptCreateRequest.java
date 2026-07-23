package cn.geelato.ide.dto;

import lombok.Data;

/**
 * 创建 IDE 脚本请求。
 *
 * @author geelato
 */
@Data
public class IdeScriptCreateRequest {
    private String code;
    private String name;
    private String groupName;
    private String language;
    private String content;
    /** wasm base64（language=wasm 时） */
    private String wasmBinaryBase64;
    private String envScope;
    private String description;
    private String defaultParams;
}
