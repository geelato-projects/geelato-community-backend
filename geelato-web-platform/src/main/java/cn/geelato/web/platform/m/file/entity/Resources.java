package cn.geelato.web.platform.m.file.entity;

import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_resources",catalog = "platform")
@Title(title = "资源信息", description = "对应各类资源文件，如mvel规则文件，sql语句等")
public class Resources extends Attachment {

    public Resources() {
        super();
    }

    public Resources(MultipartFile file) {
        super(file);
    }

    public Resources(File file) throws IOException {
        super(file);
    }
}
