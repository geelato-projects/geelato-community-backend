package cn.geelato.orm.meta.model.field;

/**
 * @author diabl
 * @description: 数据库类型范围
 * @date 2023/6/20 14:29
 */
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

    public Long getMax() {
        return max;
    }

    public void setMax(Long max) {
        this.max = max;
    }

    public Long getMin() {
        return min;
    }

    public void setMin(Long min) {
        this.min = min;
    }

    public Integer getDigit() {
        return digit;
    }

    public void setDigit(Integer digit) {
        this.digit = digit;
    }

    public Integer getUnDigit() {
        return unDigit;
    }

    public void setUnDigit(Integer unDigit) {
        this.unDigit = unDigit;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
    }
}
