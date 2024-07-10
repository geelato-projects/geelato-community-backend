package cn.geelato.orm.querydsl;

import lombok.Data;
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
