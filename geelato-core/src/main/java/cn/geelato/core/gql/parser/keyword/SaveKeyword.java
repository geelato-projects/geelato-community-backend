package cn.geelato.core.gql.parser.keyword;

import cn.geelato.core.gql.command.BaseCommand;
import cn.geelato.core.gql.command.CommandValidator;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.KeyWordHandler;
import com.alibaba.fastjson2.JSONObject;

public enum SaveKeyword implements KeyWordHandler {
    BIZ("@biz") {
        @Override
        public void handle(JSONObject jo, String key, String value, BaseCommand command, CommandValidator validator, FilterGroup fg, String entityName) {
            validator.appendMessage("[");
            validator.appendMessage(key);
            validator.appendMessage("]");
            validator.appendMessage("不支持;");
        }
    };

    private final String key;

    SaveKeyword(String key) {
        this.key = key;
    }

    public static SaveKeyword fromKey(String key) {
        for (SaveKeyword k : values()) {
            if (k.key.equals(key)) {
                return k;
            }
        }
        return null;
    }
}
