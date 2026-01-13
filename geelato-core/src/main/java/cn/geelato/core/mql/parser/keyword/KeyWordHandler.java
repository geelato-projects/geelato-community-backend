package cn.geelato.core.mql.parser.keyword;

import cn.geelato.core.mql.command.BaseCommand;
import cn.geelato.core.mql.command.CommandValidator;
import cn.geelato.core.mql.filter.FilterGroup;
import com.alibaba.fastjson2.JSONObject;

public interface KeyWordHandler {
    void handle(JSONObject jo, String key, String value, BaseCommand command, CommandValidator validator, FilterGroup fg, String entityName);
}


