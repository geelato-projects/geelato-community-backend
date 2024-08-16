package cn.geelato.core.meta.model.view;


import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
public class ViewMeta {
    private TableView viewMeta;
    private String viewName;
    private String viewType;
    private String viewConstruct;
    private String viewColumn;
    private String subjectEntity;

    public ViewMeta(String viewName, String viewType, String viewConstruct, String viewColumn, String entityName) {
        this.viewName = viewName;
        this.viewType = viewType;
        this.viewConstruct = viewConstruct;
        this.viewColumn = viewColumn;
        this.subjectEntity = entityName;
    }
}
