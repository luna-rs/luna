package io.luna.util.parser.impl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import game.item.degradable.DegradableItemType;
import io.luna.game.model.def.DegradableItemDefinition;
import io.luna.util.parser.JsonFileParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link JsonFileParser} that loads grouped degradable-item definitions from {@code degradable.json}.
 * <p>
 * The source file is expected to be grouped by {@link DegradableItemType}, where each top-level entry contains an
 * {@code items} array describing the degradation chain transitions for that type. Parsed definitions are collected into
 * {@link #loadedItems} and published to {@link DegradableItemDefinition} once parsing has completed.
 *
 * @author lare96
 */
public final class DegradableItemDefinitionFileParser extends JsonFileParser<DegradableItemDefinition> {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * All parsed degradable-item definitions collected during file parsing, grouped by degradable item type.
     */
    private static final SetMultimap<DegradableItemType, DegradableItemDefinition> loadedItems = HashMultimap.create();

    /**
     * Creates a new {@link DegradableItemDefinitionFileParser}.
     */
    public DegradableItemDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "equipment", "degradable.json"));
    }

    @Override
    public DegradableItemDefinition convert(JsonObject token) {
        DegradableItemType type = DegradableItemType.valueOf(token.get("type").getAsString());
        JsonArray items = token.get("items").getAsJsonArray();

        for (JsonElement element : items) {
            JsonObject item = element.getAsJsonObject();
            int prevId = item.get("prev_id").getAsInt();
            int id = item.get("id").getAsInt();
            int nextId = item.get("next_id").getAsInt();

            loadedItems.put(type, new DegradableItemDefinition(prevId, id, nextId, type));
        }
        return null;
    }

    @Override
    public void onCompleted(ImmutableList<DegradableItemDefinition> tokenObjects) {
        DegradableItemDefinition.loadAll(loadedItems);
        logger.debug("Loaded {} degradable items!", box(loadedItems.size()));
    }
}