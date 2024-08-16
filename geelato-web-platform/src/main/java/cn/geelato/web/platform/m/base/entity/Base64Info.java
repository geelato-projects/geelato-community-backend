package cn.geelato.web.platform.m.base.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.File;

/**
 * @author diabl
 * @date 2023/11/12 15:27
 */
@Getter
@Setter
public class Base64Info {
    private String id;
    private String name;
    private String type;
    private Long size;
    private String base64;
    private File file;
    private String remark;

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
