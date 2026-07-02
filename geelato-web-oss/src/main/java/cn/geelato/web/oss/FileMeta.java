package cn.geelato.web.oss;

import cn.geelato.web.oss.helper.ContentTypeHelper;
import cn.geelato.web.oss.helper.FileExtensionHelper;
import cn.geelato.web.oss.helper.FileSizeHelper;
import lombok.Getter;
import lombok.Setter;

import java.io.BufferedInputStream;
import java.io.InputStream;

@Getter
@Setter
public class FileMeta{
    public FileMeta(String fileName, InputStream fileStream) {
        this.fileName=fileName;
        this.fileInputStream=new BufferedInputStream(fileStream);
        this.fileSize= FileSizeHelper.getFileSize(this.fileInputStream);
        this.fileContentType= ContentTypeHelper.getContentType(this.fileInputStream);
        this.fileExtension= FileExtensionHelper.getFileExtension(this.fileName);
    }
    private String fileName;
    private long fileSize;
    private BufferedInputStream fileInputStream;
    private String fileContentType;
    private String fileExtension;
}
