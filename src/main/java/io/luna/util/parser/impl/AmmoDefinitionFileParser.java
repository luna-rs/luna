package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.def.AmmoDefinition;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Graphic;
import io.luna.util.GsonUtils;
import io.luna.util.parser.JsonFileParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.function.BiFunction;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Parses ranged ammo definitions from {@code data/game/def/equipment/ammo.json}.
 *
 * @author lare96
 */
public final class AmmoDefinitionFileParser extends JsonFileParser<AmmoDefinition> {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Creates a new {@link AmmoDefinitionFileParser}.
     */
    public AmmoDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "equipment", "ammo.json"));
    }

    @Override
    public AmmoDefinition convert(JsonObject token) {
        int id = token.get("id").getAsInt();
        int strength = token.get("strength").getAsInt();
        Graphic startGraphic = token.has("start_graphic") ?
                readGraphic(token.get("start_graphic").getAsJsonObject()) : null;
        Graphic endGraphic = token.has("end_graphic") ?
                readGraphic(token.get("end_graphic").getAsJsonObject()) : null;
        BiFunction<Mob, Mob, LocalProjectile> projectile = readProjectile(token.get("projectile").getAsJsonObject());
        ImmutableSet<Integer> bows = ImmutableSet.copyOf(GsonUtils.getAsType(token.get("bows"), Integer[].class));
        return new AmmoDefinition(id, strength, startGraphic, endGraphic, projectile, bows);
    }

    @Override
    public void onCompleted(ImmutableList<AmmoDefinition> tokenObjects) {
        AmmoDefinition.ALL.storeAndLock(tokenObjects);
        logger.debug("Loaded {} ammo definitions!", box(tokenObjects.size()));
    }
}