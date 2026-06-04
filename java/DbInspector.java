import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbInspector {

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3306/cd2026?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                "root",
                "root");
             Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(
                    "select id,user_name,user_pwd,user_status,failed_attempts,lock_time,deleted from `user` order by id")) {
                while (resultSet.next()) {
                    System.out.println(
                            resultSet.getLong("id") + "|"
                                    + resultSet.getString("user_name") + "|"
                                    + resultSet.getString("user_pwd") + "|"
                                    + resultSet.getString("user_status") + "|"
                                    + resultSet.getInt("failed_attempts") + "|"
                                    + resultSet.getString("lock_time") + "|"
                                    + resultSet.getInt("deleted"));
                }
            }

            try (ResultSet resultSet = statement.executeQuery(
                    "select u.user_name, ur.role_id from user_role ur join `user` u on u.id = ur.user_id order by ur.user_id, ur.role_id")) {
                while (resultSet.next()) {
                    System.out.println("ROLE|" + resultSet.getString("user_name") + "|" + resultSet.getLong("role_id"));
                }
            }
        }
    }
}
