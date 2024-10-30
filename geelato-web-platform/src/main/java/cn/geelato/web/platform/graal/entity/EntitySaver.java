package cn.geelato.web.platform.graal.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EntitySaver {
    private String entity;
    private List<EntityField> fields;
}
