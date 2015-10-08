package io.luna.util.yaml;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Queue;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * An abstraction model containing functions to serialize {@code YAML} files. It
 * implements {@link Runnable} so serialization for {@link YamlDocument}s can be
 * done asynchronously.
 * 
 * @author lare96 <http://github.org/lare96>
 * @param <T> The type that will be serialized into a {@code YAML} file.
 */
public abstract class YamlSerializer<T> implements Runnable {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(YamlDeserializer.class);

    /**
     * A queue containing all of the {@link Object}s that will be serialized.
     */
    private final Queue<T> serialize = new ArrayDeque<>();

    /**
     * The absolute path to the {@code YAML} file.
     */
    private final Path path;

    /**
     * Creates a new {@link YamlSerializer}.
     *
     * @param path The absolute path to the file.
     * @param serialize The sequence of {@link Object}s that will be serialized.
     */
    public YamlSerializer(String path, Iterable<T> serialize) {
        checkArgument(path.endsWith(".yml"), "Invalid file extension.");
        this.path = Paths.get(path);
        Iterables.addAll(this.serialize, serialize);
    }

    /**
     * Creates a new {@link YamlSerializer}.
     *
     * @param path The absolute path to the file.
     * @param serialize The single {@link Object} that will be serialized.
     */
    public YamlSerializer(String path, T serialize) {
        this(path, ImmutableList.of(serialize));
    }

    /**
     * Serializes {@code obj} into {@link YamlDocument}.
     * 
     * @param yml The {@link Yaml} instance being used to serialize.
     * @param obj The {@link Object} to serialize.
     * @return The serialized {@code obj}, represented as {@code YamlDocument}.
     * @throws Exception If any {@link Exception}s are thrown during
     *         serialization.
     */
    public abstract YamlDocument serialize(Yaml yml, T obj) throws Exception;

    @Override
    public final void run() {
        try {
            Yaml yaml = new Yaml();
            YamlFile file = new YamlFile();
            for (;;) {
                T next = serialize.poll();
                if (next == null) {
                    break;
                }
                file.add(serialize(yaml, next));
            }
            yaml.dumpAll(file.toSerializableList().iterator(), new FileWriter(path.toFile()));
        } catch (Exception e) {
            LOGGER.catching(Level.WARN, e);
        }
    }
}
