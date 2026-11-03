package io.luna.net.websocket;

import java.net.URI;
import java.util.List;

/**
 * Configures the optional browser listener independently of the desktop TCP transport.
 *
 * @author lare96
 */
public final class WebSocketSettings {

    private boolean enabled;
    private String bindAddress = "127.0.0.1";
    private int port = 43595;
    private String path = "/game";
    private List<String> allowedOrigins = List.of("http://127.0.0.1:8095", "http://localhost:8095");
    private List<String> trustedProxyAddresses = List.of();
    private String forwardedIpHeader = "CF-Connecting-IP";
    private int maxHttpContentLength = 8192;
    private int maxFramePayloadLength = 1048576;
    private long maxPendingOutboundBytes = 8388608;
    private long handshakeTimeoutMillis = 10000;

    /**
     * Validates an enabled listener before either network port is bound.
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (!literalAddress(bindAddress) || port < 1 || port > 65535) {
            throw new IllegalArgumentException("WebSocket bind_address must be an IP literal with a valid port.");
        }
        if (path == null || !path.startsWith("/") || path.contains("?") || path.contains("#")
                || !URI.create(path).getRawPath().equals(path)) {
            throw new IllegalArgumentException("WebSocket path must be an absolute URL path without a query.");
        }
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("WebSocket allowed_origins must contain explicit website origins.");
        }
        for (String origin : allowedOrigins) {
            if (origin == null) {
                throw new IllegalArgumentException("WebSocket allowed_origins cannot contain null entries.");
            }
            URI uri = URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || !uri.getRawPath().isEmpty()
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("WebSocket origins require http(s), a host, and no URL path.");
            }
        }
        if (trustedProxyAddresses == null || trustedProxyAddresses.stream().anyMatch(it -> !literalAddress(it))) {
            throw new IllegalArgumentException("WebSocket trusted_proxy_addresses must contain exact IP literals.");
        }
        if (forwardedIpHeader == null || !forwardedIpHeader.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("WebSocket forwarded_ip_header must be a single HTTP header name.");
        }
        if (maxHttpContentLength < 1 || maxHttpContentLength > 65536
                || maxFramePayloadLength < 1 || maxFramePayloadLength > 1048576
                || maxPendingOutboundBytes < maxFramePayloadLength || maxPendingOutboundBytes > 67108864
                || handshakeTimeoutMillis < 1000 || handshakeTimeoutMillis > 60000) {
            throw new IllegalArgumentException("WebSocket request, frame, queue, or handshake limits are invalid.");
        }
    }

    private static boolean literalAddress(String address) {
        return WebSocketPeerAddress.normalize(address) != null;
    }

    public boolean enabled() { return enabled; }
    public String bindAddress() { return bindAddress; }
    public int port() { return port; }
    public String path() { return path; }
    public List<String> allowedOrigins() { return List.copyOf(allowedOrigins); }
    public List<String> trustedProxyAddresses() { return List.copyOf(trustedProxyAddresses); }
    public String forwardedIpHeader() { return forwardedIpHeader; }
    public int maxHttpContentLength() { return maxHttpContentLength; }
    public int maxFramePayloadLength() { return maxFramePayloadLength; }
    public long maxPendingOutboundBytes() { return maxPendingOutboundBytes; }
    public long handshakeTimeoutMillis() { return handshakeTimeoutMillis; }
}
