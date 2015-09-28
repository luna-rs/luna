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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * 
 * 
 * @author lare96 <http://github.org/lare96>
 */
public abstract class YamlDeserializer<T> implements Callable<List<T>> {

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

    public abstract T fromYaml(YamlDocument yml);

    public void onComplete(List<T> list) {

    }

    /**
     * Attempts to parse all of the documents in {@code path}, potentially on an
     * independent {@code Thread}.
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<T> call() throws Exception {
        List<T> list = new LinkedList<>();
        Yaml yaml = new Yaml();
        int curr = 0;

        try (Reader in = Files.newBufferedReader(path)) {
            for (Object obj : yaml.loadAll(in)) {
                requireNonNull(obj, "Malformed document.");
                list.add(fromYaml(YamlDocument.immutable((Map<String, Object>) obj)));
                curr++;
            }
        } catch (Exception e) {
            LOGGER.warn("An exception occured at YAML document index: " + curr, e);
        }
        return list;
    }
}
