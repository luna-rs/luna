package io.luna.game.model.item.economy;

import com.google.gson.reflect.TypeToken;
import io.luna.game.model.def.ItemDefinition;
import io.luna.util.GsonUtils;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent developer seed overrides kept separately from hand-edited JSONC definitions.
 * Writes run on an I/O worker; readers observe an immutable, atomically published snapshot.
 *
 * @author lare96
 */
public final class EconomySeedOverrides {
    private static final Path PATH = Path.of("data", "game", "def", "items", "economy_seed_overrides.json");
    private static volatile Map<Integer, Integer> prices = Map.of();

    private EconomySeedOverrides() {}

    public static Integer get(int id) { return prices.get(id); }

    public static void load() throws IOException { load(PATH); }

    static synchronized void load(Path path) throws IOException {
        if (!Files.exists(path)) { prices = Map.of(); return; }
        try (var reader = Files.newBufferedReader(path)) {
            Map<Integer, Integer> loaded = GsonUtils.GSON.fromJson(reader,
                    new TypeToken<Map<Integer, Integer>>() {}.getType());
            if (loaded == null) throw new IOException("Invalid economy seed overrides.");
            for (var entry : loaded.entrySet()) validate(entry.getKey(), entry.getValue());
            prices = Map.copyOf(loaded);
        }
    }

    public static void validate(int id, int price) {
        var def = ItemDefinition.ALL.retrieve(id);
        if (id == 995 || def.isNoted() || !def.isTradeable()) {
            throw new IllegalArgumentException("Choose a tradeable, unnoted item other than coins.");
        }
        ItemPriceData.checkPrice(id, price);
    }

    public static void setAndSave(int id, int price) throws IOException { setAndSave(PATH, id, price); }

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
