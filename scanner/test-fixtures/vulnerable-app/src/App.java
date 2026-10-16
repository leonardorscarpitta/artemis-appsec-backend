import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Random;

public class App {
    void find(Connection connection, String name) throws Exception {
        Statement statement = connection.createStatement();
        statement.executeQuery("SELECT * FROM users WHERE name = '" + name + "'");
        MessageDigest.getInstance("MD5");
        int code = new Random().nextInt();
        Runtime.getRuntime().exec("ping " + name);
    }
}
