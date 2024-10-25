package cn.geelato.core.meta.model.field;

public class StringFieldValueMeta extends FieldValueMeta {
    private String stringValue;
    public StringFieldValueMeta(FieldMeta fieldMeta, String stringValue){
        this.stringValue=stringValue;
        this.fieldMeta = fieldMeta;
    }

}
