package io.luna.util.parser;

import java.io.BufferedReader;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * A {@link FileParser} implementation designated for files that have tokens separated by a new line.
 *
 * @author lare96
 */
public abstract class NewLineFileParser extends FileParser<Scanner, String, String> {

    /**
     * Creates a new {@link NewLineFileParser}.
     *
     * @param filePath The file to parse.
     */
    public NewLineFileParser(Path filePath) {
        super(filePath);
    }

    /**
     * An enumerated type whose elements represent empty line policies.
     */
    public enum EmptyLinePolicy {

        /**
         * Ignore that the line is empty and treat it as if it was a normal line, reading it and forwarding it
         * to the implementing class.
         */
        READ,

        /**
         * Throw an {@link IllegalStateException} stating that the line was empty.
         */
        EXCEPTION,

        /**
         * Skip the line completely without any warning or thrown exception.
         */
        SKIP
    }

    @Override
    public String parse(Scanner parser) {
        return parser.nextLine();
    }

    @Override
    public Scanner newParser(BufferedReader reader) {
        return new Scanner(reader);
    }

    @Override
    public boolean hasNext(Scanner parser) {
        return parser.hasNextLine();
    }

    @Override
    public String convert(String token) {
        if (token.isEmpty()) {
            EmptyLinePolicy linePolicy = emptyLinePolicy();
            if (linePolicy == EmptyLinePolicy.SKIP) {
                return null;
            }
            if (linePolicy == EmptyLinePolicy.EXCEPTION) {
                throw new IllegalStateException("[@ index: " +
                        currentIndex + "] Parser does not allow empty lines!");
            }
        }
        return token;
    }

    /**
     * @return The {@link EmptyLinePolicy} that determines what happens when an empty line is encountered
     * while parsing various lines of data.
     */
    public EmptyLinePolicy emptyLinePolicy() {
        return EmptyLinePolicy.SKIP;
    }
}
