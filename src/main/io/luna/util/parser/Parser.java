package io.luna.util.parser;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The base class for all parsers. Provides functionality that allows subclasses to perform asynchronous parsing of various
 * types of files. Also provides a completion handler with a {@link List} of the successfully parsed {@code Objects}.
 *
 * @param <T1> The reader that will be parsing the file.
 * @param <T2> The {@code Object} being parsed.
 * @author lare96 <http://github.org/lare96>
 */
public abstract class Parser<T1, T2> implements Runnable {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(Parser.class);

    /**
     * The path to the file being parsed.
     */
    private final Path path;

    /**
     * Creates a new {@link Parser}.
     *
     * @param path The path to the file being parsed.
     */
    public Parser(String path) {
        this.path = Paths.get(path);
    }

    @Override
    public final void run() {
        try (BufferedReader in = Files.newBufferedReader(path)) {
            List<T2> readObjects = new ArrayList<>();
            T1 reader = getReader(in);

            while (canRead(reader)) {
                readObjects.add(doRead(reader));
            }
            onReadComplete(readObjects);
        } catch (Exception e) {
            LOGGER.catching(Level.FATAL, e);
        }
    }

    public abstract T2 doRead(T1 reader) throws Exception;

    public abstract T1 getReader(BufferedReader in) throws Exception;

    public abstract boolean canRead(T1 objectReader) throws Exception;

    public abstract void onReadComplete(List<T2> readObjects) throws Exception;
}
