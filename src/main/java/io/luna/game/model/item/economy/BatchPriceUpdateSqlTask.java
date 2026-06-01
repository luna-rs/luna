package io.luna.game.model.item.economy;

import io.luna.game.model.World;
import io.luna.util.SqlTask;

import java.sql.Connection;
import java.util.Collection;
import java.util.Iterator;

/**
 * Updates one or more active economy guide prices in SQL.
 * <p>
 * This task persists the latest price snapshot for each changed item into the {@code prices} table. If a row for an
 * item does not already exist, it is inserted. If a row already exists, the current price, previous price, current
 * sample count, and previous sample count are updated.
 * <p>
 * This table represents the latest known economy state. Historical movement snapshots should be written separately to
 * {@code price_history}.
 *
 * @author lare96
 */
public final class BatchPriceUpdateSqlTask extends SqlTask {

    /**
     * The item price data snapshots to insert or update.
     */
    private final Collection<ItemPriceData> pending;

    /**
     * Creates a new SQL task that persists active economy prices.
     *
     * @param world The world submitting this SQL task.
     * @param pending The item price data snapshots to persist.
     */
    public BatchPriceUpdateSqlTask(World world, Collection<ItemPriceData> pending) {
        super(world);
        this.pending = pending;
    }

    @Override
    public void run(Connection connection) throws Exception {
        if (pending.isEmpty()) {
            return;
        }

        try (var upsert = connection.prepareStatement(
                "INSERT INTO prices (item_id, price, last_price, samples, last_samples) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE price = ?, last_price = ?, samples = ?, last_samples = ?")) {
            Iterator<ItemPriceData> it = pending.iterator();
            while (it.hasNext()) {
                ItemPriceData data = it.next();
                it.remove();
                upsert.setInt(1, data.id);
                upsert.setDouble(2, data.price);
                upsert.setDouble(3, data.lastPrice);
                upsert.setLong(4, data.samples);
                upsert.setLong(5, data.lastSamples);
                upsert.setDouble(6, data.price);
                upsert.setDouble(7, data.lastPrice);
                upsert.setLong(8, data.samples);
                upsert.setLong(9, data.lastSamples);
                upsert.addBatch();
            }
            upsert.executeBatch();
        }
    }
}