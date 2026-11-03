package org.ssdd.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** Creates only a new test database; never migrates or drops an existing application schema. */
public final class MySqlTestDatabase implements AutoCloseable {
    private final String database;
    private final String adminUrl;
    private final String username;
    private final String password;
    private final String url;
    private final MySQLContainer<?> container;

    public static boolean available() {
        return "true".equalsIgnoreCase(System.getenv("SSDD_TEST_MYSQL_ENABLED"))
                || DockerClientFactory.instance().isDockerAvailable();
    }

    public static MySqlTestDatabase open() throws SQLException {
        return new MySqlTestDatabase();
    }

    private MySqlTestDatabase() throws SQLException {
        database = "ssdd_p1_test_" + UUID.randomUUID().toString().replace("-", "");
        if ("true".equalsIgnoreCase(System.getenv("SSDD_TEST_MYSQL_ENABLED"))) {
            container = null;
            adminUrl = env("SSDD_TEST_MYSQL_ADMIN_URL",
                    "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
            username = env("SSDD_TEST_MYSQL_USERNAME", "");
            password = env("SSDD_TEST_MYSQL_PASSWORD", "");
            if (username.isBlank()) throw new IllegalArgumentException("Configure SSDD_TEST_MYSQL_USERNAME");
        } else {
            container = new MySQLContainer<>(env("SSDD_TEST_MYSQL_IMAGE", "mysql:8.4.8"));
            container.start();
            username = "root";
            password = container.getPassword();
            adminUrl = "jdbc:mysql://" + container.getHost() + ":" + container.getMappedPort(3306)
                    + "/?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        }
        String[] parts = adminUrl.split("\\?", 2);
        if (!parts[0].matches("jdbc:mysql://[^/]+/")) {
            if (container != null) container.stop();
            throw new IllegalArgumentException("Test admin URL must not contain an existing database name");
        }
        url = parts[0] + database + (parts.length == 2 ? "?" + parts[1] : "");
        try (var connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + safeName() + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        } catch (SQLException ex) {
            if (container != null) container.stop();
            throw ex;
        }
    }

    public String url() { return url; }
    public String username() { return username; }
    public String password() { return password; }
    public String database() { return database; }
    private Connection adminConnection() throws SQLException { return DriverManager.getConnection(adminUrl, username, password); }
    private String safeName() {
        if (!database.matches("ssdd_p1_test_[0-9a-f]{32}")) throw new IllegalStateException("Refuse unsafe test schema operation");
        return database;
    }

    @Override
    public void close() throws SQLException {
        try (var connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE `" + safeName() + "`");
        } finally {
            if (container != null) container.stop();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
