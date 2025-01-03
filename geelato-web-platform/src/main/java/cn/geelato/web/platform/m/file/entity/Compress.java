package cn.geelato.web.platform.m.file.entity;

import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Getter
@Setter
@Entity(name = "platform_compress")
@Title(title = "压缩文件")
@Accessors(chain = true)
public class Compress extends Attachment {

    public Compress() {
        super();
    }

    public Compress(MultipartFile file) {
        super(file);
    }

    public Compress(File file) throws IOException {
        super(file);
    }
}
