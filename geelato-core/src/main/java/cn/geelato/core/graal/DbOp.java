package cn.geelato.core.graal;

import java.sql.*;
import java.util.Map;

public class DbOp {
    public void test(Map<String,Object> map) throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");

        //准备用户名、密码、数据库地址
        String name = "geelato_user";
        String password = "pass@Word1";
        String url = "jdbc:mysql://134.175.84.44:5050/geelato?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";

        //创建数据库连接
        Connection connection = DriverManager.getConnection(url, name, password);

        String m_value= map.get("login_name").toString();
        String sql = "select * from platform_user where login_name='"+m_value+"'";
        System.out.println("execute sql is :"+sql);
        PreparedStatement statement = connection.prepareStatement(sql);

        //执行sql，返回结果集
        ResultSet resultSet = statement.executeQuery();

        while (resultSet.next()) {
            System.out.println("sql result :");
            System.out.println(resultSet.getString("login_name")+"---"+resultSet.getString("email"));
        }

        //释放资源
        resultSet.close();
        statement.close();
        connection.close();

    }
}
