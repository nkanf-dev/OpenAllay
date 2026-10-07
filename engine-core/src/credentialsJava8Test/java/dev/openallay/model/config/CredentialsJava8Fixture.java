package dev.openallay.model.config;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.openallay.json.EngineJson;
import dev.openallay.tool.ToolResult;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Synthetic fixtures only. Never reads settings, user credentials, or game data. */
public final class CredentialsJava8Fixture {
    private static final String SECRET = "fixture-only-key/uri?header=synthetic";
    private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private static int checks;
    private static int posixChecked;
    private static int posixSkipped;
    private static int symlinkChecked;
    private static int symlinkSkipped;

    private CredentialsJava8Fixture() {}

    public static void main(String[] arguments) throws Exception {
        String expected = System.getProperty("credentials.expectedJava", "1.8");
        check(expected.equals(System.getProperty("java.specification.version")), "runtime version");
        Path owned = Files.createTempDirectory("openallay-credentials-synthetic-");
        try {
            referenceAndSecret();
            json();
            resolver();
            store(owned);
            invalidStores(owned);
            symlink(owned);
        } finally {
            // Only this fixture's fresh directory. All JDBC connections are closed first.
            try (Stream<Path> paths = Files.walk(owned)) {
                Path[] reverse = paths.sorted(Collections.reverseOrder()).toArray(Path[]::new);
                for (Path path : reverse) Files.deleteIfExists(path);
            }
        }
        System.out.println("PASS actual CredentialReference/SecretValue/CredentialResolver/LocalCredentialStore "
                + checks + " checks; java=" + System.getProperty("java.version")
                + "; POSIX checked=" + posixChecked + ", skipped=" + posixSkipped
                + "; symlink checked=" + symlinkChecked + ", skipped=" + symlinkSkipped);
    }

    private static void referenceAndSecret() {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        CredentialReference local = new CredentialReference(CredentialReference.Kind.LOCAL,
                id.toString().toUpperCase(java.util.Locale.ROOT));
        check(local.equals(CredentialReference.local(id)), "UUID canonicalization");
        check(local.value().equals(id.toString()), "local value");
        check(local.hashCode() == 31 * local.kind().hashCode() + local.value().hashCode(), "record hash zero seed");
        check(local.encoded().equals("local:" + id), "encoded local");
        check(local.toString().equals(local.encoded()), "qualified reference diagnostic");
        check(CredentialReference.parse("LOCAL:" + id).equals(local), "kind case parsing");
        CredentialReference env = CredentialReference.environment("_FIXTURE_1");
        check(env.equals(CredentialReference.parse("ENV:_FIXTURE_1")), "environment parse");
        check(env.kind() == CredentialReference.Kind.ENVIRONMENT, "environment kind");
        check(env.toString().equals("env:_FIXTURE_1"), "environment diagnostic");
        check(!env.equals(local) && !env.equals("env:_FIXTURE_1") && !env.equals(null), "reference equality domain");
        reject(() -> new CredentialReference(null, "fixture"), NullPointerException.class);
        for (String text : new String[] {null, "", " ", "\u2003"}) {
            reject(() -> CredentialReference.environment(text), IllegalArgumentException.class);
            reject(() -> SecretValue.of(text), IllegalArgumentException.class);
        }
        for (String text : new String[] {"bad:name", "A-B", "1BAD", "x\n", SECRET})
            reject(() -> CredentialReference.environment(text), IllegalArgumentException.class);
        for (String text : new String[] {null, "", ":bad", "local:", "other:test", "local:invalid"})
            reject(() -> CredentialReference.parse(text), IllegalArgumentException.class);
        SecretValue secret = SecretValue.of(SECRET);
        check(secret.reveal().equals(SECRET), "secret reveal exact");
        check(secret.toString().equals("[REDACTED]"), "secret toString");
        check(secret.equals(SecretValue.of(SECRET)) && !secret.equals(SecretValue.of("different")), "secret equality");
        check(!secret.equals(SECRET) && !secret.equals(null), "secret equality domain");
        check(secret.hashCode() == java.util.Objects.hash("[REDACTED]"), "secret fixed redacted hash");
        check(secret.hashCode() == SecretValue.of("different").hashCode(), "hash does not derive secret");
        check(SecretValue.of(" \tfixture \r\n").reveal().equals(" \tfixture \r\n"), "secret not trimmed");
        check(SecretValue.of("\u00a0").reveal().equals("\u00a0"), "NBSP is not blank");
        safe(new ToolResult.Success<>(secret).toString());
    }

    private static void json() {
        Gson gson = EngineJson.create();
        CredentialReference env = CredentialReference.environment("FIXTURE_KEY");
        String goldenEnv = "{\"kind\":\"ENVIRONMENT\",\"value\":\"FIXTURE_KEY\"}";
        check(goldenEnv.equals(gson.toJson(env)), "reference JSON bytes");
        check(env.equals(gson.fromJson(goldenEnv, CredentialReference.class)), "reference JSON roundtrip");
        CredentialReference local = CredentialReference.local(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        String goldenLocal = "{\"kind\":\"LOCAL\",\"value\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"}";
        check(goldenLocal.equals(gson.toJson(local)), "local JSON bytes");
        check(local.equals(gson.fromJson(goldenLocal, CredentialReference.class)), "local JSON roundtrip");
        String uppercaseLocal = goldenLocal.replace("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE");
        check(local.equals(gson.fromJson(uppercaseLocal, CredentialReference.class)), "JSON canonical constructor");
        check(env.equals(gson.fromJson(goldenEnv.replace("}", ",\"future\":true}"), CredentialReference.class)),
                "existing unknown-field behavior");
        check(gson.fromJson("null", CredentialReference.class) == null, "nullable reference");
        for (String text : new String[] {"{}", "{\"kind\":\"ENVIRONMENT\"}",
                "{\"kind\":null,\"value\":\"FIXTURE_KEY\"}",
                "{\"kind\":\"ENVIRONMENT\",\"value\":\"bad-name\"}",
                "{\"kind\":\"LOCAL\",\"value\":\"not-a-uuid\"}",
                "{\"kind\":\"ENVIRONMENT\",\"kind\":\"LOCAL\",\"value\":\"FIXTURE_KEY\"}",
                "[1]", "{\"kind\":"}) {
            reject(() -> gson.fromJson(text, CredentialReference.class), JsonParseException.class);
        }
        // This is the existing explicit synthetic serialization shape, never a diagnostic.
        String expectedSecret = "{\"value\":\"fixture-only-key/uri?header\\u003dsynthetic\"}";
        check(expectedSecret.equals(gson.toJson(SecretValue.of(SECRET))), "existing secret serializer bytes");
        ToolResult.Failure<SecretValue> failure = new ToolResult.Failure<>(
                "model_not_configured", "The configured credential is unavailable");
        String expectedFailure = "{\"code\":\"model_not_configured\",\"message\":\"The configured credential is unavailable\"}";
        check(expectedFailure.equals(gson.toJson(failure)), "failure JSON bytes");
        safe(failure.toString());
    }

    private static void resolver() {
        Map<String, String> values = new HashMap<>();
        values.put("FIXTURE_KEY", SECRET);
        values.put("BLANK", "\u2003\t");
        values.put("NBSP", "\u00a0");
        CredentialResolver resolver = CredentialResolver.environment(values);
        values.put("FIXTURE_KEY", "changed");
        check(SECRET.equals(success(resolver.resolve(CredentialReference.environment("FIXTURE_KEY"))).reveal()), "environment snapshot");
        fail(resolver.resolve(CredentialReference.environment("MISSING")), "model_not_configured",
                "The configured credential is unavailable");
        fail(resolver.resolve(CredentialReference.environment("BLANK")), "model_not_configured",
                "The configured credential is unavailable");
        check(success(resolver.resolve(CredentialReference.environment("NBSP"))).reveal().equals("\u00a0"), "environment NBSP");
        CredentialReference localRef = CredentialReference.local(new UUID(0, 1));
        fail(resolver.resolve(localRef), "credential_not_found", "The stored credential is unavailable");
        reject(() -> resolver.resolve(null), NullPointerException.class);
        reject(() -> CredentialResolver.environment(null), NullPointerException.class);
        Map<String, String> invalid = new HashMap<>();
        invalid.put("NULL", null);
        reject(() -> CredentialResolver.environment(invalid), NullPointerException.class);
        invalid.clear(); invalid.put(null, "value");
        reject(() -> CredentialResolver.environment(invalid), NullPointerException.class);
        final int[] localCalls = {0};
        CredentialResolver composite = CredentialResolver.composite(ref -> {
            localCalls[0]++;
            check(ref.equals(localRef), "composite local identity");
            return new ToolResult.Failure<>("credential_store_unavailable", "Stored credentials are unavailable");
        }, Collections.singletonMap("FIXTURE_KEY", SECRET));
        fail(composite.resolve(localRef), "credential_store_unavailable", "Stored credentials are unavailable");
        check(success(composite.resolve(CredentialReference.environment("FIXTURE_KEY"))).reveal().equals(SECRET), "composite environment");
        check(localCalls[0] == 1, "composite route count");
        reject(() -> CredentialResolver.composite(null, Collections.emptyMap()), NullPointerException.class);
    }

    private static void store(Path owned) throws Exception {
        Path path = owned.resolve("nested").resolve("credentials.sqlite3");
        CredentialReference first;
        CredentialReference second;
        try (LocalCredentialStore store = new LocalCredentialStore(
                path.getParent().resolve(".." ).resolve("nested").resolve(path.getFileName()), CLOCK)) {
            first = success(store.insert(SecretValue.of(SECRET)));
            second = success(store.insert(SecretValue.of("fixture-only-second")));
            check(first.kind() == CredentialReference.Kind.LOCAL && !first.equals(second), "unique local IDs");
            safe(first.toString());
            check(success(store.contains(first)) && success(store.contains(second)), "contains");
            check(success(store.resolve(first)).reveal().equals(SECRET), "stored exact value");
            check(!success(store.contains(CredentialReference.local(new UUID(0, 2)))), "missing contains");
            fail(store.resolve(CredentialReference.local(new UUID(0, 2))), "credential_not_found", "The stored credential is unavailable");
            unavailable(store.resolve(CredentialReference.environment("FIXTURE_KEY")));
            unavailable(store.contains(CredentialReference.environment("FIXTURE_KEY")));
            check(!success(store.deleteIfUnreferenced(CredentialReference.environment("FIXTURE_KEY"), Collections.emptySet())), "environment not deleted");
            check(!success(store.deleteIfUnreferenced(first, Collections.singleton(first))), "retain shared reference");
            Set<CredentialReference> retained = new HashSet<>();
            retained.add(first); retained.add(CredentialReference.environment("FIXTURE_KEY"));
            check(success(store.collectUnreferenced(retained)) == 1, "collect exact count");
            check(!success(store.contains(second)), "collect removed unreferenced");
            check(success(store.resolve(first)).reveal().equals(SECRET), "collect retained value");
            check(!success(store.deleteIfUnreferenced(second, Collections.emptySet())), "already missing delete");
            check(success(store.collectUnreferenced(retained)) == 0, "collect idempotent");
            reject(() -> store.insert(null), NullPointerException.class);
            reject(() -> store.resolve(null), NullPointerException.class);
            reject(() -> store.contains(null), NullPointerException.class);
            reject(() -> store.collectUnreferenced(Collections.singleton(null)), NullPointerException.class);
            reject(() -> store.deleteIfUnreferenced(first, Collections.singleton(null)), NullPointerException.class);
            reject(() -> store.collectUnreferenced(null), NullPointerException.class);
            if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
                check(Files.getPosixFilePermissions(path).equals(EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)), "DB owner-only POSIX mode");
                posixChecked++;
            } else posixSkipped++;
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
                    Statement query = connection.createStatement()) {
                Set<String> tables = new HashSet<>();
                try (ResultSet result = query.executeQuery("select name from sqlite_master where type='table' and name not glob 'sqlite_*'")) {
                    while (result.next()) tables.add(result.getString(1));
                }
                check(tables.equals(Collections.singleton("credentials")), "exact current tables");
                Set<String> columns = new HashSet<>();
                try (ResultSet result = query.executeQuery("pragma table_xinfo(credentials)")) {
                    while (result.next()) {
                        check(result.getString("dflt_value") == null && result.getInt("hidden") == 0, "column defaults and visibility");
                        columns.add(result.getString("name") + ":" + result.getString("type") + ":"
                                + result.getInt("notnull") + ":" + result.getInt("pk"));
                    }
                }
                check(columns.equals(new HashSet<>(Arrays.asList("credential_id:TEXT:0:1", "secret_value:BLOB:1:0",
                        "created_at:TEXT:1:0", "updated_at:TEXT:1:0"))), "exact current columns");
                try (ResultSet result = query.executeQuery("pragma journal_mode")) {
                    check(result.next() && result.getString(1).equals("wal"), "WAL mode");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "select secret_value,created_at,updated_at from credentials where credential_id=?")) {
                    statement.setString(1, first.value());
                    try (ResultSet result = statement.executeQuery()) {
                        check(result.next(), "saved row");
                        check(Arrays.equals(result.getBytes(1), SECRET.getBytes(StandardCharsets.UTF_8)), "exact UTF8 BLOB");
                        check(result.getString(2).equals(Instant.EPOCH.toString())
                                && result.getString(3).equals(Instant.EPOCH.toString()), "fixed clock timestamps");
                        check(!result.next(), "single identity row");
                    }
                }
            }
        }
        try (LocalCredentialStore reopened = new LocalCredentialStore(path, CLOCK)) {
            check(success(reopened.resolve(first)).reveal().equals(SECRET), "reopen saved credential");
            check(success(reopened.deleteIfUnreferenced(first, Collections.emptySet())), "delete unretained");
            fail(reopened.resolve(first), "credential_not_found", "The stored credential is unavailable");
            reopened.close();
            unavailable(reopened.resolve(first));
            unavailable(reopened.contains(first));
            unavailable(reopened.insert(SecretValue.of(SECRET)));
            unavailable(reopened.deleteIfUnreferenced(first, Collections.emptySet()));
            unavailable(reopened.collectUnreferenced(Collections.emptySet()));
        }
        Path never = owned.resolve("closed-never-created.sqlite3");
        LocalCredentialStore closed = new LocalCredentialStore(never, CLOCK);
        closed.close(); unavailable(closed.insert(SecretValue.of(SECRET)));
        check(!Files.exists(never), "closed store no write");
        Path parentFile = owned.resolve("parent-is-file");
        Files.write(parentFile, new byte[] {1});
        try (LocalCredentialStore blocked = new LocalCredentialStore(parentFile.resolve("database"), CLOCK)) {
            unavailable(blocked.insert(SecretValue.of(SECRET)));
        }
        check(Arrays.equals(new byte[] {1}, Files.readAllBytes(parentFile)), "IO failure no overwrite");
    }

    private static void invalidStores(Path owned) throws Exception {
        for (String mode : new String[] {"foreign", "extra-table", "extra-column", "default-column", "hidden-column", "empty", "malformed"}) {
            Path path = owned.resolve(mode + ".sqlite3");
            CredentialReference ref = CredentialReference.local(new UUID(0, 3));
            if (mode.equals("empty")) Files.createFile(path);
            else if (mode.equals("malformed")) Files.write(path, "fixture-only-not-a-db".getBytes(StandardCharsets.UTF_8));
            else if (mode.equals("foreign")) {
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path); Statement statement = connection.createStatement()) {
                    statement.execute("create table unrelated(value text)");
                    statement.execute("insert into unrelated values ('fixture-only-retained')");
                }
            } else {
                try (LocalCredentialStore store = new LocalCredentialStore(path, CLOCK)) { ref = success(store.insert(SecretValue.of(SECRET))); }
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path); Statement statement = connection.createStatement()) {
                    if (mode.equals("extra-table")) statement.execute("create table unexpected(value text)");
                    else if (mode.equals("extra-column")) statement.execute("alter table credentials add column unexpected text");
                    else if (mode.equals("default-column")) {
                        statement.execute("alter table credentials rename to old_credentials");
                        statement.execute("create table credentials(credential_id text primary key,secret_value blob not null,created_at text not null default 'fixture',updated_at text not null)");
                        statement.execute("insert into credentials select * from old_credentials");
                        statement.execute("drop table old_credentials");
                    } else {
                        statement.execute("alter table credentials add column hidden text generated always as (credential_id) virtual");
                    }
                }
            }
            byte[] before = Files.readAllBytes(path);
            byte[] beforeWal = bytesIfPresent(path.resolveSibling(path.getFileName() + "-wal"));
            try (LocalCredentialStore store = new LocalCredentialStore(path, CLOCK)) {
                unavailable(store.insert(SecretValue.of(SECRET)));
                unavailable(store.resolve(ref));
                unavailable(store.contains(ref));
                unavailable(store.deleteIfUnreferenced(ref, Collections.emptySet()));
                unavailable(store.collectUnreferenced(Collections.emptySet()));
            }
            check(Arrays.equals(before, Files.readAllBytes(path)), "invalid database byte preservation");
            check(Arrays.equals(beforeWal, bytesIfPresent(path.resolveSibling(path.getFileName() + "-wal"))), "invalid WAL preservation");
            if (mode.equals("foreign")) {
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path); Statement statement = connection.createStatement();
                        ResultSet result = statement.executeQuery("select value from unrelated")) {
                    check(result.next() && result.getString(1).equals("fixture-only-retained") && !result.next(), "foreign row retained");
                }
            } else if (!mode.equals("empty") && !mode.equals("malformed")) {
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path); Statement statement = connection.createStatement();
                        ResultSet result = statement.executeQuery("select secret_value from credentials")) {
                    check(result.next() && Arrays.equals(result.getBytes(1), SECRET.getBytes(StandardCharsets.UTF_8)) && !result.next(), "invalid schema row retained");
                }
            }
        }
    }

    private static void symlink(Path owned) throws Exception {
        Path target = owned.resolve("link-target.sqlite3");
        Path link = owned.resolve("link.sqlite3");
        CredentialReference ref;
        try (LocalCredentialStore store = new LocalCredentialStore(target, CLOCK)) { ref = success(store.insert(SecretValue.of(SECRET))); }
        try { Files.createSymbolicLink(link, target.getFileName()); }
        catch (UnsupportedOperationException | java.io.IOException | SecurityException unavailable) {
            symlinkSkipped++; return;
        }
        // Existing policy follows links; this port adds no private filesystem policy.
        try (LocalCredentialStore store = new LocalCredentialStore(link, CLOCK)) {
            check(success(store.resolve(ref)).reveal().equals(SECRET), "existing symlink follows target");
        }
        check(Files.isSymbolicLink(link), "link preserved");
        symlinkChecked++;
    }

    private static byte[] bytesIfPresent(Path path) throws Exception { return Files.exists(path) ? Files.readAllBytes(path) : null; }
    private static void unavailable(ToolResult<?> result) { fail(result, "credential_store_unavailable", "Stored credentials are unavailable"); }
    private static void fail(ToolResult<?> result, String code, String message) {
        check(result instanceof ToolResult.Failure<?>, "failure type");
        ToolResult.Failure<?> failure = (ToolResult.Failure<?>) result;
        check(failure.code().equals(code), "failure code");
        check(failure.message().equals(message), "failure message");
        safe(failure.toString());
        safe(EngineJson.create().toJson(failure));
    }
    @SuppressWarnings("unchecked") private static <T> T success(ToolResult<T> result) {
        check(result instanceof ToolResult.Success<?>, "success type");
        safe(result.toString());
        return ((ToolResult.Success<T>) result).value();
    }
    private interface Checked { void run() throws Exception; }
    private static void reject(Checked action, Class<? extends Throwable> type) {
        try { action.run(); }
        catch (Throwable failure) {
            check(type.isInstance(failure), "exception type");
            StringWriter diagnostic = new StringWriter();
            failure.printStackTrace(new PrintWriter(diagnostic));
            safe(diagnostic.toString());
            return;
        }
        throw new AssertionError("Expected rejected synthetic input");
    }
    private static void safe(String text) {
        check(!text.contains(SECRET) && !text.contains("fixture-only-second"), "redacted diagnostics");
    }
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
