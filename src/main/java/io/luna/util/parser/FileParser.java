package io.luna.util.parser;

import com.google.common.collect.ImmutableList;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * An abstraction model that provides functions for parsing of various types of files. All file parser implementations
 * should be safe to use across multiple threads.
 *
 * @param <P> The token parser type.
 * @param <T> The un-parsed token type.
 * @param <R> The parsed token type.
 * @author lare96
 */
public abstract class FileParser<P, T, R> implements Runnable {

    /**
     * The immutable list of files to parse.
     */
    private final Path filePath;

    /**
     * The current parsing index.
     */
    int currentIndex;

    /**
     * Creates a new {@link FileParser}.
     *
     * @param filePath The file to parse.
     */
    public FileParser(Path filePath) {
        this.filePath = filePath;
    }

    @Override
    public final void run() {
       parseFile(filePath);
    }

    /**
     * Parses a single token.
     *
     * @param parser The token parser.
     * @return The parsed token.
     * @throws Exception If any errors occur while parsing the token.
     */
    public abstract T parse(P parser);

    /**
     * Converts a single token into a token Object, or {@code null} if this token should be silently
     * discarded. If discarded, the current index counter will not be incremented.
     *
     * @param token The token to convert.
     * @return The token Object.
     */
    public abstract R convert(T token);

    /**
     * Determines if the parser can parse another token.
     *
     * @param parser The token parser.
     * @return {@code true} if another token can be parsed.
     * @throws Exception If any errors occur while determining if a token can be parsed.
     */
    public abstract boolean hasNext(P parser);

    /**
     * Creates and returns a new parser that will parse tokens contained within {@code in}.
     *
     * @param reader The reader of the file.
     * @return The new file parser.
     * @throws Exception If any errors occur while creating the parser.
     */
    public abstract P newParser(BufferedReader reader);

    /**
     * A function called when all tokens have been parsed.
     *
     * @param tokenObjects An immutable list of all token objects.
     * @throws Exception If any errors occur while notifying this listener.
     */
    public void onCompleted(ImmutableList<R> tokenObjects) {

    }

    /**
     * Parses {@code file} and notifies {@link #onCompleted(ImmutableList)} when finished.
     *
     * @param file The file to parse.
     */
    private void parseFile(Path file) {
        try (BufferedReader buf = Files.newBufferedReader(file)) {
            List<R> tokenObjects = new ArrayList<>();
            P parser = newParser(buf);

            while (hasNext(parser)) {
                R tokenObj = convert(parse(parser));
                if (tokenObj != null) {
                    tokenObjects.add(tokenObj);
                    currentIndex++;
                }
            }
            onCompleted(ImmutableList.copyOf(tokenObjects));
        } catch (Exception e) {
            throw new RuntimeException("Error while reading file [" + file + "]", e);
        }
    }

    /**
     * @return The immutable list of files to parse.
     */
    public Path getFilePath() {
        return filePath;
    }
}
