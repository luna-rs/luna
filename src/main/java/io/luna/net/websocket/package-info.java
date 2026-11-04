/**
 * Native browser transport for the existing Luna login and game protocol.
 *
 * <h2>Architecture</h2>
 * One Luna process owns both listeners and the same world. Desktop clients continue using the
 * configured {@code game.port}, normally TCP 43594. The optional browser listener accepts binary
 * WebSocket connections on a different internal port, normally 43595. It unwraps and wraps the
 * existing ordered byte stream; it does not open another game TCP socket, route to other servers,
 * duplicate game logic, or require a Node relay. Cache files and the webclient remain ordinary
 * HTTP assets supplied by the website, not by this listener.
 * <p>
 * A WebSocket message is not a RuneScape packet. Packets may span multiple messages or share one
 * message. Netty handles the HTTP upgrade, masking, fragmented messages and control frames; the
 * adapter supplies ordered bytes to the existing progressive login/game decoders. RSA, ISAAC,
 * packet readers and the queued game-thread dispatch are unchanged. Network adapters run on their
 * channel's Netty event loop and never execute gameplay actions directly.
 * <p>
 * HTTP path/origin checks and numeric peer resolution precede upgrade. Only an upgraded channel
 * can acquire the shared admission lease and install the normal game pipeline. The names
 * {@code login-decoder} and {@code login-encoder} remain stable for the authenticated codec
 * transition. Abandoned handshakes, retained early input and closed connections release their
 * buffers and connection counts. Invalid settings or bind failures abort startup; a partially
 * opened desktop listener is closed when the optional browser port cannot bind.
 *
 * <h2>Configuration and local development</h2>
 * Configure {@code game.websocket} in {@code data/luna.jsonc}. It is disabled by default, and an
 * absent section preserves existing TCP-only installations. After setting {@code enabled} to
 * {@code true}, restart Luna to bind the listener. All property defaults, limits and units are
 * documented by {@link io.luna.net.websocket.WebSocketSettings} and in the example configuration.
 * <p>
 * For a local webclient served at {@code http://127.0.0.1:8095}, the defaults permit a connection
 * to {@code ws://127.0.0.1:43595/game}. Add the exact origin if the HTTP preview uses a different
 * port. Point the browser's configured WebSocket URL at that address; the shared Java client and
 * desktop endpoint need no protocol changes. The optional single {@code server=development} or
 * {@code server=beta} query is accepted for existing client compatibility. It is a label only:
 * this listener always connects to its own Luna world and cannot select an arbitrary destination.
 * <p>
 * Use JDK 21 and the project's Gradle wrapper to build, then run Luna from the directory containing
 * its existing {@code data} folder. Build artifacts do not replace player saves, caches, RSA keys
 * or other server data. Enabling the WebSocket listener does not change the desktop TCP port.
 *
 * <h2>Public websites and TLS</h2>
 * HTTPS websites require a secure {@code wss://} endpoint. This listener itself speaks plain
 * WebSocket; terminate TLS with a trusted HTTPS reverse proxy or tunnel and forward its upgraded
 * connection to the loopback listener. The public browser connection can use normal HTTPS port 443
 * while the proxy connects to internal port 43595. Do not expose that internal port just to make
 * it accessible to the website. A static host such as GitHub Pages supplies the client files;
 * the game connection terminates on the machine running Luna.
 * <p>
 * An example Pages deployment behind a local Cloudflare connector is:
 * <pre>{@code
 * "websocket": {
 *   "enabled": true,
 *   "bind_address": "127.0.0.1",
 *   "port": 43595,
 *   "path": "/game",
 *   "allowed_origins": ["https://luna-rs.github.io"],
 *   "trusted_proxy_addresses": ["127.0.0.1"],
 *   "forwarded_ip_header": "CF-Connecting-IP"
 * }
 * }</pre>
 * Origins contain scheme, hostname and optional port only: a Pages project path such as
 * {@code /luna-client/} is not part of the Origin header. Configure the connector's HTTP origin
 * as {@code http://127.0.0.1:43595}. Its public secure URL supplies the browser's
 * {@code wss://.../game} endpoint. Temporary tunnel hostnames can change after restart, requiring
 * a website configuration update. An origin allowlist restricts browser use; it is not account
 * authentication and does not replace Luna's normal login checks.
 *
 * <h2>Client identity through a proxy</h2>
 * Without a configured trusted peer, forwarded-address headers are ignored and the real socket
 * address is used. When a peer is explicitly trusted, exactly one numeric client address must be
 * supplied in {@code forwarded_ip_header}; missing, duplicate, malformed or comma-separated values
 * reject the upgrade. Trust exact proxy IPs only, and configure the proxy to overwrite that header
 * with its verified client address. A generic proxy can use a dedicated single-address header such
 * as {@code X-Real-IP} instead of the Cloudflare default.
 * <p>
 * The resolved address is attached before creating login clients and before admission, so both
 * account metadata and the shared ban/connection-limit policy see the same canonical identity.
 * This is essential behind a local proxy: the legacy TCP filter whitelists {@code 127.0.0.1};
 * leaving proxy trust unconfigured would otherwise make all proxied players look like that
 * whitelisted peer. Keep the listener on loopback when the trusted proxy is local. A trusted
 * proxy's address identifies who may assert client identity, not which players bypass admission.
 *
 * <h2>Limits, rollout and verification</h2>
 * Request bodies, incoming frames/fragmented messages, early input and pending game/wire output
 * are bounded. Text frames are rejected. A handshake deadline applies before the upgrade; the
 * existing game read timeout applies after it. WebSocket ping/pong is transport control and does
 * not replace normal game traffic. Pending-output overflow closes a slow connection rather than
 * allowing its queue to grow without limit.
 * <p>
 * Deploy the updated server code with its matching runtime dependencies, preserve the existing
 * data directory, enable the optional listener and configure the TLS endpoint. Restart the game
 * server, verify browser login/relogin and desktop TCP connectivity, then update the published
 * client's WebSocket URL. Verify the published connection before draining and retiring an older
 * relay. Disabling {@code game.websocket.enabled} and restarting removes the optional listener
 * while preserving the desktop endpoint. This source integration does not itself deploy a server,
 * update a website or provide certificate renewal/service supervision.
 *
 * @author lare96
 */
package io.luna.net.websocket;
