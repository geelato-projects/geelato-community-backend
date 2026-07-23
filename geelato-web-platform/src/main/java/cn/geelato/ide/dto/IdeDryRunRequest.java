package cn.geelato.ide.dto;

import lombok.Data;

/**
 * dry-run 请求。
 *
 * @author geelato
 */
@Data
public class IdeDryRunRequest {
    /** 语言：js/python/wasm */
    private String language;
    /** 脚本内容（js/python 源 / wasm base64） */
    private String content;
    /** 执行参数（任意 JSON） */
    private Object parameter;
    /** 超时毫秒（默认 5000） */
    private Long timeoutMs;
    /** 是否捕获输出 */
    private Boolean captureOutput;
    /** 是否开启断点调试（仅 worker geelato.worker.graal.debug-enabled=true 时生效） */
    private Boolean debugMode;
}
