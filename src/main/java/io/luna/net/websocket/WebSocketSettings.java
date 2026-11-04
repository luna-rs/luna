package io.luna.net.websocket;

import java.net.URI;
import java.util.List;

/**
 * Configures the optional browser listener independently of the desktop TCP transport.
 * <p>
 * The JSON loader maps these fields to {@code game.websocket} using lower-case names with underscores.
 * Missing fields retain the defaults below; an absent section is also disabled. Configuration changes
 * take effect on server restart. This listener speaks plain WebSocket, so public HTTPS sites require
 * a TLS-terminating proxy or tunnel in front of it.
 * <p>
 * Only numeric bind and trusted-proxy addresses are accepted. The listener validates its settings
 * before either game port is bound; it never resolves configuration or forwarded addresses through DNS.
 *
 * @author lare96
 */
public final class WebSocketSettings {

    /**
     * Whether to bind the browser listener. Disabled by default to preserve existing installations.
     */
    private boolean enabled;

    /**
     * Numeric local interface to bind. Loopback keeps the listener reachable only by a local TLS proxy.
     */
    private String bindAddress = "127.0.0.1";

    /**
     * Internal WebSocket TCP port, distinct from the desktop game port.
     */
    private int port = 43595;

    /**
     * Exact upgrade path. The optional legacy browser {@code server} query is validated separately.
     */
    private String path = "/game";

    /**
     * Exact browser origins, including scheme and optional port, without a trailing slash or URL path.
     */
    private List<String> allowedOrigins = List.of("http://127.0.0.1:8095", "http://localhost:8095");

    /**
     * Exact socket peer addresses allowed to supply the forwarded client-address header. Empty trusts none.
     */
    private List<String> trustedProxyAddresses = List.of();

    /**
     * Single-valued numeric address header supplied by a trusted proxy. Comma-separated chains are rejected.
     */
    private String forwardedIpHeader = "CF-Connecting-IP";

    /**
     * Maximum aggregated HTTP request body size in bytes, from 1 through 65,536.
     * Valid upgrade requests must still have an empty body.
     */
    private int maxHttpContentLength = 8192;

    /**
     * Maximum incoming frame and aggregated fragmented-message size in bytes, from 1 through 1,048,576.
     * Also bounds bytes retained during the upgrade/admission handoff.
     */
    private int maxFramePayloadLength = 1048576;

    /**
     * Maximum pending game and encoded wire output in bytes. At least one maximum-sized incoming frame
     * must fit; the configured ceiling may not exceed 67,108,864 bytes.
     */
    private long maxPendingOutboundBytes = 8388608;

    /**
     * Deadline in milliseconds for HTTP/WebSocket upgrade completion, from 1,000 through 60,000.
     * The shared five-second game read timeout is installed only after the WebSocket upgrade.
     */
    private long handshakeTimeoutMillis = 10000;

    /**
     * Creates disabled defaults for an absent configuration section or JSON fields not explicitly set.
     */
    public WebSocketSettings() {
    }

    /**
     * Validates an enabled listener before either network port is bound.
     *
     * @throws IllegalArgumentException If enabled settings cannot describe a bounded, explicit listener.
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

    /**
     * Checks an unscoped, unbracketed IP literal without performing a DNS lookup.
     *
     * @param address The configured address.
     * @return Whether the transport can normalize the address.
     */
    private static boolean literalAddress(String address) {
        return WebSocketPeerAddress.normalize(address) != null;
    }

    /**
     * Returns whether startup should open the optional browser listener.
     *
     * @return Whether to bind the optional browser listener on startup.
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the local network interface used by the browser transport.
     *
     * @return The numeric local interface used by the WebSocket listener.
     */
    public String bindAddress() {
        return bindAddress;
    }

    /**
     * Returns the browser transport's internal listening port.
     *
     * @return The internal WebSocket port, independent of the public TLS port and desktop port.
     */
    public int port() {
        return port;
    }

    /**
     * Returns the HTTP path accepted for a WebSocket upgrade.
     *
     * @return The exact permitted HTTP upgrade path.
     */
    public String path() {
        return path;
    }

    /**
     * Returns the explicit website allowlist without exposing mutable configuration storage.
     *
     * @return An immutable copy of the exact permitted browser origins.
     */
    public List<String> allowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    /**
     * Returns the peers whose single-address identity header may be trusted.
     *
     * @return An immutable copy of the exact proxy peer addresses allowed to forward client identity.
     */
    public List<String> trustedProxyAddresses() {
        return List.copyOf(trustedProxyAddresses);
    }

    /**
     * Returns the address header required from explicitly trusted peers.
     *
     * @return The verified single-address header required from configured trusted proxies.
     */
    public String forwardedIpHeader() {
        return forwardedIpHeader;
    }

    /**
     * Returns the HTTP request aggregation bound.
     *
     * @return The maximum aggregated HTTP request body length in bytes.
     */
    public int maxHttpContentLength() {
        return maxHttpContentLength;
    }

    /**
     * Returns the incoming WebSocket message bound.
     *
     * @return The incoming frame and fragmented-message limit in bytes.
     */
    public int maxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    /**
     * Returns the limit used to disconnect connections with excessive pending output.
     *
     * @return The pending output byte limit enforced separately before and after wire encoding.
     */
    public long maxPendingOutboundBytes() {
        return maxPendingOutboundBytes;
    }

    /**
     * Returns the pre-game upgrade deadline.
     *
     * @return The HTTP/WebSocket handshake deadline in milliseconds.
     */
    public long handshakeTimeoutMillis() {
        return handshakeTimeoutMillis;
    }
}
