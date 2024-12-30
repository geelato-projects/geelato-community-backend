package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.web.oss.OSSFile;
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
@Entity(name = "platform_resources")
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
