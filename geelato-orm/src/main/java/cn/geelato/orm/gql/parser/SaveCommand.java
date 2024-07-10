package cn.geelato.orm.gql.parser;

import java.util.Map;

/**
 * 用于数据库insert、update操作
 * @author geemeta
 *
 */
public class SaveCommand extends BaseCommand<SaveCommand> {

    // 主键的值，如19位的数字
    private String PK;
    private Map<String, Object> valueMap;

    private Map<String,Object> originValueMap;

    /**
     * 与fields同步，冗余。
     * @see #fields
     */
    public Map<String, Object> getValueMap() {
        return valueMap;
    }

    public void setValueMap(Map<String, Object> valueMap) {
        this.valueMap = valueMap;
    }

    public String getPK() {
        return PK;
    }

    public void setPK(String PK) {
        this.PK = PK;
    }

    public Map<String, Object> getOriginValueMap() {
        return originValueMap;
    }

    public void setOriginValueMap(Map<String, Object> originValueMap) {
        this.originValueMap = originValueMap;
    }
}
