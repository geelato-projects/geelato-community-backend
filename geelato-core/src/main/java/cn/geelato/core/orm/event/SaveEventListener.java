package cn.geelato.core.orm.event;

public interface SaveEventListener {
    void beforeSave(SaveEventContext context);
    void afterSave(SaveEventContext context);
    default boolean supports(SaveEventContext context) {
        return false;
    }
    default boolean enabled(SaveEventContext context) {
        return false;
    }
}
