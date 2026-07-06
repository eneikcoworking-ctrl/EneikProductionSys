import java.sql.*;
public class DumpSettings {
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:./data/eneik_db;IFEXISTS=TRUE";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM system_settings")) {
                while(rs.next()) {
                    System.out.println(rs.getString("key") + ": " + rs.getString("value"));
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
