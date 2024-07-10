package cn.geelato.orm.gql;

import cn.geelato.orm.gql.parser.JsonTextQueryParser;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 基于元数据的sql语句管理器
 * 元数据可来源于java类注解，也可来源于数据库配置的元数据信息
 *
 * @author geemeta
 */
public class GqlManager extends AbstractManager {
    private final org.slf4j.Logger logger = LoggerFactory.getLogger(GqlManager.class);
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
        logger.info("DataSourceManager Instancing...");
    }
    //========================================================
    //                  基于元数据  gql                      ==
    //========================================================
    public QueryCommand generateQuerySql(String jsonText, Ctx ctx) {
        return jsonTextQueryParser.parse(jsonText);
    }

    public List<QueryCommand> generateMultiQuerySql(String jsonText, Ctx ctx) {
        return jsonTextQueryParser.parseMulti(jsonText);
    }

    public QueryCommand generatePageQuerySql(String jsonText, Ctx ctx) {
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