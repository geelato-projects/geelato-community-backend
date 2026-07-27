package cn.geelato.ide.entity;

/**
 * IDE 脚本状态。
 * <p>
 * DRAFT     - 草稿（默认）
 * PUBLISHED - 已发布（可被生产环境调用）
 * ARCHIVED  - 已归档（不再可用，但保留历史）
 *
 * @author geelato
 */
public final class IdeScriptStatus {
    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String ARCHIVED = "ARCHIVED";

    private IdeScriptStatus() {
    }

    public static boolean isValid(String status) {
        return DRAFT.equals(status) || PUBLISHED.equals(status) || ARCHIVED.equals(status);
    }
}
