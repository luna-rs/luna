package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.WeaponDefinition;
import io.luna.game.model.def.WeaponModelAnimationDefinition;
import io.luna.game.model.mob.combat.Weapon;
import io.luna.game.model.mob.combat.WeaponPoison;
import io.luna.util.parser.JsonFileParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link JsonFileParser} that loads {@link WeaponDefinition} instances from the weapon definition file.
 * <p>
 * Each entry defines an item id, {@link Weapon} type, {@link WeaponPoison} state, and
 * {@link WeaponModelAnimationDefinition} used for stance and movement animation behavior.
 *
 * @author lare96
 */
public final class WeaponDefinitionFileParser extends JsonFileParser<WeaponDefinition> {

    /**
     * The logger used to report weapon definition loading progress.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Creates a new {@link WeaponDefinitionFileParser}.
     */
    public WeaponDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "equipment", "weapons.json"));
    }

    @Override
    public WeaponDefinition convert(JsonObject token) {
        int id = token.get("id").getAsInt();
        Weapon type = Weapon.valueOf(token.get("type").getAsString());
        WeaponPoison poison = token.has("poison") ? WeaponPoison.valueOf(token.get("poison").getAsString()) : null;
        WeaponModelAnimationDefinition model = null;
        if (token.has("model")) {
            JsonObject modelJson = token.get("model").getAsJsonObject();
            model = new WeaponModelAnimationDefinition(
                    modelJson.get("standing").getAsInt(),
                    modelJson.get("walking").getAsInt(),
                    modelJson.get("running").getAsInt()
            );
        }
        return new WeaponDefinition(id, type, poison, model);
    }

    @Override
    public void onCompleted(ImmutableList<WeaponDefinition> tokenObjects) {
        WeaponDefinition.ALL.storeAndLock(tokenObjects);
        logger.debug("Loaded {} weapon definitions!", box(tokenObjects.size()));
    }
}