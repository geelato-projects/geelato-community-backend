package cn.geelato.core.orm;

import cn.geelato.core.meta.EntityManager;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.script.sql.SqlScriptManager;
import cn.geelato.core.script.sql.SqlScriptManagerFactory;
import cn.geelato.core.sql.SqlManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
public class BaseDao {
    public final static String SQL_TEMPLATE_MANAGER = "sql";
    protected JdbcTemplate jdbcTemplate;

    protected final MetaManager metaManager = MetaManager.singleInstance();
    protected final SqlScriptManager sqlScriptManager = SqlScriptManagerFactory.get(SQL_TEMPLATE_MANAGER);
    protected final SqlManager sqlManager = SqlManager.singleInstance();
    protected final EntityManager entityManager = EntityManager.singleInstance();

    protected static final Map<String, Object> defaultParams = new HashMap<>();

}
