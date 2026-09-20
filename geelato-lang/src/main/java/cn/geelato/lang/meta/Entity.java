package cn.geelato.lang.meta;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by hongxq on 2016/5/5.
 * 如果注解的name为空，则取类名(clazz.getSimpleName())
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
public @interface Entity {
    /**
     * (Optional) The name of the model.
     * Defaults to the class name.
     * @return *
     */
    String name() default "";


    /**
     * (Optional) The name of the entity.
     *
     * Defaults to the model name.
     * @return *
     */
    String table() default "";

    /**
     * (Optional) The catalog of the entity.
     *
     * Defaults to the default catalog.
     * @return *
     */
    String catalog() default "";

    /**
     * (Optional) The schema of the entity.
     *
     * Defaults to the default schema for user.
     * @return *
     */
    String schema() default "";

    /**
     * (Optional) 数据源连接标识，对应动态数据源 key（platform_dev_db_connect 的 id）。
     * <p>
     * 作为实体数据源的显式声明，优先级高于 {@link #catalog()} 的映射。
     * 默认空表示不显式指定，回退到 catalog 映射或数据库登记值（platform_dev_table.connect_id）。
     *
     * @return 数据源 key，默认空（走默认数据源）
     */
    String connectId() default "";

    /**
     * (Optional) 删除模式，控制 delete 语句生成逻辑删除（update 置 delStatus）还是物理删除（delete from）。
     * <p>
     * 优先级：调用级显式（MetaFactory {@code physicalDelete(...)} / MQL {@code @physicalDelete}）最高，
     * 其次为该实体级配置，{@link DeleteMode#AUTO}（默认）表示跟随全局默认
     * （core 的 GlobalContext，环境变量 GEELATO_DELETE_MODE 类加载时读取一次固化，默认 logic）。
     * 显式 {@link DeleteMode#LOGIC} 时实体必须含 delStatus 字段，否则删除时硬失败。
     *
     * @return 删除模式，默认 {@link DeleteMode#AUTO}
     */
    DeleteMode deleteMode() default DeleteMode.AUTO;
}