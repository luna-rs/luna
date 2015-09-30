package io.luna.util.yaml;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * An abstraction model containing functions to deserialize {@code YAML} files.
 * It implements {@link Callable} so the deserialize progress can be tracked
 * through {@link Future}, and implements {@link Runnable} so an action can be
 * executed when the deserialization process is complete.
 * 
 * @author lare96 <http://github.org/lare96>
 * @param <T> The type that documents from a {@code YAML} file will be
 *        deserialized into.
 */
public abstract class YamlDeserializer<T> implements Callable<List<T>>, Runnable {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(YamlDeserializer.class);

    /**
     * The absolute path to the {@code YAML} file.
     */
    private final Path path;

    /**
     * Creates a new {@link YamlDeserializer}.
     *
     * @param path The absolute path to the file.
     */
    public YamlDeserializer(String path) {
        checkArgument(path.endsWith(".yml"), "Invalid file extension.");
        this.path = Paths.get(path);
    }

    /**
     * Deserializes {@code yml} into {@code T}.
     * 
     * @param yml The document to deserialize.
     * @return The deserialized document, as {@code T}.
     */
    public abstract T deserialize(YamlDocument yml);

    /**
     * Invokes the {@code call()} function as an argument to
     * {@code onComplete(List<T>)}, potentially in an asynchronous context.
     */
    @Override
    public final void run() {
        try {
            onComplete(call());
        } catch (Exception e) {
            LOGGER.catching(Level.WARN, e);
        }
    }

    /**
     * Attempts to parse all of the documents in {@code path}, potentially in an
     * asynchronous context.
     * 
     * @return The list of deserialized {@link Object}s.
     * @throws Exception If unable to deserialize {@code Object}s for whatever
     *         reason.
     */
    @SuppressWarnings("unchecked")
    @Override
    public final List<T> call() throws Exception {
        List<T> list = new LinkedList<>();
        Yaml yaml = new Yaml();
        int index = 0;

        try (Reader in = Files.newBufferedReader(path)) {
            for (Object obj : yaml.loadAll(in)) {
                requireNonNull(obj, "Malformed document.");
                list.add(deserialize(YamlDocument.immutable((Map<String, Object>) obj)));
                index++;
            }
        } catch (Exception e) {
            LOGGER.warn("YAML document index " + index, e);
        }
        return list;
    }

    /**
     * Executed when the deserialization completes.
     * 
     * @param list The list of deserialized {@link Object}s.
     * @throws Exception If any errors occur during execution.
     */
    public void onComplete(List<T> list) throws Exception {

    }
}
