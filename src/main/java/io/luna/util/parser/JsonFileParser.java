package io.luna.util.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.LocalProjectile.TargetBuilder;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.def.WeaponModelDefinition;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.block.PlayerModelAnimation;
import io.luna.util.GsonUtils;

import java.io.BufferedReader;
import java.nio.file.Path;
import java.util.function.BiFunction;

/**
 * A {@link FileParser} implementation designed to parse {@code JSON} tokens.
 *
 * @param <R> The token object type.
 * @author lare96
 */
public abstract class JsonFileParser<R> extends FileParser<JsonArray, JsonObject, R> {

    /**
     * Creates a new {@link JsonFileParser}.
     *
     * @param filePath The path to the files being parsed.
     */
    public JsonFileParser(Path filePath) {
        super(filePath);
    }

    @Override
    public JsonObject parse(JsonArray parser) {
        JsonElement element = parser.get(currentIndex);
        return element.getAsJsonObject();
    }

    @Override
    public JsonArray newParser(BufferedReader reader) {
        JsonElement jsonReader = GsonUtils.GSON.fromJson(reader, JsonElement.class);
        return jsonReader.getAsJsonArray();
    }

    @Override
    public boolean hasNext(JsonArray parser) {
        return currentIndex + 1 <= parser.size();
    }

    /**
     * Parses a graphic definition from a nested JSON object.
     *
     * <p>Expected fields:
     * <ul>
     *     <li>{@code id} - graphic id</li>
     *     <li>{@code height} - display height</li>
     *     <li>{@code delay} - render delay</li>
     * </ul>
     *
     * @param object The JSON object describing the graphic.
     * @return The parsed {@link Graphic}.
     */
    protected Graphic readGraphic(JsonObject object) {
        int id = object.get("id").getAsInt();
        int height = object.get("height").getAsInt();
        int delay = object.get("delay").getAsInt();
        return new Graphic(id, height, delay);
    }

    /**
     * Reads distance-based timing shared by ranged ammunition and combat spells. Arrival is the launch delay
     * plus the signed flight length and distance times the per-tile step. NPC distance uses the nearest occupied
     * tile. The signed length is retained, including spells whose close-range arrival precedes their launch cycle.
     *
     * @param object The projectile fields, including launch delay, flight length, step, and launch offset.
     * @return A function that snapshots distance when the projectile is created.
     */
    private BiFunction<Mob, Mob, LocalProjectile> readTimedProjectile(JsonObject object) {
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

    /**
     * Parses projectile data and returns a builder function that creates a {@link LocalProjectile} for a given source
     * and target pair.
     * <p>
     * Expected fields:
     * <ul>
     *     <li>{@code id} - projectile graphic id</li>
     *     <li>{@code delay} - legacy end cycle passed to {@link TargetBuilder#setTicksToEnd(int)}</li>
     *     <li>{@code speed} - legacy start cycle passed to {@link TargetBuilder#setTicksToStart(int)}</li>
     *     <li>{@code start_height} - launch height</li>
     *     <li>{@code end_height} - impact height</li>
     *     <li>{@code curve} - initial slope</li>
     * </ul>
     * <p>
     * Definitions with {@code launch_delay} instead use explicit cycle timing: arrival equals
     * {@code launch_delay + flight_length + distance * flight_step}, with {@code launch_offset} controlling
     * where the projectile first appears. Legacy delay/speed fields are not needed for that format.
     * <p>
     * The produced projectile always uses {@link ChunkUpdatableView#globalView()}.
     *
     * @param object The JSON object describing projectile behavior.
     * @return A function that builds a projectile for the provided source and target mobs.
     */
    protected BiFunction<Mob, Mob, LocalProjectile> readProjectile(JsonObject object) {
        if (object.has("launch_delay")) {
            return readTimedProjectile(object);
        }
        int id = object.get("id").getAsInt();
        int delay = object.get("delay").getAsInt();
        int speed = object.get("speed").getAsInt();
        int startHeight = object.get("start_height").getAsInt();
        int endHeight = object.get("end_height").getAsInt();
        int curve = object.get("curve").getAsInt();

        return (source, target) ->
                LocalProjectile.followEntity(source.getContext())
                        .setSourceEntity(source)
                        .setTargetEntity(target)
                        .setId(id)
                        .setTicksToEnd(delay)
                        .setTicksToStart(speed)
                        .setStartHeight(startHeight)
                        .setEndHeight(endHeight)
                        .setInitialSlope(curve)
                        .setView(ChunkUpdatableView.globalView())
                        .build();
    }

    /**
     * Parses model data and returns a builder function that creates a {@link PlayerModelAnimation}.
     * <p>
     * Expected fields:
     * <ul>
     *     <li>{@code standing} - Standing animation ID.</li>
     *     <li>{@code walking} - Walking animation ID.</li>
     *     <li>{@code running} - Running animation ID</li>
     * </ul>
     * <p>
     *
     * @param object The JSON object describing the model animation.
     * @return The parsed model data.
     */
    protected WeaponModelDefinition readModel(JsonObject object) {
        return new WeaponModelDefinition(
                object.get("standing").getAsInt(),
                object.get("walking").getAsInt(),
                object.get("running").getAsInt()
        );
    }
}
