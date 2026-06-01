package io.luna.game.model.item.economy;

import io.luna.game.model.World;
import io.luna.util.SqlTask;

import java.sql.Connection;
import java.util.Queue;

/**
 * Adds one or more price history snapshots for economy items.
 * <p>
 * This task is used by the economy system after guide prices have changed. Each inserted row preserves an item's
 * current price/sample state alongside its previous price/sample state, allowing later systems, tools, or diagnostics
 * to inspect how prices moved over time.
 *
 * @author lare96
 */
public final class BatchPriceHistorySqlTask extends SqlTask {

    /**
     * The item price data snapshots to persist.
     */
    private final Queue<ItemPriceData> pending;

    /**
     * Creates a new {@link BatchPriceHistorySqlTask}.
     *
     * @param world The world submitting this SQL task.
     * @param pending The item price data snapshots to persist.
     */
    public BatchPriceHistorySqlTask(World world, Queue<ItemPriceData> pending) {
        super(world);
        this.pending = pending;
    }

    @Override
    public void run(Connection connection) throws Exception {
        if (pending.isEmpty()) {
            return;
        }

        try (var insert = connection.prepareStatement(
                "INSERT INTO price_history (item_id, price, last_price, samples, last_samples) " +
                        "VALUES (?, ?, ?, ?, ?)")) {
            for(;;) {
                ItemPriceData data = pending.poll();
                if(data == null) {
                    break;
                }
                insert.setInt(1, data.id);
                insert.setDouble(2, data.price);
                insert.setDouble(3, data.lastPrice);
                insert.setLong(4, data.samples);
                insert.setLong(5, data.lastSamples);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}