package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import io.luna.net.LunaChannelFilter;
import io.luna.util.parser.NewLineParser;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A {@link NewLineParser} implementation that parses blacklisted addresses.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class BlacklistParser extends NewLineParser {

    /**
     * A channel handler that will filter channels.
     */
    private LunaChannelFilter channelFilter;

    /**
     * Creates a new {@link BlacklistParser}.
     */
    public BlacklistParser(LunaChannelFilter channelFilter) {
        this.channelFilter = channelFilter;
    }

    @Override
    public void readNextLine(String nextLine) throws Exception {
        checkArgument(InetAddresses.isInetAddress(nextLine), "Invalid IP address [" + nextLine + "]");
        channelFilter.getBlacklist().add(nextLine);
    }

    @Override
    public ImmutableList<String> forFiles() {
        return ImmutableList.of("./data/punishment/blacklist.txt");
    }
}