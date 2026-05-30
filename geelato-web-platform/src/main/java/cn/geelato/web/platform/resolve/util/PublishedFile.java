package cn.geelato.web.platform.resolve.util;

import lombok.Data;

import java.io.File;

@Data
public class PublishedFile {
    private File file;
    private String fileName;
    private String url;
}

