package cn.geelato.web.platform.m.ocr.enums;

import org.apache.logging.log4j.util.Strings;

import java.math.BigDecimal;

public enum MetaTypeEnum {
    STRING {
        @Override
        public Object toFormat(String content) {
            return content == null ? "" : content;
        }
    }, NUMBER {
        @Override
        public Object toFormat(String content) {
            if (Strings.isBlank(content)) {
                return 0;
            } else if (content.indexOf(".") == -1) {
                if (Long.parseLong(content) < Integer.MAX_VALUE) {
                    return Integer.parseInt(content);
                } else {
                    return Long.parseLong(content);
                }
            } else {
                return new BigDecimal(content).doubleValue();
            }
        }
    }, BOOLEAN {
        @Override
        public Object toFormat(String content) {
            if ("1".equalsIgnoreCase(content) || "true".equalsIgnoreCase(content)) {
                return true;
            }
            return false;
        }
    }, DATE {
        @Override
        public Object toFormat(String content) {
            return content;
        }
    }, PICTURE {
        @Override
        public Object toFormat(String content) {
            return content;
        }
    };

    /**
     * 根据名称查找对应的元数据类型枚举。
     *
     * @param name 元数据类型的名称
     * @return 如果找到匹配的元数据类型枚举，则返回该枚举；否则返回null
     */
    public static MetaTypeEnum lookUp(String name) {
        for (MetaTypeEnum metaType : MetaTypeEnum.values()) {
            System.out.println(metaType.name());
            if (metaType.name().equalsIgnoreCase(name)) {
                return metaType;
            }
        }
        return null;
    }

    /**
     * 将给定内容转换为特定格式的对象。
     *
     * @param content 需要转换的内容
     * @return 转换后的对象
     */
    public abstract Object toFormat(String content);
}
