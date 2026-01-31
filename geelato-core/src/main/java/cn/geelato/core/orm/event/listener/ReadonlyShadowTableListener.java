package cn.geelato.core.orm.event.listener;

import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.core.orm.event.AfterSaveEventListener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadonlyShadowTableListener implements AfterSaveEventListener {
    private static final Pattern P_INSERT = Pattern.compile("(?is)^\\s*(insert\\s+into\\s+)(`?)([a-zA-Z0-9_]+)(`?)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_UPDATE = Pattern.compile("(?is)^\\s*(update\\s+)(`?)([a-zA-Z0-9_]+)(`?)\\s+set\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public void beforeSave(SaveEventContext context) {}

    @Override
    public void afterSave(SaveEventContext context) {
        String sql = context.getBoundSql().getSql();
        String mirrorSql = transform(sql);
        if (mirrorSql == null) {
            return;
        }
        BoundSql readonlySql = new BoundSql();
        readonlySql.setSql(mirrorSql);
        readonlySql.setParams(context.getBoundSql().getParams());
        context.getDao().executeUpdate(readonlySql);
    }

    @Override
    public boolean supports(SaveEventContext context) {
        String sql = context.getBoundSql().getSql();
        return sql != null && (sql.trim().toLowerCase().startsWith("insert") || sql.trim().toLowerCase().startsWith("update"));
    }

    private String transform(String sql) {
        Matcher mi = P_INSERT.matcher(sql);
        if (mi.find()) {
            return mi.replaceFirst(mi.group(1) + mi.group(2) + mi.group(3) + "_readonly" + mi.group(4) + "(");
        }
        Matcher mu = P_UPDATE.matcher(sql);
        if (mu.find()) {
            return mu.replaceFirst(mu.group(1) + mu.group(2) + mu.group(3) + "_readonly" + mu.group(4) + " set");
        }
        return null;
    }
}
