package io.luna.util.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;

/**
 * A {@link Parser} implementation specifically designated for {@code JSON} files.
 *
 * @param <T> The type of {@code Object} being parsed.
 * @author lare96 <http://github.org/lare96>
 */
public abstract class GsonParser<T> extends Parser<JsonArray, T> {

    /**
     * The current parsing index.
     */
    private int currentIndex = -1;

    /**
     * Creates a new {@link GsonParser}.
     *
     * @param paths The paths to the files being parsed.
     */
    public GsonParser(String... paths) {
        super(paths);
    }

    @Override
    public T doRead(JsonArray reader) throws Exception {
        JsonElement current = reader.get(currentIndex);
        return readObject(current.getAsJsonObject());
    }

    @Override
    public JsonArray getReader(BufferedReader in) throws Exception {
        JsonElement jsonRead = new JsonParser().parse(in);
        return jsonRead.getAsJsonArray();
    }

    @Override
    public boolean canRead(JsonArray objectReader) throws Exception {
        if (currentIndex + 1 == objectReader.size()) {
            return false;
        }
        currentIndex++;
        return true;
    }

    /**
     * Read the data from the parsed file.
     *
     * @param reader Where the data will be read from.
     * @return The newly created {@code Object} from the read data.
     * @throws Exception If any errors occur while reading.
     */
    public abstract T readObject(JsonObject reader) throws Exception;
}
