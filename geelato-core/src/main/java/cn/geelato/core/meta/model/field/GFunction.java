package cn.geelato.core.meta.model.field;

public enum GFunction implements FunctionResolver{
    increment{
        @Override
        public FunctionFieldValue resolve(String functionExpression) {
            return null;
        }
    },
    findinset{
        @Override
        public FunctionFieldValue resolve(String functionExpression) {
            return null;
        }
    };


}
