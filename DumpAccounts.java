import java.sql.*;
public class DumpAccounts {
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:./data/eneik_db;IFEXISTS=TRUE";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT id, name, github_username, enabled, status FROM accounts")) {
                while(rs.next()) {
                    System.out.println("ID: " + rs.getString("id") + ", Name: " + rs.getString("name") + ", GH: " + rs.getString("github_username") + ", Enabled: " + rs.getBoolean("enabled") + ", Status: " + rs.getString("status"));
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
