package cn.geelato.core.orm.event;

public interface DeleteEventListener {
    void beforeDelete(DeleteEventContext context);
    void afterDelete(DeleteEventContext context);
    default boolean supports(DeleteEventContext context) {
        return true;
    }
    default boolean enabled(DeleteEventContext context) {
        return true;
    }
}
