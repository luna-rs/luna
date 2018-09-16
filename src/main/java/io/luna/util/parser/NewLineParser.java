package io.luna.util.parser;

import java.io.BufferedReader;
import java.util.List;
import java.util.Scanner;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Parser} implementation designated for files that have tokens separated by a new line.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class NewLineParser extends Parser<Scanner, String> {

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
    public String doRead(Scanner reader) throws Exception {
        return reader.nextLine();
    }

    @Override
    public Scanner getReader(BufferedReader in) throws Exception {
        return new Scanner(in);
    }

    @Override
    public boolean canRead(Scanner objectReader) throws Exception {
        return objectReader.hasNextLine();
    }

    @Override
    public void onReadComplete(List<String> readObjects) throws Exception {
        for (String nextLine : readObjects) {
            if (nextLine.isEmpty()) {
                EmptyLinePolicy linePolicy = requireNonNull(emptyLinePolicy());
                if (linePolicy == EmptyLinePolicy.SKIP) {
                    continue;
                } else if (linePolicy == EmptyLinePolicy.EXCEPTION) {
                    throw new IllegalStateException("nextLine.isEmpty()");
                }
            }
            readNextLine(nextLine);
        }
    }

    /**
     * Reads the next line that was parsed by the {@link Scanner}.
     *
     * @param nextLine The line to read.
     */
    public abstract void readNextLine(String nextLine) throws Exception;

    /**
     * @return The {@link EmptyLinePolicy} that determines what happens when an empty line is encountered
     * while parsing various lines of data.
     */
    public EmptyLinePolicy emptyLinePolicy() {
        return EmptyLinePolicy.SKIP;
    }
}