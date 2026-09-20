package cn.geelato.web.platform.srv.ormhook.enums;

/**
 * ORM 钩子事件类型（三类，均为事务提交后异步触发）。
 * <p>
 * insert=新增提交后；update=更新提交后（逻辑删除以 Update 形式触发本事件，payload 的 values
 * 含 del_status=1）；delete=物理删除提交后（物理删除才产生 DeleteCommand/删除事件）。
 *
 * @author geelato
 */
public enum OrmHookEventEnum {

    /** 新增事务提交后。 */
    INSERT("insert", "新增后"),

    /** 更新事务提交后（含逻辑删除）。 */
    UPDATE("update", "更新后"),

    /** 物理删除事务提交后。 */
    DELETE("delete", "删除后");

    private final String value;
    private final String title;

    OrmHookEventEnum(String value, String title) {
        this.value = value;
        this.title = title;
    }

    public String value() {
        return value;
    }

    public String title() {
        return title;
    }

    /** 按 value 解析，未匹配返回 null。 */
    public static OrmHookEventEnum of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (OrmHookEventEnum event : values()) {
            if (event.value.equals(value)) {
                return event;
            }
        }
        return null;
    }
}
