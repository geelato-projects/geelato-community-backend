package cn.geelato.core.mql.spi;

public interface MqlSaveFieldValueFiller {

    boolean isEnabled();

    void fill(MqlSaveFieldValueFillContext context);
}
