package cn.geelato.security;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Permission {

    private String entity;
    private String name;
    private String rule;
    private Integer weight;
    private Integer roleWeight;

}
