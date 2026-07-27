package cn.geelato.ide.dto;

import lombok.Data;

/**
 * 更新 IDE 脚本请求（乐观锁：客户端必须传 version）。
 *
 * @author geelato
 */
@Data
public class IdeScriptUpdateRequest {
    private String name;
    private String groupName;
    private String language;
    private String content;
    /** wasm base64（language=wasm 时） */
    private String wasmBinaryBase64;
    private String envScope;
    private String description;
    private String defaultParams;
    /** 乐观锁：客户端从上一次读取拿到的 version，必须与服务端一致才允许更新。 */
    private Integer version;
}
