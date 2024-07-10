package cn.geelato.orm.meta.model.view;


public class ViewMeta {
    private TableView viewMeta;
    private String viewName;
    private String viewType;
    private String viewConstruct;
    private String viewColumn;
    private String subjectEntity;

    public ViewMeta(String viewName, String viewType, String viewConstruct,String viewColumn,String entityName) {
        this.viewName = viewName;
        this.viewType = viewType;
        this.viewConstruct = viewConstruct;
        this.viewColumn=viewColumn;
        this.subjectEntity=entityName;
    }

    public TableView getViewMeta() {
        return viewMeta;
    }

    public void setViewMeta(TableView viewMeta) {
        this.viewMeta = viewMeta;
    }

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public String getViewType() {
        return viewType;
    }

    public void setViewType(String viewType) {
        this.viewType = viewType;
    }

    public String getViewConstruct() {
        return viewConstruct;
    }

    public void setViewConstruct(String viewConstruct) {
        this.viewConstruct = viewConstruct;
    }

    public String getViewColumn() {
        return viewColumn;
    }

    public void setViewColumn(String viewColumn) {
        this.viewColumn = viewColumn;
    }

    public String getSubjectEntity() {
        return subjectEntity;
    }

    public void setSubjectEntity(String subjectEntity) {
        this.subjectEntity = subjectEntity;
    }
}
