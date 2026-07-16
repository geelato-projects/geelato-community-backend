package cn.geelato.core.meta.spi;

public interface EntitySaveFieldValueFiller {

    boolean isEnabled();

    void fill(EntitySaveFieldValueFillContext context);
}
