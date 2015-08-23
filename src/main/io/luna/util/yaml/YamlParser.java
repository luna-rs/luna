package io.luna.util.yaml;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

public abstract class YamlParser implements Runnable {

    // TODO: Documentation, unit tests.

    private final Logger logger = LogManager.getLogger(YamlParser.class);
    private final Yaml yaml = new Yaml();
    private final File file;

    public YamlParser(File file) {
        checkArgument(file.getName().endsWith(".yml"), "Invalid file extension.");
        this.file = file;
    }

    protected abstract void parse(Map<String, Object> document) throws Exception;

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        try (InputStream in = new FileInputStream(file)) {
            for (Object obj : yaml.loadAll(in)) {
                if (obj == null) {
                    throw new Exception("Malformed document.");
                }
                parse((Map<String, Object>) obj);
            }
        } catch (Exception e) {
            logger.catching(e);
            onException(e);
        }
    }

    public void onException(Exception e) {}
}
