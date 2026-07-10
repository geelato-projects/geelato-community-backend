package cn.geelato.app.scaffold.boot;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "geelato.app.scaffold")
public class AppScaffoldProperties {
    private boolean autoInitTables = true;

    private boolean openapiEnabled = true;

    private boolean openapiExposeInProd = false;

    private List<String> extraControllers = new ArrayList<>();

}
