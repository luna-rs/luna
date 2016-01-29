package io.luna.util.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;

import java.io.BufferedReader;
import java.util.List;

/**
 * A {@link Parser} implementation specifically designated for {@code TOML} files.
 *
 * @param <T> The type of {@code Object} being parsed.
 * @author lare96 <http://github.org/lare96>
 */
public abstract class TomlParser<T> extends GsonParser<T> {

    /**
     * The name of the table array to retrieve.
     */
    private final String tableArrayName;

    /**
     * Creates a new {@link TomlParser}.
     *
     * @param path The path to the file being parsed.
     */
    public TomlParser(String path, String tableArrayName) {
        super(path);
        this.tableArrayName = tableArrayName;
    }

    @Override
    public JsonArray getReader(BufferedReader in) throws Exception {
        List<Toml> tables = new Toml().read(in).getTables(tableArrayName);
        JsonArray array = new JsonArray();

        tables.stream().map(it -> it.to(JsonObject.class)).forEach(array::add);

        return array;
    }
}
