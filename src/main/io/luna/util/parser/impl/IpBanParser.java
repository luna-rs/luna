package io.luna.util.parser.impl;

import io.luna.util.parser.NewLineStringParser;

import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * A {@link NewLineStringParser} implementation that reads banned addresses.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class IpBanParser extends NewLineStringParser {

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
    public String doRead(Scanner reader) throws Exception {
        String bannedAddress = reader.nextLine();
        if (bannedAddress.isEmpty()) {
            throw new Exception("empty line");
        }
        return bannedAddress;
    }

    @Override
    public void onReadComplete(List<String> readObjects) throws Exception {
        BANNED_ADDRESSES.addAll(readObjects);
    }
}
