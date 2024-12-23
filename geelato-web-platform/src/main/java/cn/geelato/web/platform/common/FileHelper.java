package cn.geelato.web.platform.common;

import cn.geelato.core.SessionCtx;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.oss.FileMeta;
import cn.geelato.web.oss.FileObjectSrvProvider;
import cn.geelato.web.oss.OSSResult;
import cn.geelato.web.oss.ali.AliFileObjectSrvProvider;
import cn.geelato.web.oss.ali.AliOSSConfiguration;
import cn.geelato.web.platform.boot.properties.OSSConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
                .setGenerateObjectNameFn((fileMeta) -> generateFilePrefix() + UIDGenerator.generate() + "." + fileMeta.getFileExtension());
        fileObjectSrvProvider = new AliFileObjectSrvProvider(aliOSSConfiguration);
    }

    public OSSResult putFile(MultipartFile file) throws IOException {
        return putFile(file.getOriginalFilename(), file.getInputStream());
    }

    public OSSResult putFile(String name, InputStream inputStream) {
        FileMeta fileMeta = new FileMeta(URLEncoder.encode(name, StandardCharsets.UTF_8), inputStream);
        return fileObjectSrvProvider.putFile(fileMeta);
    }

    public OSSResult getFile(String objectName) {
        return fileObjectSrvProvider.getFile(objectName);
    }

    private String generateFilePrefix() {
        return SessionCtx.getCurrentTenantCode() + "/";
    }
}

