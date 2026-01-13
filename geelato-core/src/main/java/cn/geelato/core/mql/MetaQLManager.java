package cn.geelato.core.mql;

import cn.geelato.core.AbstractManager;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.parser.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 基于元数据的sql语句管理器
 * 元数据可来源于java类注解，也可来源于数据库配置的元数据信息
 *
 * @author geemeta
 */
@Slf4j
public class MetaQLManager extends AbstractManager {
    private static MetaQLManager instance;
    private final JsonTextQueryParser jsonTextQueryParser = new JsonTextQueryParser();
    private final JsonTextSaveParser jsonTextSaveParser = new JsonTextSaveParser();
    private final JsonTextDeleteParser jsonTextDeleteParser = new JsonTextDeleteParser();

    public static MetaQLManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new MetaQLManager();
        }
        lock.unlock();
        return instance;
    }

    private MetaQLManager() {
        log.info("MetaQLManager Instancing...");
    }
    public QueryCommand generateQuerySql(String jsonText) {
        return jsonTextQueryParser.parse(jsonText);
    }
    public List<QueryCommand> generateMultiQuerySql(String jsonText) {
        return jsonTextQueryParser.parseMulti(jsonText);
    }
    public SaveCommand generateSaveSql(String jsonText, SessionCtx sessionCtx) {
        return jsonTextSaveParser.parse(jsonText, sessionCtx);
    }

    public List<SaveCommand> generateBatchSaveSql(String jsonText, SessionCtx sessionCtx) {
        return jsonTextSaveParser.parseBatch(jsonText, sessionCtx);
    }
    public List<SaveCommand> generateMultiSaveSql(String jsonText, SessionCtx sessionCtx) {
        return jsonTextSaveParser.parseMulti(jsonText, sessionCtx);
    }
    public DeleteCommand generateDeleteSql(String jsonText, SessionCtx sessionCtx) {
        return jsonTextDeleteParser.parse(jsonText, sessionCtx);
    }

}