package io.luna.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A static utility class that provides helper functions for working with the Google GSON serialization library.
 * <p>
 * This class defines a preconfigured {@link Gson} instance that uses pretty-printing, disables HTML escaping, and
 * follows a {@link FieldNamingPolicy#LOWER_CASE_WITH_UNDERSCORES} naming convention. It also exposes simplified
 * convenience methods for reading and writing JSON to and from files, objects, and raw {@link JsonElement} instances.
 * </p>
 *
 * <p>
 * Typical usage:
 * <pre>
 *     // Read a JSON file into an object
 *     PlayerData data = GsonUtils.readAsType(path, PlayerData.class);
 *
 *     // Convert an object to JSON and write to file
 *     JsonElement json = GsonUtils.toJsonTree(data);
 *     GsonUtils.writeJson(json, outputPath);
 * </pre>
 * </p>
 *
 * @author lare96
 */
public final class GsonUtils {

    /**
     * A shared {@link Gson} instance configured for general-purpose use throughout the Luna engine.
     * <ul>
     *     <li>HTML escaping is disabled.</li>
     *     <li>Inner class serialization is disabled.</li>
     *     <li>Output is pretty-printed for readability.</li>
     *     <li>Field names use {@code lower_case_with_underscores} formatting.</li>
     * </ul>
     */
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .disableInnerClassSerialization()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    /**
     * Converts a {@link JsonElement} into an object of the specified type.
     * <p>
     * This is a shorthand for {@link Gson#fromJson(JsonElement, Class)} using the
     * shared {@link #GSON} instance.
     * </p>
     *
     * @param element The JSON element to deserialize.
     * @param clazz   The target class type.
     * @param <T>     The generic type of the desired object.
     * @return A new instance of {@code T} representing the JSON data.
     */
    public static <T> T getAsType(JsonElement element, Class<T> clazz) {
        return GSON.fromJson(element, clazz);
    }

    /**
     * Converts an object into a {@link JsonElement} representation.
     * <p>
     * This is a shorthand for {@link Gson#toJsonTree(Object)} using the
     * shared {@link #GSON} instance.
     * </p>
     *
     * @param obj The object to convert.
     * @return A {@link JsonElement} representing the object.
     */
    public static JsonElement toJsonTree(Object obj) {
        return GSON.toJsonTree(obj);
    }

    /**
     * Writes a {@link Object} to a file at the specified path.
     * <p>
     * The file will be created or overwritten, and the output will be formatted using
     * the shared {@link #GSON} instance.
     * </p>
     *
     * @param element The object to write.
     * @param path    The destination file path.
     * @throws IOException If an I/O error occurs during writing.
     */
    public static void writeJson(Object element, Path path) throws IOException {
        try (FileWriter fw = new FileWriter(path.toFile(), StandardCharsets.UTF_8)) {
            fw.append(GSON.toJson(element));
        }
    }

    /**
     * Reads a JSON file from the given {@link Path} and deserializes it into an object of the specified type.
     * <p>
     * This is a shorthand for {@link Gson#fromJson(Reader, Type)} using a buffered reader.
     * </p>
     *
     * @param path  The path to the JSON file.
     * @param clazz The class type to deserialize into.
     * @param <T>   The generic type of the desired object.
     * @return A new instance of {@code T} representing the file’s contents.
     * @throws IOException If the file cannot be read or parsed.
     */
    public static <T> T readAsType(Path path, Class<T> clazz) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            return GSON.fromJson(br, clazz);
        }
    }

    /**
     * Private constructor to prevent instantiation.
     * <p>
     * This class is intended for static use only.
     * </p>
     */
    private GsonUtils() {
    }
}
