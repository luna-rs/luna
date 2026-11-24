
package io.luna.game.model.item.economy;

import com.google.gson.reflect.TypeToken;
import io.luna.game.model.def.ItemDefinition;
import io.luna.util.GsonUtils;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages persistent developer-defined seed price overrides separately from the standard item definitions.
 * <p>
 * Overrides are stored in an immutable snapshot published through a volatile reference, allowing concurrent reads
 * without synchronization. Loading and saving are synchronized to prevent concurrent modifications.
 * <p>
 * Changes are written to a temporary file before replacing the existing file. Atomic replacement is attempted when
 * supported by the filesystem, and the in-memory snapshot is updated only after persistence succeeds.
 * <p>
 * File operations are synchronous and should be executed from an I/O worker to avoid blocking the game thread.
 *
 * @author lare96
 */
public final class EconomySeedOverrides {

    private static final Path PATH = Path.of("data", "game", "def", "items", "economy_seed_overrides.json");
    private static volatile Map<Integer, Integer> prices = Map.of();

    private EconomySeedOverrides() {}

    /**
     * Returns the developer-defined seed price override for an item.
     *
     * @param id The item identifier.
     * @return The overridden price, or {@code null} if no override exists.
     */
    public static Integer get(int id) {
        return prices.get(id);
    }

    /**
     * Loads the persisted seed price overrides from the default file.
     *
     * @throws IOException If the file cannot be read or contains invalid override data.
     * @throws IllegalArgumentException If an override fails validation.
     */
    public static void load() throws IOException {
        load(PATH);
    }

    /**
     * Loads the persisted seed price overrides from the specified file.
     * <p>
     * If the file does not exist, all currently loaded overrides are cleared. The new immutable snapshot is published
     * only after every loaded override has been validated.
     *
     * @param path The file containing the seed price overrides.
     * @throws IOException If the file cannot be read or contains invalid override data.
     * @throws IllegalArgumentException If an override fails validation.
     */
    static synchronized void load(Path path) throws IOException {
        if (!Files.exists(path)) {
            prices = Map.of();
            return;
        }

        try (var reader = Files.newBufferedReader(path)) {
            Map<Integer, Integer> loaded = GsonUtils.GSON.fromJson(reader,
                    new TypeToken<Map<Integer, Integer>>() {}.getType());

            if (loaded == null) {
                throw new IOException("Invalid economy seed overrides.");
            }

            for (var entry : loaded.entrySet()) {
                validate(entry.getKey(), entry.getValue());
            }

            prices = Map.copyOf(loaded);
        }
    }

    /**
     * Validates an item and its proposed seed price override.
     * <p>
     * Only tradeable, unnoted items other than coins are eligible. The proposed price must also satisfy the economy's
     * existing price validation rules.
     *
     * @param id The item identifier.
     * @param price The proposed seed price.
     * @throws IllegalArgumentException If the item is ineligible or the proposed price is invalid.
     */
    public static void validate(int id, int price) {
        var def = ItemDefinition.ALL.retrieve(id);

        if (id == 995 || def.isNoted() || !def.isTradeable()) {
            throw new IllegalArgumentException("Choose a tradeable, unnoted item other than coins.");
        }

        ItemPriceData.checkPrice(id, price);
    }

    /**
     * Sets and persists a developer-defined seed price override to the default file.
     *
     * @param id The item identifier.
     * @param price The new seed price.
     * @throws IOException If the override cannot be persisted.
     * @throws IllegalArgumentException If the override fails validation.
     */
    public static void setAndSave(int id, int price) throws IOException {
        setAndSave(PATH, id, price);
    }

    /**
     * Sets and persists a developer-defined seed price override to the specified file.
     * <p>
     * Existing overrides are preserved, and the updated snapshot becomes visible to readers only after the file has
     * been successfully replaced. Atomic replacement is attempted where supported by the filesystem, with a regular
     * replacement used as a fallback.
     *
     * @param path The destination file.
     * @param id The item identifier.
     * @param price The new seed price.
     * @throws IOException If the override cannot be persisted.
     * @throws IllegalArgumentException If the override fails validation.
     */
    static synchronized void setAndSave(Path path, int id, int price) throws IOException {
        validate(id, price);

        var updated = new HashMap<>(prices);
        updated.put(id, price);

        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        GsonUtils.writeJson(updated, temporary);

        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }

        prices = Map.copyOf(updated);
    }
}
