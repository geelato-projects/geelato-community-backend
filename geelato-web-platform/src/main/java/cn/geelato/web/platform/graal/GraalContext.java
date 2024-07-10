package cn.geelato.web.platform.graal;

public class GraalContext {

    public GraalContext(Object result){
//        this.parameter=parameter;
        this.result=result;
    }
//    private Object parameter;

    private Object result;

//    public Object getParameter() {
//        return parameter;
//    }

//    public void setParameter(Object parameter) {
//        this.parameter = parameter;
//    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
