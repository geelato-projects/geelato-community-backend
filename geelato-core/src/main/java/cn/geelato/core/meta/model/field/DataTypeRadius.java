package cn.geelato.core.meta.model.field;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @description: 数据库类型范围
 * @date 2023/6/20 14:29
 */
@Getter
@Setter
public class DataTypeRadius {

    // 字符串：最大长度；数值：最大值；
    private Long max;
    // 数值：最小值。
    private Long min;
    // 数值：有符号下整数位。
    private Integer digit;
    // 数值：无符号下整数位。
    private Integer unDigit;
    // 数值：小数位。
    private Integer precision;

    public DataTypeRadius() {
    }

    public DataTypeRadius(Long max, Long min, Integer digit, Integer unDigit, Integer precision) {
        this.max = max;
        this.min = min;
        this.digit = digit;
        this.unDigit = unDigit;
        this.precision = precision;
    }
}
