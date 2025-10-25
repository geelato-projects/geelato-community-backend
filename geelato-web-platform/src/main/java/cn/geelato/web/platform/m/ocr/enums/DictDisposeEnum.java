package cn.geelato.web.platform.m.ocr.enums;

import cn.geelato.meta.DictItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum DictDisposeEnum {
    // 完全相等（输入值等于字典key值），用字典value值替换输入值
    EQUALS {
        /**
         * 一对一，字典
         */
        @Override
        public String dispose(String content, List<DictItem> list) {
            for (DictItem dictItem : list) {
                if (content.equals(dictItem.getItemName())) {
                    return dictItem.getItemCode();
                }
            }
            return null;
        }

        /**
         * 一对一，静态字典
         */
        @Override
        public String dispose(String content, Map<String, String> map) {
            return map.get(content);
        }

        /**
         * 多对一，字典
         */
        @Override
        public List<String> dispose(List<String> contents, List<DictItem> list) {
            List<String> codes = new ArrayList<>();
            for (DictItem dictItem : list) {
                if (contents.contains(dictItem.getItemName())) {
                    codes.add(dictItem.getItemCode());
                }
            }
            return codes;
        }
    },
    // 部分包含（输入值包含字典key值），用字典value值替换包含的部分
    CONTAINS {
        /**
         * 一对一，字典
         */
        @Override
        public String dispose(String content, List<DictItem> list) {
            for (DictItem dictItem : list) {
                if (content.contains(dictItem.getItemName())) {
                    return content.replaceAll(dictItem.getItemName(), dictItem.getItemCode());
                }
            }
            return null;
        }

        /**
         * 一对一，静态字典
         */
        @Override
        public String dispose(String content, Map<String, String> map) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (content.contains(entry.getKey())) {
                    return content.replaceAll(entry.getKey(), entry.getValue());
                }
            }
            return null;
        }

        /**
         * 多对一，字典
         */
        @Override
        public List<String> dispose(List<String> contents, List<DictItem> list) {
            List<String> codes = new ArrayList<>();
            for (String content : contents) {
                for (DictItem dictItem : list) {
                    if (content.contains(dictItem.getItemName())) {
                        codes.add(content.replaceAll(dictItem.getItemName(), dictItem.getItemCode()));
                    }
                }
            }
            return codes;
        }
    },
    // 部分包含（输入值包含字典key值），用字典value值替换输入值
    CONTAINS_ALL {
        /**
         * 一对一，字典
         */
        @Override
        public String dispose(String content, List<DictItem> list) {
            for (DictItem dictItem : list) {
                if (content.contains(dictItem.getItemName())) {
                    return dictItem.getItemCode();
                }
            }
            return null;
        }

        /**
         * 一对一，静态字典
         */
        @Override
        public String dispose(String content, Map<String, String> map) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (content.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return null;
        }

        /**
         * 多对一，字典
         */
        @Override
        public List<String> dispose(List<String> contents, List<DictItem> list) {
            List<String> codes = new ArrayList<>();
            for (String content : contents) {
                for (DictItem dictItem : list) {
                    if (content.contains(dictItem.getItemName())) {
                        codes.add(dictItem.getItemCode());
                    }
                }
            }
            return codes;
        }
    };

    /**
     * 一对一，字典
     */
    public abstract String dispose(String content, List<DictItem> list);

    /**
     * 一对一，静态字典
     */
    public abstract String dispose(String content, Map<String, String> map);

    /**
     * 多对一，字典
     */
    public abstract List<String> dispose(List<String> contents, List<DictItem> list);

    /**
     * 根据名称查找对应的枚举值
     *
     * @param name 枚举名称
     * @return 对应的枚举值，如果未找到则返回null
     */
    public static DictDisposeEnum lookUp(String name) {
        for (DictDisposeEnum dde : DictDisposeEnum.values()) {
            if (dde.name().equalsIgnoreCase(name)) {
                return dde;
            }
        }
        return null;
    }
}
