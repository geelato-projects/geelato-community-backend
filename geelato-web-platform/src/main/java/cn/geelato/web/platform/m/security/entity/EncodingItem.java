package cn.geelato.web.platform.m.security.entity;

/**
 * @author diabl
 * @description: 编码，模板项
 * @date 2023/8/2 10:51
 */
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public Long getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Long seqNo) {
        this.seqNo = seqNo;
    }

    public String getConstantValue() {
        return constantValue;
    }

    public void setConstantValue(String constantValue) {
        this.constantValue = constantValue;
    }

    public String getDateType() {
        return dateType;
    }

    public void setDateType(String dateType) {
        this.dateType = dateType;
    }

    public Integer getSerialDigit() {
        return serialDigit;
    }

    public void setSerialDigit(Integer serialDigit) {
        this.serialDigit = serialDigit;
    }

    public String getSerialType() {
        return serialType;
    }

    public void setSerialType(String serialType) {
        this.serialType = serialType;
    }

    public boolean isCoverPos() {
        return coverPos;
    }

    public void setCoverPos(boolean coverPos) {
        this.coverPos = coverPos;
    }
}
