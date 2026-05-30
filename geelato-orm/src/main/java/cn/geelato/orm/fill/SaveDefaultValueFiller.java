package cn.geelato.orm.fill;

/**
 * Strategy interface for filling default values before DSL save commands are converted to SQL.
 */
public interface SaveDefaultValueFiller {

    void fill(SaveDefaultValueContext context);
}
