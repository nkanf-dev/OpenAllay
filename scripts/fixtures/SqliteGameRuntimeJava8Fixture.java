import java.nio.file.*;
import java.sql.*;
public final class SqliteGameRuntimeJava8Fixture {
    public static void main(String[] args) throws Exception {
        if (!"1.8".equals(System.getProperty("java.specification.version"))) throw new AssertionError("Require actual Java8");
        if (args.length != 1) throw new IllegalArgumentException("Fresh isolated database path required");
        Path database = Paths.get(args[0]);
        if (Files.exists(database)) throw new IllegalArgumentException("Refuse existing database path");
        Files.createDirectories(database.toAbsolutePath().getParent());
        Class.forName("org.sqlite.JDBC");
        String sqliteVersion;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("create table proof(id integer primary key, value text not null)");
                statement.executeUpdate("insert into proof(value) values('native-current')");
                try (ResultSet result = statement.executeQuery("select value, sqlite_version() from proof where id=1")) {
                    if (!result.next() || !"native-current".equals(result.getString(1))) throw new AssertionError("JDBC read/write mismatch");
                    sqliteVersion = result.getString(2);
                    if (!"3.50.3".equals(sqliteVersion)) throw new AssertionError("Native SQLite version differs: " + sqliteVersion);
                }
            }
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("select count(*) from proof")) {
            if (!result.next() || result.getInt(1) != 1) throw new AssertionError("Reopen persistence mismatch");
        }
        System.out.println("PASS genuine Java8 projected product SQLite JDBC/JNI version=" + sqliteVersion
            + " runtime=" + System.getProperty("java.version") + " db=" + database.toAbsolutePath());
    }
}
