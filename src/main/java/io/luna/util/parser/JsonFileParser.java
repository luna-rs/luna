package io.luna.util.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;

/**
 * A {@link AbstractFileParser} implementation designed to parse {@code JSON} tokens.
 *
 * @param <R> The token object type.
 * @author lare96 <http://github.org/lare96>
 */
public abstract class JsonFileParser<R> extends AbstractFileParser<JsonArray, JsonObject, R> {

    /**
     * Creates a new {@link JsonFileParser}.
     *
     * @param files The files to parse.
     */
    public JsonFileParser(String... files) {
        super(files);
    }

    @Override
    public JsonObject parse(JsonArray parser) {
        JsonElement element = parser.get(currentIndex);
        return element.getAsJsonObject();
    }

    @Override
    public JsonArray newParser(BufferedReader reader) {
        JsonElement jsonReader = JsonParser.parseReader(reader);
        return jsonReader.getAsJsonArray();
    }

    @Override
    public boolean hasNext(JsonArray parser) {
        return currentIndex + 1 != parser.size();
    }
}
