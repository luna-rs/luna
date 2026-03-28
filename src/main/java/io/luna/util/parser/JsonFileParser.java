package io.luna.util.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.LocalProjectile.TargetBuilder;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Graphic;
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
     * Parses projectile data and returns a builder function that creates a {@link LocalProjectile} for a given source
     * and target pair.
     * <p>
     * Expected fields:
     * <ul>
     *     <li>{@code id} - projectile graphic id</li>
     *     <li>{@code delay} - value passed to {@link TargetBuilder#setTicksToEnd(int)}</li>
     *     <li>{@code speed} - value passed to {@link TargetBuilder#setTicksToStart(int)}</li>
     *     <li>{@code start_height} - launch height</li>
     *     <li>{@code end_height} - impact height</li>
     *     <li>{@code curve} - initial slope</li>
     * </ul>
     * <p>
     * The produced projectile always uses {@link ChunkUpdatableView#globalView()}.
     *
     * @param object The JSON object describing projectile behavior.
     * @return A function that builds a projectile for the provided source and target mobs.
     */
    protected BiFunction<Mob, Mob, LocalProjectile> readProjectile(JsonObject object) {
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
                        .setTicksToStart(delay)
                        .setTicksToEnd(speed)
                        .setStartHeight(startHeight)
                        .setEndHeight(endHeight)
                        .setInitialSlope(curve)
                        .setView(ChunkUpdatableView.globalView())
                        .build();
    }
}
