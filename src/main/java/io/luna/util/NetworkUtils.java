package io.luna.util;


import io.netty.channel.Channel;

import java.net.InetSocketAddress;

/**
 * @author lare96 <http://github.com/lare96>
 */
public class NetworkUtils {

    public static String getIpAddress(Channel channel) {
        InetSocketAddress socketAddress = (InetSocketAddress) channel.remoteAddress();
        return socketAddress.getAddress().getHostAddress();
    }

    private NetworkUtils() {
    }
}