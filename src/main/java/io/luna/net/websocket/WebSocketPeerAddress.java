package io.luna.net.websocket;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.NetUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Resolves numeric client addresses using one explicitly trusted proxy hop.
 *
 * @author lare96
 */
final class WebSocketPeerAddress {

    private WebSocketPeerAddress() {
    }

    /**
     * Resolves the socket peer or the configured header supplied by a trusted peer.
     *
     * @param channel The accepted socket.
     * @param request The upgrade request.
     * @param settings The listener settings.
     * @return The verified numeric client address.
     */
    static String resolve(Channel channel, FullHttpRequest request, WebSocketSettings settings) {
        if (!(channel.remoteAddress() instanceof InetSocketAddress peer) || peer.getAddress() == null) {
            throw new IllegalArgumentException("A numeric socket peer is required.");
        }
        String address = NetUtil.toAddressString(peer.getAddress());
        boolean trusted = settings.trustedProxyAddresses().stream()
                .anyMatch(value -> address.equals(normalize(value)));
        if (!trusted) {
            return address;
        }
        List<String> values = request.headers().getAll(settings.forwardedIpHeader());
        String forwarded = values.size() == 1 ? normalize(values.get(0)) : null;
        if (forwarded == null) {
            throw new IllegalArgumentException("The trusted proxy must provide one numeric client address.");
        }
        return forwarded;
    }

    /**
     * Normalizes an IP literal without making a DNS request.
     *
     * @param value The address literal.
     * @return The normalized address, or {@code null} if malformed.
     */
    static String normalize(String value) {
        if (value == null || value.indexOf('%') >= 0 || value.indexOf('[') >= 0
                || value.indexOf(']') >= 0 || !value.equals(value.trim())
                || !NetUtil.isValidIpV4Address(value) && !NetUtil.isValidIpV6Address(value)) {
            return null;
        }
        try {
            return NetUtil.toAddressString(InetAddress.getByAddress(NetUtil.createByteArrayFromIpAddressString(value)));
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
