package cn.geelato.core.gql.execute;

import lombok.Getter;
import lombok.Setter;

/**
 * @author geemeta
 *
 */
@Getter
@Setter
public class BoundPageSql{
    private BoundSql boundSql;
    private String countSql;
}
