package io.luna.util;

import io.luna.game.model.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;

/**
 * Represents a unit of SQL work that can be executed asynchronously.
 * <p>
 * A task automatically borrows a {@link Connection} from the world's SQL connection pool, runs the task-specific SQL
 * logic, and then returns the connection when finished. Subclasses only need to implement {@link #run(Connection)} and
 * should not close the connection themselves.
 *
 * @author lare96
 */
public abstract class SqlTask implements Runnable {

    /**
     * The logger used to report SQL task failures.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The world that owns the SQL connection pool used by this task.
     */
    protected final World world;

    /**
     * Creates a new {@link SqlTask}.
     *
     * @param world The world that provides the SQL connection pool.
     */
    public SqlTask(World world) {
        this.world = world;
    }

    @Override
    public void run() {
        try (Connection connection = world.getConnectionPool().take()) {
            run(connection);
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    /**
     * Executes the task-specific SQL logic.
     * <p>
     * The provided connection is managed by {@link #run()} and must not be closed by implementations.
     *
     * @param connection The SQL connection used to execute this task.
     * @throws Exception If this SQL task fails.
     */
    public abstract void run(Connection connection) throws Exception;
}