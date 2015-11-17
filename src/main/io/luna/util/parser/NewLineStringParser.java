package io.luna.util.parser;

import java.io.BufferedReader;
import java.util.Scanner;

/**
 * A {@link Parser} implementation specifically designated for {@code .txt} files that have a series of {@code Object}s
 * separated by a new line.
 *
 * @param <T> The type of {@code Object} being parsed.
 * @author lare96 <http://github.org/lare96>
 */
public abstract class NewLineStringParser extends Parser<Scanner, String> {

    /**
     * Creates a new {@link NewLineStringParser}.
     *
     * @param path The path to the file being parsed.
     */
    public NewLineStringParser(String path) {
        super(path);
    }

    @Override
    public Scanner getReader(BufferedReader in) throws Exception {
        return new Scanner(in);
    }

    @Override
    public boolean canRead(Scanner objectReader) throws Exception {
        return objectReader.hasNextLine();
    }
}
