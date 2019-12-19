package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import io.luna.net.LunaChannelFilter;
import io.luna.util.parser.AbstractNewLineFileParser;

/**
 * A {@link AbstractNewLineFileParser} implementation that parses blacklisted addresses.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class BlacklistFileParser extends AbstractNewLineFileParser {

    /**
     * A channel handler that will filter channels.
     */
    private LunaChannelFilter channelFilter;

    /**
     * Creates a new {@link BlacklistFileParser}.
     *
     * @param channelFilter A channel handler that will filter channels.
     */
    public BlacklistFileParser(LunaChannelFilter channelFilter) {
        super("./data/punishment/blacklist.txt");
        this.channelFilter = channelFilter;
    }

    @Override
    public void onCompleted(ImmutableList<String> tokenObjects) throws Exception {
        tokenObjects.stream().filter(InetAddresses::isInetAddress).forEach(channelFilter::addToBlacklist);
    }
}
