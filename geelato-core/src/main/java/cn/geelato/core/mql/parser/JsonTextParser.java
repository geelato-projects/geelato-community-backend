package cn.geelato.core.mql.parser;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.CommandValidator;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Slf4j
public class JsonTextParser {

    protected final static String KEYWORD_FLAG = "@";
    protected final static String FILTER_FLAG = "\\|";
    protected final String FN_UPDATE_AT = "updateAt";
    protected final String FN_UPDATER = "updater";
    protected final String FN_UPDATER_NAME = "updaterName";

    protected final String FN_CREATE_AT = "createAt";
    protected final String FN_CREATOR = "creator";
    protected final String FN_CREATOR_NAME = "creatorName";
    protected final String FN_TENANT_CODE = "tenantCode";
    protected final String FN_BU_ID = "buId";
    protected final String FN_DEPT_ID = "deptId";

    protected final String FN_DEL_STATUS = "delStatus";
    protected final String FN_DELETE_AT = "deleteAt";
    protected final String FN_ENABLE_STATUS = "enableStatus";

    protected final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DateUtils.DATETIME);
    protected static final MetaManager metaManager = MetaManager.singleInstance();

    protected void putBaseDefaultField(SessionCtx sessionCtx, Map<String, Object> params, CommandValidator validator) {
        String newDataString = simpleDateFormat.format(new Date());
        if (validator.hasKeyField(FN_UPDATE_AT)) {
            params.put(FN_UPDATE_AT, newDataString);
        }
        if (validator.hasKeyField(FN_UPDATER)) {
            params.put(FN_UPDATER, SessionCtx.getUserId());
        }
        if (validator.hasKeyField(FN_UPDATER_NAME)) {
            params.put(FN_UPDATER_NAME,  SessionCtx.getUserName());
        }
    }

    protected void logAndThrow(String message, String format, Object... args) {
        IllegalArgumentException ex = new IllegalArgumentException(message);
        Object[] newArgs = new Object[(args == null ? 0 : args.length) + 1];
        if (args != null && args.length > 0) {
            System.arraycopy(args, 0, newArgs, 0, args.length);
        }
        newArgs[newArgs.length - 1] = ex;
        log.error(format, newArgs);
        throw ex;
    }
}
