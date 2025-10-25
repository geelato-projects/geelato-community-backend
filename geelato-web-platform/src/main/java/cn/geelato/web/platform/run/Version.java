package cn.geelato.web.platform.run;

import lombok.Getter;

@Getter
public class Version {
    public static Version current = new Version();

    private final String edition = "community";

    private final String version = "1.0.0-SNAPSHOT";

}