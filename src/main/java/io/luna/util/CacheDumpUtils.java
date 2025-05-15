package io.luna.util;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.def.VarBitDefinition;
import io.luna.game.model.def.VarpDefinition;
import io.luna.game.model.def.WidgetDefinition;

import java.nio.file.Paths;

/**
 * A utility class containing functions that help with dumping data from the cache.
 *
 * @author lare96
 */
public final class CacheDumpUtils {

    /**
     * Represents a single cache dump request.
     */
    private static final class Dump {

        /**
         * The file to dump to.
         */
        private final String fileName;

        /**
         * The elements to dump.
         */
        private final Iterable<?> elements;

        /**
         * Creates a new {@link Dump}.
         *
         * @param fileName The file to dump to.
         * @param elements The elements to dump.
         */
        private Dump(String fileName, Iterable<?> elements) {
            this.fileName = fileName;
            this.elements = elements;
        }
    }

    /**
     * All dump requests that will be run once {@link #dump()} is called.
     */
    private static final ImmutableList<Dump> DUMPS = ImmutableList.of(
            new Dump("items.json", ItemDefinition.ALL),
            new Dump("objects.json", GameObjectDefinition.ALL),
            new Dump("npcs.json", NpcDefinition.ALL),
            new Dump("varbits.json", VarBitDefinition.ALL),
            new Dump("varps.json", VarpDefinition.ALL),
            new Dump("widgets.json", WidgetDefinition.ALL)
    );

    /**
     * Dumps all data from {@link #DUMPS} in JSON, to their respective files. The cache must be loaded in memory,
     * and all decoders must have successfully run for this to complete normally.
     *
     * @throws Exception If any errors occur.
     */
    public static void dump() throws Exception {
        for (Dump dump : DUMPS) {
            JsonArray array = new JsonArray();
            for (Object def : dump.elements) {
                array.add(GsonUtils.toJsonTree(def));
            }
            GsonUtils.writeJson(array, Paths.get("data", "dumps", dump.fileName));
        }
    }
}
