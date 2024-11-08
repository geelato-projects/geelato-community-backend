package cn.geelato.core.meta.model.field;

public class StringFieldValue extends FieldValue {
    private String stringValue;
    public StringFieldValue(FieldMeta fieldMeta, String stringValue){
        this.stringValue=stringValue;
        this.fieldMeta = fieldMeta;
    }
    public StringFieldValue( String stringValue){
        this.stringValue=stringValue;
    }
}
