package cn.geelato.web.platform.m.file.param;

import cn.geelato.utils.entity.Resolution;
import lombok.Getter;
import lombok.Setter;

import java.io.File;

@Getter
@Setter
public class ThumbnailResolution extends Resolution {
    private File file;
    private String path;


    public ThumbnailResolution() {
    }

    public ThumbnailResolution(Resolution resolution, File file) {
        super(resolution.getWidth(), resolution.getHeight());
        this.file = file;
    }

    public ThumbnailResolution(Resolution resolution, File file, String path) {
        super(resolution.getWidth(), resolution.getHeight());
        this.file = file;
        this.path = path;
    }
}
