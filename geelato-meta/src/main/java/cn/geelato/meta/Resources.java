package cn.geelato.meta;

import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;

/**
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_resources",catalog = "platform")
@Title(title = "资源信息")
public class Resources extends Attachment {

    public Resources() {
        super();
    }

    public Resources(File file) throws IOException {
        super(file);
    }
}
