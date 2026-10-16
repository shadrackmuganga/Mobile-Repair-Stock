package com.mobilerepair.stock.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Durable {@link DocStore} backed by PostgreSQL (or any JDBC-compatible server).
 *
 * <p>Every document is stored as a row of {@code documents(collection, id, data)}
 * where {@code data} is the JSON serialisation of the document. Because the whole
 * application state (orgData, users, organisations, ...) is stored through the
 * {@link DocStore} abstraction, this makes the data survive restarts/redeploys.
 *
 * <p>The connection string accepts the standard PostgreSQL URL forms handed out
 * by hosting providers ({@code postgres://}, {@code postgresql://}) as well as a
 * ready-made {@code jdbc:postgresql://} URL.
 */
public class PostgresDocStore implements DocStore {

    private static final Set<String> SSL_MODES =
            Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");
    private static final Set<String> PLACEHOLDER_USERS =
            Set.of("user", "your_real_user", "your_user", "<user>");
    private static final Set<String> PLACEHOLDER_PASSWORDS =
            Set.of("password", "your_real_password", "your_password", "<password>");

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final ObjectMapper mapper = new ObjectMapper();
    private final MapType mapType;

    public PostgresDocStore(String connectionUrl, String username, String password) {
        this.mapper.findAndRegisterModules();
        this.mapType = mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);
        this.jdbcUrl = toJdbcUrl(connectionUrl);
        this.username = resolveUser(connectionUrl, username);
        this.password = resolvePassword(connectionUrl, password);
        init();
    }

    private void init() {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS documents ("
                            + "collection text NOT NULL, "
                            + "id text NOT NULL, "
                            + "data text NOT NULL, "
                            + "updated_at timestamptz NOT NULL DEFAULT now(), "
                            + "PRIMARY KEY (collection, id))");
        } catch (SQLException e) {
            throw new DocStoreException("Unable to initialise the documents table", e);
        }
    }

    @Override
    public List<Map<String, Object>> list(String collection) {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, data FROM documents WHERE collection = ?")) {
            statement.setString(1, collection);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(read(rows.getString(1), rows.getString(2)));
                }
            }
        } catch (SQLException e) {
            throw new DocStoreException("Unable to list collection " + collection, e);
        }
        return result;
    }

    @Override
    public Optional<Map<String, Object>> get(String collection, String id) {
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT data FROM documents WHERE collection = ? AND id = ?")) {
            statement.setString(1, collection);
            statement.setString(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(id, rows.getString(1)));
            }
        } catch (SQLException e) {
            throw new DocStoreException("Unable to read " + collection + "/" + id, e);
        }
    }

    @Override
    public void put(String collection, String id, Map<String, Object> document) {
        String json;
        try {
            json = mapper.writeValueAsString(document == null ? new LinkedHashMap<>() : document);
        } catch (Exception e) {
            throw new DocStoreException("Unable to serialise " + collection + "/" + id, e);
        }
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO documents (collection, id, data) VALUES (?, ?, ?) "
                                + "ON CONFLICT (collection, id) DO UPDATE SET data = EXCLUDED.data, updated_at = now()")) {
            statement.setString(1, collection);
            statement.setString(2, id);
            statement.setString(3, json);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DocStoreException("Unable to write " + collection + "/" + id, e);
        }
    }

    private Map<String, Object> read(String id, String json) {
        try {
            Map<String, Object> data = mapper.readValue(json, mapType);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.putAll(data);
            return result;
        } catch (Exception e) {
            throw new DocStoreException("Unable to deserialise document " + id, e);
        }
    }

    private Connection open() throws SQLException {
        Properties properties = new Properties();
        if (username != null) {
            properties.setProperty("user", username);
        }
        if (password != null) {
            properties.setProperty("password", password);
        }
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    static String toJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new DocStoreException("A database URL is required");
        }
        String value = url.trim();
        if (!value.startsWith("jdbc:")) {
            if (value.startsWith("postgres://")) {
                value = value.substring("postgres://".length());
            } else if (value.startsWith("postgresql://")) {
                value = value.substring("postgresql://".length());
            }
            // Drop any user:password@ section; credentials are passed as properties.
            int at = value.lastIndexOf('@');
            if (at >= 0) {
                value = value.substring(at + 1);
            }
            value = "jdbc:postgresql://" + value;
        }
        return sanitizeSslMode(value);
    }

    static String resolveUser(String connectionUrl, String username) {
        if (usable(username) && !PLACEHOLDER_USERS.contains(username.trim().toLowerCase(Locale.ROOT))) {
            return username.trim();
        }
        return extractUser(connectionUrl);
    }

    static String resolvePassword(String connectionUrl, String password) {
        if (usable(password) && !PLACEHOLDER_PASSWORDS.contains(password.trim().toLowerCase(Locale.ROOT))) {
            return password.trim();
        }
        return extractPassword(connectionUrl);
    }

    private static boolean usable(String value) {
        return value != null && !value.isBlank();
    }

    static String sanitizeSslMode(String jdbcUrl) {
        int queryAt = jdbcUrl.indexOf('?');
        String base = queryAt >= 0 ? jdbcUrl.substring(0, queryAt) : jdbcUrl;
        String query = queryAt >= 0 ? jdbcUrl.substring(queryAt + 1) : "";
        Map<String, String> params = new LinkedHashMap<>();
        if (!query.isBlank()) {
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                String key = (eq >= 0 ? part.substring(0, eq) : part).trim();
                String val = eq >= 0 ? part.substring(eq + 1).trim() : "";
                if (!key.isEmpty()) {
                    params.put(key, val);
                }
            }
        }
        if (params.containsKey("sslmode")) {
            params.put("sslmode", normalizeSslMode(params.get("sslmode")));
        } else if (isRenderHost(base)) {
            params.put("sslmode", "require");
        }
        if (params.isEmpty()) {
            return base;
        }
        StringBuilder out = new StringBuilder(base).append('?');
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                out.append('&');
            }
            first = false;
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    private static String normalizeSslMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "require";
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        for (String mode : SSL_MODES) {
            if (lower.equals(mode)) {
                return mode;
            }
            if (lower.startsWith(mode)
                    && lower.length() > mode.length()
                    && !Character.isLetter(lower.charAt(mode.length()))) {
                return mode;
            }
        }
        return "require";
    }

    private static boolean isRenderHost(String jdbcUrl) {
        String host = jdbcUrl.toLowerCase(Locale.ROOT);
        return host.contains(".render.com") || host.contains("dpg-");
    }

    private static String extractUser(String url) {
        String userInfo = userInfo(url);
        if (userInfo == null) {
            return null;
        }
        int colon = userInfo.indexOf(':');
        String user = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
        return URLDecoder.decode(user, StandardCharsets.UTF_8);
    }

    private static String extractPassword(String url) {
        String userInfo = userInfo(url);
        if (userInfo == null) {
            return null;
        }
        int colon = userInfo.indexOf(':');
        if (colon < 0) {
            return null;
        }
        return URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8);
    }

    private static String userInfo(String url) {
        if (url == null) {
            return null;
        }
        String value = url.trim();
        if (value.startsWith("jdbc:")) {
            value = value.substring("jdbc:".length());
        }
        try {
            URI uri = URI.create(value.replace("postgres://", "postgresql://"));
            return uri.getUserInfo();
        } catch (IllegalArgumentException e) {
            int scheme = value.indexOf("://");
            String rest = scheme >= 0 ? value.substring(scheme + 3) : value;
            int at = rest.lastIndexOf('@');
            return at >= 0 ? rest.substring(0, at) : null;
        }
    }
}
