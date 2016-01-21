package io.luna.util.parser;

import com.google.common.collect.Range;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link NewLineParser} implementation that will parse a set of tokens on each line of a file.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class NewLineSplitParser extends NewLineParser {

    /**
     * Creates a new {@link NewLineSplitParser}.
     *
     * @param path The path to the file being parsed.
     */
    public NewLineSplitParser(String path) {
        super(path);
    }

    @Override
    public final void readNextLine(String nextLine) throws Exception {
        String[] tokens = nextLine.split(regex());

        checkState(tokenLength().contains(tokens.length), "invalid token array length");

        readTokens(tokens);
    }

    @Override
    public final EmptyLinePolicy emptyLinePolicy() {
        return EmptyLinePolicy.SKIP;
    }

    /**
     * Read the parsed tokens, split up into an array.
     *
     * @param tokens The tokens to read.
     * @throws Exception If any errors occur while reading the tokens.
     */
    public abstract void readTokens(String[] tokens) throws Exception;

    /**
     * @return The length range of the token array.
     */
    public abstract Range<Integer> tokenLength();

    /**
     * @return The regex that determines how the tokens will be split up.
     */
    public abstract String regex();
}
