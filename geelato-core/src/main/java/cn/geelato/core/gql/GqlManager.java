package cn.geelato.core.gql;

import cn.geelato.core.AbstractManager;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.Ctx;
import cn.geelato.core.gql.parser.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 基于元数据的sql语句管理器
 * 元数据可来源于java类注解，也可来源于数据库配置的元数据信息
 *
 * @author geemeta
 */
@Slf4j
public class GqlManager extends AbstractManager {
    private static GqlManager instance;
    private final JsonTextQueryParser jsonTextQueryParser = new JsonTextQueryParser();
    private final JsonTextSaveParser jsonTextSaveParser = new JsonTextSaveParser();
    private final JsonTextDeleteParser jsonTextDeleteParser = new JsonTextDeleteParser();

    public static GqlManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new GqlManager();
        }
        lock.unlock();
        return instance;
    }

    private  GqlManager() {
        log.info("GqlManager Instancing...");
    }
    //========================================================
    //                  基于元数据  gql                      ==
    //========================================================
    public QueryCommand generateQuerySql(String jsonText) {
        return jsonTextQueryParser.parse(jsonText);
    }

    public List<QueryCommand> generateMultiQuerySql(String jsonText) {
        return jsonTextQueryParser.parseMulti(jsonText);
    }

    public QueryCommand generatePageQuerySql(String jsonText) {
        return jsonTextQueryParser.parse(jsonText);
    }

    public SaveCommand generateSaveSql(String jsonText, Ctx ctx) {
        return jsonTextSaveParser.parse(jsonText, ctx);
    }

    public List<SaveCommand> generateBatchSaveSql(String jsonText, Ctx ctx) {
        return jsonTextSaveParser.parseBatch(jsonText, ctx);
    }
    public List<SaveCommand> generateMultiSaveSql(String jsonText, Ctx ctx) {
        return jsonTextSaveParser.parseMulti(jsonText, ctx);
    }
    public DeleteCommand generateDeleteSql(String jsonText, Ctx ctx) {
        return jsonTextDeleteParser.parse(jsonText, ctx);
    }

}