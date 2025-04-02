package cn.geelato.core.orm;

import cn.geelato.core.meta.EntityManager;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.script.db.DbScriptManager;
import cn.geelato.core.script.db.DbScriptManagerFactory;
import cn.geelato.core.script.sql.SqlScriptManager;
import cn.geelato.core.script.sql.SqlScriptManagerFactory;
import cn.geelato.core.sql.SqlManager;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

public class BaseDao {
    protected final SqlScriptManager sqlScriptManager = SqlScriptManagerFactory.get("sql");
    protected final DbScriptManager dbScriptManager= DbScriptManagerFactory.get("db");

    protected JdbcTemplate jdbcTemplate;

    protected final MetaManager metaManager = MetaManager.singleInstance();
    protected final SqlManager sqlManager = SqlManager.singleInstance();
    protected final EntityManager entityManager = EntityManager.singleInstance();

    protected static final Map<String, Object> defaultParams = new HashMap<>();



}
