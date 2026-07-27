package cn.geelato.ide.entity;


import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.lang.meta.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * IDE 脚本实体（JS/Python/Wasm 三语言统一存储）。
 * <p>
 * 与 platform_api 解耦：ide_script 是 IDE/AI 协作的唯一源，第一阶段完全不读写老表。
 * 文件↔DB 同步由 VS Code 插件以 git 风格 pull/push 完成（参见 M8）。
 *
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "ide_script")
@Title(title = "IDE脚本")
public class IdeScript extends BaseEntity {

    @Title(title = "业务编码", description = "脚本业务编码，租户内唯一，作为文件名主键使用。")
    @Col(name = "code", nullable = false, unique = true, charMaxlength = 128)
    private String code;

    @Title(title = "显示名")
    @Col(name = "name", nullable = false, charMaxlength = 128)
    private String name;

    @Title(title = "分组")
    @Col(name = "group_name", charMaxlength = 64)
    private String groupName;

    @Title(title = "语言", description = "js / python / wasm")
    @Col(name = "language", nullable = false, charMaxlength = 16)
    private String language = "js";

    @Title(title = "脚本正文", description = "JS/Python 源；wasm 时为 base64 字符串。")
    @Col(name = "content")
    private String content;

    @Title(title = "Wasm 存储路径", description = "language=wasm 时字节码在 OSS/本地磁盘的 objectName，DB 不存二进制。")
    @Col(name = "wasm_object_name", charMaxlength = 256)
    private String wasmObjectName;

    @Title(title = "内容哈希", description = "content 的 sha256，用于文件↔DB 同步冲突检测。")
    @Col(name = "file_hash", charMaxlength = 64)
    private String fileHash;

    @Title(title = "乐观锁版本")
    @Col(name = "version", nullable = false)
    private Integer version = 1;

    @Title(title = "状态", description = "DRAFT / PUBLISHED / ARCHIVED")
    @Col(name = "status", nullable = false, charMaxlength = 16)
    private String status = IdeScriptStatus.DRAFT;

    @Title(title = "环境范围", description = "dev / staging / prod，控制 dry-run 与 push 是否需要二次确认。")
    @Col(name = "env_scope", nullable = false, charMaxlength = 64)
    private String envScope = "dev";

    @Title(title = "描述")
    @Col(name = "description", charMaxlength = 512)
    private String description;

    @Title(title = "默认 dry-run 参数 JSON")
    @Col(name = "default_params")
    private String defaultParams;

    @Title(title = "所属应用")
    @Col(name = "app_id", charMaxlength = 32)
    private String appId;

    // ==================== 计算字段（不入库，供序列化使用） ====================

    /**
     * wasm 字节码的 base64 表示，便于 HTTP 传输。
     * <p>
     * 由 Service 在按需（getById/getByCode 显式加载）时从 OSS/本地磁盘读取并填充，
     * 列表查询不填充（避免 N 次 IO）。
     */
    @Transient
    private String wasmBinaryBase64;
}
