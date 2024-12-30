package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.web.oss.OSSFile;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_attach")
@Title(title = "附件")
@Accessors(chain = true)
public class Attach extends Attachment {

    public Attach() {
        super();
    }

    public Attach(MultipartFile file) {
        super(file);
    }

    public Attach(File file) throws IOException {
        super(file);
    }
}
