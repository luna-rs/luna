package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.def.AmmoDefinition;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.AmmoType;
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
        super(Paths.get("data", "game", "def", "equipment", "ammo.jsonc"));
    }

    @Override
    protected BiFunction<Mob, Mob, LocalProjectile> readProjectile(JsonObject object) {
        if (!object.has("launch_delay")) {
            return super.readProjectile(object);
        }
        int id = object.get("id").getAsInt();
        int delay = object.get("launch_delay").getAsInt();
        int length = object.get("flight_length").getAsInt();
        int step = object.get("flight_step").getAsInt();
        int offset = object.get("launch_offset").getAsInt();
        int startHeight = object.get("start_height").getAsInt();
        int endHeight = object.get("end_height").getAsInt();
        int curve = object.get("curve").getAsInt();
        return (source, target) -> {
            int x = source.getPosition().getX();
            int y = source.getPosition().getY();
            int targetX = target.getPosition().getX();
            int targetY = target.getPosition().getY();
            // Lost City's npc_range measures to the nearest occupied tile, rather than the south-west corner.
            int size = Math.max(1, target.size());
            int dx = Math.max(Math.max(targetX - x, x - (targetX + size - 1)), 0);
            int dy = Math.max(Math.max(targetY - y, y - (targetY + size - 1)), 0);
            int duration = delay + length + Math.max(dx, dy) * step;
            return LocalProjectile.followEntity(source.getContext())
                    .setSourceEntity(source)
                    .setTargetEntity(target)
                    .setId(id)
                    .setTiming(delay, duration)
                    .setLaunchOffset(offset)
                    .setStartHeight(startHeight)
                    .setEndHeight(endHeight)
                    .setInitialSlope(curve)
                    .build();
        };
    }

    @Override
    public AmmoDefinition convert(JsonObject token) {
        AmmoType type = AmmoType.valueOf(token.get("type").getAsString());
        int strength = token.get("strength").getAsInt();
        Graphic startGraphic = token.has("start_graphic") ?
                readGraphic(token.get("start_graphic").getAsJsonObject()) : null;
        Graphic endGraphic = token.has("end_graphic") ?
                readGraphic(token.get("end_graphic").getAsJsonObject()) : null;
        BiFunction<Mob, Mob, LocalProjectile> projectile = readProjectile(token.get("projectile").getAsJsonObject());
        ImmutableSet<Integer> ammo = ImmutableSet.copyOf(GsonUtils.getAsType(token.get("ammo"), Integer[].class));
        ImmutableSet<Integer> weapons = ImmutableSet.copyOf(GsonUtils.getAsType(token.get("weapons"), Integer[].class));
        return new AmmoDefinition(type, strength, startGraphic, endGraphic, projectile, ammo, weapons);
    }

    @Override
    public void onCompleted(ImmutableList<AmmoDefinition> tokenObjects) {
        AmmoDefinition.loadAll(tokenObjects);
        logger.debug("Loaded {} ammo definitions!", box(tokenObjects.size()));
    }
}
