package cn.geelato.web.platform.srv.security.entity;

import cn.geelato.utils.UUIDUtils;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * 编码，模板项
 */
@Getter
@Setter
public class EncodingItem {
    // 基础
    private String id;// id、唯一值
    private String itemType;// 模板项类型
    private Long seqNo;// 排序
    // 常量 constant
    private String constantValue;// 常量值
    // 日期 date
    private String dateType;// 日期格式
    // 序列号 serial
    private Integer serialDigit;// 位数
    private String serialType;// 顺序、随机
    private boolean coverPos = true;// 顺序补位 0
    private String randomRange = UUIDUtils.CHARS_NUMBER;// 随机范围
}
