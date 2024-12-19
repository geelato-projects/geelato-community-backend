package cn.geelato.web.platform.common;

import cn.geelato.utils.SnowFlake;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.utils.UUIDUtils;
import cn.geelato.web.oss.*;
import cn.geelato.web.oss.ali.AliFileObjectSrvProvider;
import cn.geelato.web.oss.ali.AliOSSConfiguration;
import cn.geelato.web.platform.boot.properties.OSSConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class FileHelper {
    FileObjectSrvProvider fileObjectSrvProvider;
    @Autowired
    public FileHelper(OSSConfigurationProperties ossConfigurationProperties) {
        AliOSSConfiguration aliOSSConfiguration = new AliOSSConfiguration()
                .setAccessKeyId(ossConfigurationProperties.getAccessKeyId())
                .setAccessKeySecret(ossConfigurationProperties.getAccessKeySecret())
                .setEndPoint(ossConfigurationProperties.getEndPoint())
                .setRegion(ossConfigurationProperties.getRegion())
                .setBucketName(ossConfigurationProperties.getBucketName())
                .setGenerateObjectNameFn((fileMeta) -> generateFilePrefix() + UIDGenerator.generate());
        fileObjectSrvProvider = new AliFileObjectSrvProvider(aliOSSConfiguration);
    }

    public OSSResult putFile(MultipartFile file) throws IOException {
        return putFile(file.getOriginalFilename(),file.getInputStream());
    }
    public OSSResult putFile(String name, InputStream inputStream){
        FileMeta fileMeta=new FileMeta(name,inputStream);
        return fileObjectSrvProvider.putFile(fileMeta);
    }





    public OSSResult getFile(String objectName) {
        return fileObjectSrvProvider.getFile(objectName);
    }

    private String generateFilePrefix() {
        return "";
    }
}

