package cn.geelato.core.gql.parser;

import cn.geelato.core.gql.command.BaseCommand;
import cn.geelato.core.gql.command.CommandValidator;
import cn.geelato.core.gql.filter.FilterGroup;
import com.alibaba.fastjson2.JSONObject;

public interface KeyWordHandler {
    void handle(JSONObject jo, String key, String value, BaseCommand command, CommandValidator validator, FilterGroup fg, String entityName);
}


