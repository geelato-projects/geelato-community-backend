package cn.geelato.meta;

import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

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

    public Compress(File file) throws IOException {
        super(file);
    }
}
