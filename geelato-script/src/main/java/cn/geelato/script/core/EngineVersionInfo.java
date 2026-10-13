package cn.geelato.script.core;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EngineVersionInfo {

    private String id;
    /** CURRENT / DEPRECATED / RETIRED。 */
    private String status;
    private String migrateHint;
    /** 该版本可见的 kit 命名空间清单。 */
    private java.util.List<String> namespaces;
}
