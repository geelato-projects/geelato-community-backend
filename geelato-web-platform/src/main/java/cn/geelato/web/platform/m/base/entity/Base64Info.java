package cn.geelato.web.platform.m.base.entity;

import java.io.File;

/**
 * @author diabl
 * @date 2023/11/12 15:27
 */
public class Base64Info {
    private String id;
    private String name;
    private String type;
    private Long size;
    private String base64;
    private File file;
    private String remark;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getBase64() {
        return base64;
    }

    public void setBase64(String base64) {
        this.base64 = base64;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public static Base64Info getBase64InfoByAttach(Attach attach) {
        Base64Info info = new Base64Info();
        if (attach != null) {
            info.setId(attach.getId());
            info.setName(attach.getName());
            info.setType(attach.getType());
            info.setSize(attach.getSize());
            File aFile = new File(attach.getPath());
            info.setFile((aFile != null && aFile.exists()) ? aFile : null);
        }
        return info;
    }
}
