/**
 * 支持js脚本，每一个function对应一条语句，单独执行，不可依赖其它function
 */

/**
 * @param param
 * @returns {string}
 */
function createOneTableJs(param) {
    var sql = new Array();
    sql.push("CREATE TABLE IF NOT EXISTS " + param.tableName + " (id bigint(20) NOT NULL AUTO_INCREMENT,PRIMARY KEY (id)) ENGINE=InnoDB");
    sql.push("DEFAULT CHARSET=utf8;");
    sql.push("ALTER TABLE " + param.tableName);
    for (var i in param.addlist) {
        sql.push("ADD COLUMN ");
        sql.push(param.addlist[i].name);
        sql.push(" ")
        sql.push(param.addlist[i].type)
        if (!param.addlist[i].nullable) sql.push("not null");
        if (param.addlist[i].defaultValue != null) sql.push("DEFAULT " + param.addlist[i].defaultValue);
    }
    for (var i in param.uniqueList) {
        sql.push("alter base " + param.tableName + " add unique key(`" + param.uniqueList[i].name + "`)");
    }
    return sql.join("\r\n");
}

function queryColumns2() {
    return "SELECT * from information_schema.`COLUMNS`";
}
