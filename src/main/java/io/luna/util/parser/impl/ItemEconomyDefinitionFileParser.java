package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.def.ItemEconomyDefinition;
import io.luna.util.parser.JsonFileParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.HashSet;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Loads optional initial price and popularity overrides through Luna's JSONC definition pipeline.
 *
 * @author lare96
 */
public final class ItemEconomyDefinitionFileParser extends JsonFileParser<ItemEconomyDefinition> {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Creates the parser for economy item definitions.
     */
    public ItemEconomyDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "items", "item_economy.jsonc"));
    }

    @Override
    public ItemEconomyDefinition convert(JsonObject token) {
        int id = token.get("id").getAsBigDecimal().intValueExact();
        Integer price = token.has("initial_price") ?
                token.get("initial_price").getAsBigDecimal().intValueExact() : null;
        long popularity = token.has("initial_popularity") ?
                token.get("initial_popularity").getAsBigDecimal().longValueExact() : 0L;
        return new ItemEconomyDefinition(id, price, popularity);
    }

    @Override
    public void onCompleted(ImmutableList<ItemEconomyDefinition> tokenObjects) {
        var ids = new HashSet<Integer>();
        for (ItemEconomyDefinition definition : tokenObjects) {
            var item = ItemDefinition.ALL.get(definition.id()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown economy item ID: " + definition.id()));
            checkArgument(item.isTradeable() && !item.isNoted(),
                    "Economy definitions require tradeable, unnoted items: %s", definition.id());
            checkArgument(ids.add(definition.id()), "Duplicate economy item ID: %s", definition.id());
        }
        ItemEconomyDefinition.ALL.storeAndLock(tokenObjects);
        logger.debug("Loaded {} item economy definitions!", box(tokenObjects.size()));
    }
}
