import java.sql.*;
public class DumpApiKeys {
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:./data/eneik_db;IFEXISTS=TRUE";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT name, api_key FROM accounts")) {
                while(rs.next()) {
                    System.out.println("Name: " + rs.getString("name") + ", Key: " + rs.getString("api_key"));
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
