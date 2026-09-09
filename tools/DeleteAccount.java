import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * Lists or deletes accounts in the embedded game database.
 *
 * The server must be running (the embedded PostgreSQL only listens while the server is up).
 *
 * Usage (from the repo root):
 *   java -cp <postgresql-driver.jar> tools/DeleteAccount.java list
 *   java -cp <postgresql-driver.jar> tools/DeleteAccount.java delete <account name>
 */
public class DeleteAccount {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: list | delete <account name>");
            System.exit(2);
        }
        List<String> pid = Files.readAllLines(Path.of(".data", "postgres", "postmaster.pid"));
        int port = Integer.parseInt(pid.get(3).trim());
        String url = "jdbc:postgresql://localhost:" + port + "/postgres";
        try (Connection c = DriverManager.getConnection(url, "postgres", "")) {
            if (args[0].equals("list")) {
                String sql =
                    "SELECT a.id, a.account_name, a.rights, ch.id AS character_id, ch.display_name " +
                    "FROM accounts a LEFT JOIN account_characters ch ON ch.account_id = a.id " +
                    "ORDER BY a.id";
                try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                    System.out.println("account_id | account_name | rights | character_id | display_name");
                    while (rs.next()) {
                        System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | " + rs.getString(3)
                            + " | " + rs.getObject(4) + " | " + rs.getString(5));
                    }
                }
            } else if (args[0].equals("delete") && args.length == 2) {
                String sql = "DELETE FROM accounts WHERE lower(account_name) = lower(?)";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, args[1]);
                    int n = ps.executeUpdate();
                    System.out.println(n == 0 ? "No account named '" + args[1] + "'" : "Deleted account '" + args[1] + "' and its character");
                }
            } else {
                System.err.println("usage: list | delete <account name>");
                System.exit(2);
            }
        }
    }
}
