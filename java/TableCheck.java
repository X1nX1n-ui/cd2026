import java.sql.*;
public class TableCheck {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection(
      "jdbc:mysql://127.0.0.1:3306/cd2026?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
      "root","root");
      Statement s = c.createStatement();
      ResultSet rs = s.executeQuery("SHOW TABLES")) {
      while (rs.next()) {
        System.out.println(rs.getString(1));
      }
    }
  }
}
