package cn.geelato.web.platform.m.base.service;

import cn.geelato.web.platform.m.base.entity.Resources;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author diabl
 */
@Component
public class ResourcesService extends BaseService {

    public Resources saveByFile(File file, String name, String genre, String appId, String tenantCode) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        Resources attach = new Resources();
        attach.setTenantCode(tenantCode);
        attach.setAppId(appId);
        attach.setName(name);
        attach.setType(Files.probeContentType(file.toPath()));
        attach.setGenre(genre);
        attach.setSize(attributes.size());
        attach.setPath(file.getPath());
        return this.createModel(attach);
    }
}
