package io.luna.game.model.item.economy;

import io.luna.game.model.World;
import io.luna.util.SqlTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Loads persisted economy guide prices from SQL.
 * <p>
 * This task reads every row from the {@code prices} table and restores it into the world's economy price cache. Each
 * row represents the latest known guide price for a tradeable item, including its previous price and sample counts.
 *
 * @author lare96
 */
public final class LoadEconomySqlTask extends SqlTask {

    /**
     * Creates a new {@link LoadEconomySqlTask}.
     *
     * @param world The world that owns the economy and SQL connection pool.
     */
    public LoadEconomySqlTask(World world) {
        super(world);
    }

    @Override
    public void run(Connection connection) throws Exception {
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM prices;")) {
            try (ResultSet results = select.executeQuery()) {
                while (results.next()) {
                    int itemId = results.getInt("item_id");
                    double price = results.getDouble("price");
                    double lastPrice = results.getDouble("last_price");
                    int samples = results.getInt("samples");
                    int lastSamples = results.getInt("last_samples");
                    world.getEconomy().prices.put(itemId, new ItemPriceData(itemId, price, lastPrice, samples, lastSamples));
                }
            }
        }
    }
}
