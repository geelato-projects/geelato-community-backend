package cn.geelato.core.gql.parser;

import cn.geelato.core.Ctx;
import cn.geelato.core.gql.command.CommandValidator;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class JsonTextParser {
    protected final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DateUtils.DATETIME);
    protected static final MetaManager metaManager = MetaManager.singleInstance();

    protected void putBaseDefaultField(Ctx ctx, Map<String, Object> params, CommandValidator validator) {
        String newDataString = simpleDateFormat.format(new Date());
        if (validator.hasKeyField("updateAt")) {
            params.put("updateAt", newDataString);
        }
        if (validator.hasKeyField("updater")) {
            params.put("updater", ctx.get("userId"));
        }
        if (validator.hasKeyField("updaterName")) {
            params.put("updaterName", ctx.get("userName"));
        }
    }
}
