package cn.geelato.web.platform.m.syspackage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "geelato.package")
public class PackageConfigurationProperties {
    private String env;
    private String path;
    private String uploadFolder;

    public String getUploadPath(){
        return this.path+"/"+uploadFolder+"/";
    }


}
