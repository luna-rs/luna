package io.luna.util.yaml;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * An abstract class that serves the purpose of providing functionality to parse
 * {@code YAML} files.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public abstract class YamlParser implements Runnable {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(YamlParser.class);

    /**
     * The {@code YAML} serializer.
     */
    private final Yaml yaml = new Yaml();

    /**
     * The absolute path to the {@code YAML} file.
     */
    private final Path path;

    /**
     * Creates a new {@link io.luna.util.yaml.YamlParser}.
     *
     * @param path The absolute path to the file.
     */
    public YamlParser(Path path) {
        checkArgument(path.getFileName().endsWith(".yml"), "Invalid file extension.");
        this.path = path;
    }

    /**
     * An abstract method that allows the implementing class to determine how to
     * parse each individual {@code YAML} document. A single {@code YAML}
     * document is represented in Java as a map of {@code String}s to
     * {@code Object}s.
     * 
     * @param yml The current document being parsed.
     * @throws Exception If any errors occur while parsing the document.
     */
    protected abstract void parse(YamlDocument yml) throws Exception;

    /**
     * Attempts to parse all of the documents in {@code path}, potentially on an
     * independent {@code Thread}. All exceptions are caught here by the logger.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        try (Reader in = Files.newBufferedReader(path)) {
            for (Object obj : yaml.loadAll(in)) {
                checkState(obj != null, "Malformed document.");
                parse(new YamlDocument((Map<String, Object>) obj));
            }
        } catch (Exception e) {
            LOGGER.catching(e);
        }
    }
}
