import java.sql.*;
public class CheckLegacy {
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:./data/eneik_db;IFEXISTS=TRUE";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "SETTINGS", null)) {
                if (rs.next()) {
                    System.out.println("Legacy SETTINGS table EXISTS");
                    try (ResultSet rs2 = conn.createStatement().executeQuery("SELECT * FROM settings")) {
                        while(rs2.next()) System.out.println(rs2.getString(1) + ": " + rs2.getString(2));
                    }
                } else {
                    System.out.println("Legacy SETTINGS table NOT FOUND");
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
