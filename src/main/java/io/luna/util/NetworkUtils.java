package io.luna.util;


import io.luna.game.model.mob.bot.BotChannel;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;

/**
 * A static-utility class that contains functions for networking.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class NetworkUtils {

    /**
     * Retrieves the IP address for {@code channel}.
     *
     * @param channel The channel.
     * @return The IP address of the channel.
     */
    public static String getIpAddress(Channel channel) {
        if(channel == BotChannel.CHANNEL) {
            return "bot-client";
        }
        try {
            InetSocketAddress socketAddress = (InetSocketAddress) channel.remoteAddress();
            return socketAddress.getAddress().getHostAddress();
        } catch (NullPointerException e) {
            return "localhost";
        }
    }

    /**
     * A private constructor to discourage external instantiation.
     */
    private NetworkUtils() {
    }
}