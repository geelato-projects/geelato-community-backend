package cn.geelato.orm.query2;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class QueryOperatorParameter {
    private List<String> select = new ArrayList<>();
    private String from;
    private List<String> where = new ArrayList<>();
}
