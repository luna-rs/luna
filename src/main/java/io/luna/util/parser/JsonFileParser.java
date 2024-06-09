package io.luna.util.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.util.GsonUtils;

import java.io.BufferedReader;
import java.nio.file.Path;

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
     * @param files The files to parse.
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
        return currentIndex + 1 != parser.size();
    }
}
