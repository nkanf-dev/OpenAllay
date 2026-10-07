package dev.openallay.model.config;

import dev.openallay.tool.ToolResult;
import dev.openallay.util.Java8Collections;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Dedicated local credential database; no secret is exposed through observable settings state. */
public final class LocalCredentialStore implements CredentialResolver, AutoCloseable {
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path database;
    private final Clock clock;
    private boolean closed;

    public LocalCredentialStore(Path database, Clock clock) {
        this.database = Objects.requireNonNull(database, "database").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized ToolResult<CredentialReference> insert(SecretValue secret) {
        Objects.requireNonNull(secret, "secret");
        if (closed) {
            return unavailable();
        }
        CredentialReference reference = CredentialReference.local(UUID.randomUUID());
        byte[] encoded = secret.reveal().getBytes(StandardCharsets.UTF_8);
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into credentials(\n"
                                + "    credential_id, secret_value, created_at, updated_at)\n"
                                + "values (?, ?, ?, ?)\n")) {
            String now = Instant.now(clock).toString();
            statement.setString(1, reference.value());
            statement.setBytes(2, encoded);
            statement.setString(3, now);
            statement.setString(4, now);
            statement.executeUpdate();
            return new ToolResult.Success<>(reference);
        } catch (SQLException | RuntimeException failure) {
            return unavailable();
        } finally {
            java.util.Arrays.fill(encoded, (byte) 0);
        }
    }

    @Override
    public synchronized ToolResult<SecretValue> resolve(CredentialReference reference) {
        Objects.requireNonNull(reference, "reference");
        if (closed || reference.kind() != CredentialReference.Kind.LOCAL) {
            return unavailable();
        }
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(
                        "select secret_value from credentials where credential_id = ?")) {
            statement.setString(1, reference.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new ToolResult.Failure<>(
                            "credential_not_found", "The stored credential is unavailable");
                }
                byte[] encoded = result.getBytes(1);
                try {
                    return new ToolResult.Success<>(
                            SecretValue.of(new String(encoded, StandardCharsets.UTF_8)));
                } finally {
                    java.util.Arrays.fill(encoded, (byte) 0);
                }
            }
        } catch (SQLException | RuntimeException failure) {
            return unavailable();
        }
    }

    /** Checks a local reference without materializing its secret value. */
    public synchronized ToolResult<Boolean> contains(CredentialReference reference) {
        Objects.requireNonNull(reference, "reference");
        if (closed || reference.kind() != CredentialReference.Kind.LOCAL) {
            return unavailable();
        }
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(
                        "select 1 from credentials where credential_id = ?")) {
            statement.setString(1, reference.value());
            try (ResultSet result = statement.executeQuery()) {
                return new ToolResult.Success<>(result.next());
            }
        } catch (SQLException | RuntimeException failure) {
            return unavailable();
        }
    }

    public synchronized ToolResult<Boolean> deleteIfUnreferenced(
            CredentialReference reference,
            Set<CredentialReference> retained) {
        Objects.requireNonNull(reference, "reference");
        Set<CredentialReference> retainedCopy = Java8Collections.setCopyOf(retained);
        if (closed) {
            return unavailable();
        }
        if (reference.kind() != CredentialReference.Kind.LOCAL || retainedCopy.contains(reference)) {
            return new ToolResult.Success<>(Boolean.FALSE);
        }
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(
                        "delete from credentials where credential_id = ?")) {
            statement.setString(1, reference.value());
            return new ToolResult.Success<>(statement.executeUpdate() > 0);
        } catch (SQLException | RuntimeException failure) {
            return unavailable();
        }
    }

    public synchronized ToolResult<Integer> collectUnreferenced(
            Set<CredentialReference> retained) {
        Set<String> retainedIds = new HashSet<>();
        for (CredentialReference reference : Java8Collections.setCopyOf(retained)) {
            if (reference.kind() == CredentialReference.Kind.LOCAL) {
                retainedIds.add(reference.value());
            }
        }
        if (closed) {
            return unavailable();
        }
        try (Connection connection = open()) {
            int deleted = 0;
            try (Statement query = connection.createStatement();
                    ResultSet result = query.executeQuery(
                            "select credential_id from credentials order by credential_id")) {
                java.util.List<String> candidates = new java.util.ArrayList<>();
                while (result.next()) {
                    String id = result.getString(1);
                    if (!retainedIds.contains(id)) {
                        candidates.add(id);
                    }
                }
                try (PreparedStatement remove = connection.prepareStatement(
                        "delete from credentials where credential_id = ?")) {
                    for (String id : candidates) {
                        remove.setString(1, id);
                        deleted += remove.executeUpdate();
                    }
                }
            }
            return new ToolResult.Success<>(deleted);
        } catch (SQLException | RuntimeException failure) {
            return unavailable();
        }
    }

    private Connection open() throws SQLException {
        try {
            Path parent = database.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException failure) {
            throw new SQLException("credential directory unavailable", failure);
        }
        boolean create = !Files.exists(database);
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        boolean success = false;
        try {
            ensureSchema(connection, create);
            configure(connection);
            hardenPermissions();
            success = true;
            return connection;
        } finally {
            if (!success) {
                connection.close();
            }
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("pragma journal_mode=wal");
            statement.execute("pragma synchronous=full");
        }
    }

    private static void ensureSchema(Connection connection, boolean create) throws SQLException {
        Set<String> tables = applicationTables(connection);
        if (create && tables.isEmpty()) {
            createSchema(connection);
            return;
        }
        if (!tables.equals(Java8Collections.setOf("credentials"))) {
            throw new SQLException("unrecognized credential database");
        }
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("pragma table_xinfo(credentials)")) {
            while (result.next()) {
                if (result.getString("dflt_value") != null || result.getInt("hidden") != 0) {
                    throw new SQLException("unrecognized credential database");
                }
                columns.add(result.getString("name") + ":" + result.getString("type")
                        + ":" + result.getInt("notnull") + ":" + result.getInt("pk"));
            }
        }
        if (!columns.equals(Java8Collections.setOf(
                "credential_id:TEXT:0:1", "secret_value:BLOB:1:0",
                "created_at:TEXT:1:0", "updated_at:TEXT:1:0"))) {
            throw new SQLException("unrecognized credential database");
        }
    }

    private static void createSchema(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("create table credentials(\n"
                    + "    credential_id text primary key,\n"
                    + "    secret_value blob not null,\n"
                    + "    created_at text not null,\n"
                    + "    updated_at text not null\n"
                    + ")\n");
            connection.commit();
        } catch (SQLException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static Set<String> applicationTables(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("select name from sqlite_master\n"
                        + "where type = 'table' and name not glob 'sqlite_*'\n")) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }
        return Java8Collections.setCopyOf(tables);
    }

    private void hardenPermissions() {
        try {
            if (Files.getFileStore(database).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(database, OWNER_ONLY);
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // The store remains usable but callers must not claim OS-vault protection.
        }
    }

    private static <T> ToolResult.Failure<T> unavailable() {
        return new ToolResult.Failure<>(
                "credential_store_unavailable", "Stored credentials are unavailable");
    }

    @Override
    public synchronized void close() {
        closed = true;
    }
}
