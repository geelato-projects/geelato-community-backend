package cn.geelato.web.platform.m.excel.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
public class WordTagMeta {
    public static final String TYPE_START = "start";
    public static final String TYPE_END = "end";
    private String type;
    private int index = 0;
    private int position = -1;
    private String identify;
}
