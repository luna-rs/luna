package io.luna.util.parser.impl;

import io.luna.net.LunaChannelFilter;
import io.luna.net.LunaChannelInitializer;
import io.luna.util.parser.NewLineParser;

/**
 * A {@link NewLineParser} implementation that reads banned addresses.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class IpBanParser extends NewLineParser {

    /**
     * Creates a new {@link IpBanParser}.
     */
    public IpBanParser() {
        super("./data/players/ip_banned.txt");
    }

    @Override
    public void readNextLine(String nextLine) throws Exception {
        LunaChannelFilter channelFilter = (LunaChannelFilter) LunaChannelInitializer.CHANNEL_FILTER;
        channelFilter.getBannedAddresses().add(nextLine);
    }
}
