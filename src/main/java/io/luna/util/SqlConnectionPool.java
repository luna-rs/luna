package io.luna.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

import static java.util.Objects.requireNonNull;

/**
 * A wrapper for a {@code HikariCP} SQL connection pool (represented by {@link HikariDataSource}). The JDBC url and the
 * login credentials are shared across all instances.
 *
 * @author lare96 
 * @see <a href="https://github.com/brettwooldridge/HikariCP">HikariCP</a>
 */
public final class SqlConnectionPool {

    /**
     * The database host.
     */
    private static final String HOST = "localhost";

    /**
     * The database port.
     */
    private static final int PORT = 3306;

    /**
     * The database login username.
     */
    private static final String USERNAME = "luna_db";

    /**
     * The database login password.
     */
    private static final String PASSWORD = "guMQRNYf44fxERs4uHxLCWjCQ";

    /**
     * A builder for this connection pool.
     */
    public static final class Builder {

        /**
         * The pool's name.
         */
        private String poolName = "LunaSqlPool";

        /**
         * The database (default value is {@code USERNAME}).
         */
        private String database = USERNAME;

        /**
         * Sets the pool's name.
         *
         * @param poolName The new value.
         * @return This builder.
         */
        public Builder poolName(String poolName) {
            this.poolName = requireNonNull(poolName);
            return this;
        }

        /**
         * Sets the database (default value is {@code USERNAME}).
         *
         * @param database The new value.
         * @return This builder.
         */
        public Builder database(String database) {
            this.database = requireNonNull(database);
            return this;
        }

        /**
         * Creates a new connection pool.
         *
         * @return The new pool.
         * @throws SQLException If the pool cannot be created.
         */
        public SqlConnectionPool build() throws SQLException {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + HOST + ":" + PORT + "/" + database);
            config.setUsername(USERNAME);
            config.setPassword(PASSWORD);
            config.setPoolName(poolName);
            return new SqlConnectionPool(new HikariDataSource(config));
        }

    }

    /**
     * The backing connection pool.
     */
    private final HikariDataSource dataSource;

    /**
     * Creates a new {@link SqlConnectionPool}.
     *
     * @param dataSource The backing connection pool.
     */
    public SqlConnectionPool(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Forwards to {@link HikariDataSource#getConnection()}.
     */
    public Connection take() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Forwards to {@link HikariDataSource#close()}.
     */
    public void close() {
        dataSource.close();
    }

    /**
     * Forwards to {@link HikariDataSource#getPoolName()}.
     */
    public String getPoolName() {
        return dataSource.getPoolName();
    }

    /**
     * Forwards to {@link HikariDataSource#getJdbcUrl()}.
     */
    public String getJdbcUrl() {
        return dataSource.getJdbcUrl();
    }

    /**
     * Forwards to {@link HikariDataSource#getUsername()}.
     */
    public String getUsername() {
        return dataSource.getUsername();
    }

    /**
     * Forwards to {@link HikariDataSource#getPassword()}.
     */
    public String getPassword() {
        return dataSource.getPassword();
    }

    /**
     * Forwards to {@link HikariDataSource#getLoginTimeout()}.
     */
    public int getTimeout() throws SQLException {
        return dataSource.getLoginTimeout();
    }
}