package io.luna.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * A static-utility class that contains functions for manipulating {@code Object}s related to {@link Gson}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GsonUtils {

    /**
     * A general purpose {@link Gson} instance that has no registered type adapters.
     */
    public static final Gson GSON = new GsonBuilder().disableInnerClassSerialization().setPrettyPrinting()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    /**
     * Shortcut to function {@link Gson#fromJson(JsonElement, Class)}.
     */
    public static <T> T getAsType(JsonElement element, Class<T> clazz) {
        return GSON.fromJson(element, clazz);
    }

    /**
     * Shortcut to function {@link Gson#toJsonTree(Object)}.
     */
    public static JsonElement toJsonTree(Object obj) {
        return GSON.toJsonTree(obj);
    }

    /**
     * Shortcut to function {@link Gson#toJson(JsonElement, Appendable)}.
     */
    public static void writeJson(JsonElement element, File file) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.append(GSON.toJson(element));
        }
    }
}
