package cn.geelato.core.meta.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by hongxueqian on 14-3-23.
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface Col {

    /**
     * @return (Optional) The name of the column. Defaults to the property or field name.
     */
    String name();

    /**
     * (Optional) Whether the column is a unique key.  This is a
     * shortcut for the <code>UniqueConstraint</code> annotation at the entity
     * level and is useful for when the unique key constraint
     * corresponds to only a single column. This constraint applies
     * in addition to any constraint entailed by primary key mapping and
     * to constraints specified at the entity level.
     *
     * @return unique
     */
    boolean unique() default false;

    /**
     * (Optional) Whether the database column is nullable.
     *
     * @return nullable
     */
    boolean nullable() default true;


//        1.BIT[M]
//            位字段类型，M表示每个值的位数，范围从1到64，如果M被忽略，默认为1
//        2.TINYINT[(M)] [UNSIGNED] [ZEROFILL]  M默认为4
//            很小的整数。带符号的范围是-128到127。无符号的范围是0到255。
//        3. BOOL，BOOLEAN
//            是TINYINT(1)的同义词。zero值被视为假。非zero值视为真。
//        4.SMALLINT[(M)] [UNSIGNED] [ZEROFILL] M默认为6
//            小的整数。带符号的范围是-32768到32767。无符号的范围是0到65535。
//        5.MEDIUMINT[(M)] [UNSIGNED] [ZEROFILL] M默认为9
//            中等大小的整数。带符号的范围是-8388608到8388607。无符号的范围是0到16777215。
//        6. INT[(M)] [UNSIGNED] [ZEROFILL]   M默认为11
//            普通大小的整数。带符号的范围是-2147483648到2147483647。无符号的范围是0到4294967295。
//        7.BIGINT[(M)] [UNSIGNED] [ZEROFILL] M默认为20
//            大整数。带符号的范围是-9223372036854775808到9223372036854775807。无符号的范围是0到18446744073709551615。
//            ---------------------
//         注意：这里的M代表的并不是存储在数据库中的具体的长度，以前总是会误以为int(3)只能存储3个长度的数字，int(11)就会存储11个长度的数字，这是大错特错的。
//         tinyint(1) 和 tinyint(4) 中的1和4并不表示存储长度，只有字段指定zerofill是有用，
//         如tinyint(4)，如果实际值是2，如果列指定了zerofill，查询结果就是0002，左边用0来填充。
//            ---------------------
//         char是一种固定长度的类型，varchar则是一种可变长度的类型，它们的区别是：
//         char(M)类型的数据列里，每个值都占用M个字节，如果某个长度小于M，MySQL就会在它的右边用空格字符补足．
//         在varchar(M)类型的数据列里，每个值只占用刚好够用的字节再加上一个用来记录其长度的字节（即总长度为L+1字节）．
    /**
     * @return 数据类型
     */
    String dataType() default "";

    /**
     * @return charMaxlength 这里不设置默认值，在管理类中结合dataType进行默认值设定
     */
    long charMaxlength() default 0;

    /**
     * @return numericPrecision
     */
    int numericPrecision() default 20;

    /**
     * @return numericScale
     */
    int numericScale() default 0;

    /**
     * @return datetimePrecision
     */
    int datetimePrecision() default 0;

    /**
     * 外表字段，默认否
     * @return isForeignColumn
     */
    boolean isRefColumn() default false;

    /**
     * isRefColumn为true时，需要通过本表引用字段
     */
    String refLocalCol() default  "";

    /**
     * @return isForeignColumn
     */
    String refColName() default "";

    /**
     * 多张表名逗号隔开
     * @return foreignTableId
     */
    String refTables() default "";
}
