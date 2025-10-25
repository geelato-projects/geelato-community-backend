package cn.geelato.web.platform.srv.dict.vo;

import lombok.Data;

/**
 * 字典项返回对象
 * 
 * @author system
 */
@Data
public class DictItemVO {
    
    /**
     * 字典项编码
     */
    private String itemCode;
    
    /**
     * 字典项名称
     */
    private String itemName;
    
    public DictItemVO() {
    }
    
    public DictItemVO(String itemCode, String itemName) {
        this.itemCode = itemCode;
        this.itemName = itemName;
    }
}