package io.luna.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.luna.Luna;

import java.sql.Connection;
import java.sql.SQLException;

import static java.util.Objects.requireNonNull;

/**
 * A wrapper around a {@link HikariDataSource}, representing a managed SQL connection pool for Luna.
 * <p>
 * This class provides a simplified abstraction layer over the HikariCP library, handling the creation, configuration,
 * and management of a connection pool. It is designed for high-performance database access within the Luna engine,
 * where connection reuse and concurrency efficiency are critical.
 * </p>
 * By default, the connection pool reads its configuration from {@code Luna.settings().database()} unless
 * overridden via the builder.
 *
 * @author lare96
 * @see <a href="https://github.com/brettwooldridge/HikariCP">HikariCP</a>
 */
public final class SqlConnectionPool {

    /**
     * A simple immutable container holding global database connection settings.
     * <p>
     * These values (host, port, username, password) are typically sourced from {@code data/config/luna.json}
     * and represent the baseline connection configuration.
     * </p>
     */
    public static final class DatabaseSettings {

        /**
         * The database host address.
         */
        private final String host;

        /**
         * The database port number.
         */
        private final int port;

        /**
         * The username used to authenticate with the database.
         */
        private final String username;

        /**
         * The password used to authenticate with the database.
         */
        private final String password;

        /**
         * Creates a new {@link DatabaseSettings} object.
         *
         * @param host The database host.
         * @param port The database port.
         * @param username The database username.
         * @param password The database password.
         */
        private DatabaseSettings(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        /**
         * @return The database host address.
         */
        public String getHost() {
            return host;
        }

        /**
         * @return The database port number.
         */
        public int getPort() {
            return port;
        }

        /**
         * @return The database username.
         */
        public String getUsername() {
            return username;
        }

        /**
         * @return The database password.
         */
        public String getPassword() {
            return password;
        }
    }

    /**
     * A fluent builder class for constructing {@link SqlConnectionPool} instances.
     * <p>
     * The builder supports overriding default configuration values defined in {@link Luna#settings()}, such
     * as host, port, and credentials.
     * </p>
     *
     * <p>
     * Example:
     * <pre>
     *     SqlConnectionPool pool = new SqlConnectionPool.Builder()
     *             .host("127.0.0.1")
     *             .port(3306)
     *             .database("luna_game")
     *             .poolName("LunaSqlPool")
     *             .build();
     * </pre>
     * </p>
     */
    public static final class Builder {

        /**
         * The database host address.
         */
        private String host = Luna.settings().database().host;

        /**
         * The database port number.
         */
        private int port = Luna.settings().database().port;

        /**
         * The database username.
         */
        private String username = Luna.settings().database().username;

        /**
         * The database password.
         */
        private String password = Luna.settings().database().password;

        /**
         * The name assigned to this pool instance.
         */
        private String poolName = "LunaSqlPool";

        /**
         * The name of the database to connect to.
         */
        private String database;

        /**
         * Sets the database host address.
         *
         * @param host The new host address.
         * @return This builder instance.
         */
        public Builder host(String host) {
            this.host = requireNonNull(host);
            return this;
        }

        /**
         * Sets the database port number.
         *
         * @param port The new port.
         * @return This builder instance.
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the database username.
         *
         * @param username The new username.
         * @return This builder instance.
         */
        public Builder username(String username) {
            this.username = requireNonNull(username);
            return this;
        }

        /**
         * Sets the database password.
         *
         * @param password The new password.
         * @return This builder instance.
         */
        public Builder password(String password) {
            this.password = requireNonNull(password);
            return this;
        }

        /**
         * Sets the pool’s display name (as shown in management tools or logs).
         *
         * @param poolName The new pool name.
         * @return This builder instance.
         */
        public Builder poolName(String poolName) {
            this.poolName = requireNonNull(poolName);
            return this;
        }

        /**
         * Sets the name of the target database.
         *
         * @param database The database name.
         * @return This builder instance.
         */
        public Builder database(String database) {
            this.database = requireNonNull(database);
            return this;
        }

        /**
         * Builds a new {@link SqlConnectionPool} using the provided configuration.
         * <p>
         * The resulting pool will automatically connect to the specified database using HikariCP’s internal
         * thread-safe connection management.
         * </p>
         *
         * @return A fully initialized {@link SqlConnectionPool}.
         * @throws SQLException If the connection pool cannot be created.
         * @throws IllegalStateException If the database name was not provided.
         */
        public SqlConnectionPool build() throws SQLException {
            if (database == null) {
                throw new IllegalStateException("Database was not set!");
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
            config.setUsername(username);
            config.setPassword(password);
            config.setPoolName(poolName);

            return new SqlConnectionPool(new HikariDataSource(config));
        }
    }

    /**
     * The underlying HikariCP data source that manages pooled connections.
     */
    private final HikariDataSource dataSource;

    /**
     * Constructs a new {@link SqlConnectionPool} with the given data source.
     *
     * @param dataSource The HikariCP data source backing this pool.
     */
    public SqlConnectionPool(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Retrieves a new SQL connection from the pool.
     * <p>
     * The returned connection is automatically managed by HikariCP and should be closed by the caller when
     * no longer in use (e.g., via try-with-resources).
     * </p>
     *
     * @return A valid {@link Connection} from the pool.
     * @throws SQLException If a database access error occurs.
     */
    public Connection take() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Closes the underlying Hikari connection pool, releasing all resources.
     */
    public void close() {
        dataSource.close();
    }
}