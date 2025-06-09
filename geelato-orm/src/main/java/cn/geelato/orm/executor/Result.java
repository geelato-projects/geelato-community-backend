package cn.geelato.orm.executor;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class Result<T> {
    protected List<T> data;
    protected long total;

    public Result() {
    }
    public Result(List<T> data, long total) {
        this.data = data;
        this.total = total;
    }
}
