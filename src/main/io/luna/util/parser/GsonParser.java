package io.luna.util.parser;

import com.google.gson.JsonArray;
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
     * @param path The path to the file being parsed.
     */
    public GsonParser(String path) {
        super(path);
    }

    @Override
    public final T doRead(JsonArray reader) throws Exception {
        return readObject((JsonObject) reader.get(currentIndex));
    }

    @Override
    public final JsonArray getReader(BufferedReader in) throws Exception {
        return (JsonArray) new JsonParser().parse(in);
    }

    @Override
    public final boolean canRead(JsonArray objectReader) throws Exception {
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
