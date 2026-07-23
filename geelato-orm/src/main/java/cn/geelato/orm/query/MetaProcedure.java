package cn.geelato.orm.query;

import cn.geelato.orm.function.WrapperResultFunction;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MetaProcedure extends MetaOperate<MetaProcedure> {
    @Getter
    private final String procedureName;
    @Getter
    private final List<ProcedureParam> inParams = new ArrayList<>();
    private WrapperResultFunction<?, ?> wrapperFunction;

    public MetaProcedure(String procedureName) {
        this.procedureName = procedureName;
    }

    public MetaProcedure in(String name, Object value) {
        this.inParams.add(new ProcedureParam(name, value));
        return this;
    }

    public <T, R> MetaProcedure wrapperResult(WrapperResultFunction<T, R> wrapperFunction) {
        this.wrapperFunction = wrapperFunction;
        return this;
    }

    public String toSql() {
        String placeholders = inParams.isEmpty() ? "" : inParams.stream().map(param -> "?").collect(Collectors.joining(", "));
        return "call " + procedureName + "(" + placeholders + ")";
    }

    public Object[] resolveParams() {
        return inParams.stream().map(ProcedureParam::getValue).toArray();
    }

    @SuppressWarnings("unchecked")
    public <R> List<R> list() {
        List<Map<String, Object>> rows = executor().callForMapList(toSql(), resolveParams(), getConnectId());
        if (wrapperFunction == null) {
            return (List<R>) rows;
        }
        return rows.stream()
                .map(row -> (R) ((WrapperResultFunction<Map<String, Object>, ?>) wrapperFunction).apply(row))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <R> R one() {
        Map<String, Object> row = executor().callForMap(toSql(), resolveParams(), getConnectId());
        if (wrapperFunction == null) {
            return (R) row;
        }
        return (R) ((WrapperResultFunction<Map<String, Object>, ?>) wrapperFunction).apply(row);
    }
}
