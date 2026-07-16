package cn.geelato.orm.spi;

public interface FluentSaveFieldValueFiller {

    boolean isEnabled();

    void fill(FluentSaveFieldValueFillContext context);
}
