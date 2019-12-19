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
public abstract class AbstractJsonFileParser<R> extends AbstractFileParser<JsonArray, JsonObject, R> {

    /**
     * Creates a new {@link AbstractJsonFileParser}.
     *
     * @param files The files to parse.
     */
    public AbstractJsonFileParser(String... files) {
        super(files);
    }

    @Override
    public JsonObject parse(JsonArray parser) throws Exception {
        JsonElement element = parser.get(currentIndex);
        return element.getAsJsonObject();
    }

    @Override
    public JsonArray newParser(BufferedReader reader) throws Exception {
        JsonElement jsonReader = new JsonParser().parse(reader);
        return jsonReader.getAsJsonArray();
    }

    @Override
    public boolean hasNext(JsonArray parser) throws Exception {
        return currentIndex + 1 != parser.size();
    }
}
