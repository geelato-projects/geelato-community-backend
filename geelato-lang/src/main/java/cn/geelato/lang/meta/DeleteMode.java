package cn.geelato.lang.meta;

/**
 * 删除模式。
 * <p>
 * 优先级（高到低）：调用级显式（MetaFactory {@code physicalDelete(...)} / MQL {@code @physicalDelete}）
 * &gt; 实体级 {@code @Entity(deleteMode = ...)} 显式指定 显式指定 &gt; 全局默认（{@code geelato.orm.delete-mode}）。gt; 全局默认（core 的 GlobalContext，环境变量 GEELATO_DELETE_MODE 类加载时读取一次固化，默认 logic）。
 * <p>
 * {@link #AUTO} 仅用于实体注解的默认值，表示未显式指定、跟随全局默认；
 * 全局默认承载于 core 的 GlobalContext（环境变量 GEELATO_DELETE_MODE 类加载时读取一次固化，运行期不可变），
 * 且不接受 auto（语义为空）。
 */
public enum DeleteMode {

    /**
     * 未显式指定，跟随全局默认（仅实体注解使用）。
     */
    AUTO,

    /**
     * 逻辑删除：delete 转为 update，置 delStatus=1 等逻辑删除字段。
     * 实体必须含 delStatus 字段，否则删除时硬失败。
     */
    LOGIC,

    /**
     * 物理删除：生成 delete from 语句，直接删除记录。
     */
    PHYSICAL;

    /**
     * 按名称宽松解析（忽略大小写），无法解析时返回 null。
     */
    public static DeleteMode fromStringIgnoreCase(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (DeleteMode mode : values()) {
            if (mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        return null;
    }
}
