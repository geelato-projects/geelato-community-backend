package cn.geelato.web.platform.m.excel.entity;

/**
 * @author diabl
 * @date 2024/1/10 10:50
 */
public class WordTagMeta {
    public static final String TYPE_START = "start";
    public static final String TYPE_END = "end";
    private String type;
    private int index = 0;
    private int position = -1;
    private String identify;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getIdentify() {
        return identify;
    }

    public void setIdentify(String identify) {
        this.identify = identify;
    }
}
