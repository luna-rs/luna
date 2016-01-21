package io.luna.util.parser.impl;

import io.luna.util.parser.NewLineParser;

import java.util.HashSet;
import java.util.Set;

/**
 * A {@link NewLineParser} implementation that reads banned addresses.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class IpBanParser extends NewLineParser {

    /**
     * A {@link Set} containing the banned addresses.
     */
    public static final Set<String> BANNED_ADDRESSES = new HashSet<>();

    /**
     * Creates a new {@link IpBanParser}.
     */
    public IpBanParser() {
        super("./data/players/ip_banned.txt");
    }

    @Override
    public void readNextLine(String nextLine) throws Exception {
        BANNED_ADDRESSES.add(nextLine);
    }
}
