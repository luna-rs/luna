package io.luna.net.client;

import com.google.gson.JsonParser;
import io.luna.LunaContext;
import io.luna.game.LogoutService;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.persistence.PlayerData;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.IsaacCipher;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.game.GameMessageDecoder;
import io.luna.net.codec.game.GameMessageEncoder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.net.msg.GameMessageRepository;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.login.LoginDecoder;
import io.luna.net.msg.login.LoginEncoder;
import io.luna.net.msg.login.LoginRequestMessage;
import io.luna.net.msg.login.LoginResponse;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises the login response boundary with real Netty codecs and encrypted game input.
 *
 * @author lare96
 */
final class LoginTransitionTest {

    private static final long CLIENT_HALF = 0x1020304050607080L;

    @Test
    @Timeout(15)
    void acceptsImmediateEncryptedInputOnARealNioEventLoop() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var eventLoop = new NioEventLoopGroup(1);
            var loginRequest = new CompletableFuture<LoginRequestMessage>();
            var firstInput = new CompletableFuture<GameMessage>();
            var accepted = new AtomicReference<Channel>();
            var observedClient = new AtomicReference<GameClient>();
            Channel server = null;
            try {
                server = new ServerBootstrap().group(eventLoop).channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel channel) {
                                accepted.set(channel);
                                channel.pipeline().addLast("login-decoder", new LoginDecoder(fixture.context,
                                        fixture.repository));
                                channel.pipeline().addLast("login-encoder", new LoginEncoder());
                                channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
                                        if (message instanceof LoginRequestMessage request) {
                                            loginRequest.complete(request);
                                        } else if (message instanceof GameMessage game) {
                                            var client = (GameClient) ctx.channel().attr(Client.KEY).get();
                                            observedClient.set(client);
                                            client.messageReceived(game);
                                            GameMessage queued = client.pendingReadMessages.poll();
                                            if (!firstInput.complete(queued)) {
                                                queued.getPayload().releaseAll();
                                            }
                                        } else {
                                            ReferenceCountUtil.release(message);
                                        }
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                        Client<?> client = ctx.channel().attr(Client.KEY).get();
                                        if (client != null) {
                                            client.onInactive();
                                        }
                                        ctx.fireChannelInactive();
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        loginRequest.completeExceptionally(cause);
                                        firstInput.completeExceptionally(cause);
                                        ctx.close();
                                    }
                                });
                            }
                        }).bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
                try (Socket socket = new Socket()) {
                    socket.connect(server.localAddress(), 3000);
                    socket.setSoTimeout(3000);
                    socket.getOutputStream().write(new byte[]{14, 0});
                    byte[] handshake = socket.getInputStream().readNBytes(17);
                    assertEquals(17, handshake.length);
                    long serverHalf = ByteBuffer.wrap(handshake, 9, Long.BYTES).getLong();
                    ByteBuf encodedLogin = loginPacket(serverHalf, "tester", "password");
                    try {
                        socket.getOutputStream().write(ByteBufUtil.getBytes(encodedLogin));
                    } finally {
                        encodedLogin.release();
                    }
                    LoginRequestMessage request = loginRequest.get(3, TimeUnit.SECONDS);
                    var login = (LoginClient) accepted.get().attr(Client.KEY).get();
                    assertTrue(login.sendFinalLoginResponse(fixture.player, fixture.data, request));
                    assertSame(Thread.currentThread(), fixture.loadThread.get());

                    assertArrayEquals(new byte[]{2, 0, 0}, socket.getInputStream().readNBytes(3));
                    var cipher = new IsaacCipher(seed(serverHalf));
                    socket.getOutputStream().write(new byte[]{(byte) (42 + cipher.nextInt()), 77});
                    GameMessage message = firstInput.get(3, TimeUnit.SECONDS);
                    try {
                        assertEquals(42, message.getOpcode());
                        assertEquals(77, message.getPayload().getBuffer().readUnsignedByte());
                        assertSame(fixture.client.get(), observedClient.get());
                        assertSame(fixture.client.get(), accepted.get().attr(Client.KEY).get());
                    } finally {
                        message.getPayload().releaseAll();
                    }
                }
            } finally {
                if (accepted.get() != null) {
                    accepted.get().close().syncUninterruptibly();
                }
                if (server != null) {
                    server.close().syncUninterruptibly();
                }
                eventLoop.shutdownGracefully(0, 3, TimeUnit.SECONDS).syncUninterruptibly();
            }
            verify(fixture.logout).submit(eq("tester"), any(LogoutService.LogoutRequest.class));
        }
    }

    @Test
    void canonicalizesMixedCaseAndPaddedCredentialsBeforeAuthentication() throws Exception {
        try (Fixture fixture = new Fixture()) {
            LoginRequestMessage request = fixture.authenticate(false, "  TeStEr  ", "  PaSsWoRd  ");

            assertEquals("tester", request.getUsername());
            assertEquals("password", request.getPassword());
        }
    }

    @Test
    void preservesGamePacketsCoalescedWithTheLoginRequest() throws Exception {
        try (Fixture fixture = new Fixture()) {
            LoginRequestMessage request = fixture.authenticate(true);
            assertFalse(fixture.channel.config().isAutoRead());
            assertInstanceOf(LoginClient.class, fixture.channel.attr(Client.KEY).get());
            assertNull(fixture.channel.readInbound());

            assertTrue(fixture.finish(request));
            assertSame(Thread.currentThread(), fixture.loadThread.get());
            fixture.channel.runPendingTasks();

            assertSame(fixture.client.get(), fixture.channel.attr(Client.KEY).get());
            assertTrue(fixture.channel.config().isAutoRead());
            fixture.assertSuccess();
            fixture.assertGamePacket(42, 11);
            fixture.assertGamePacket(43, 12);
            assertNull(fixture.channel.readInbound());
        }
    }

    @Test
    void acceptsTheFirstGamePacketDuringTheSuccessFlush() throws Exception {
        try (Fixture fixture = new Fixture()) {
            LoginRequestMessage request = fixture.authenticate(false);
            AtomicBoolean firstFlush = new AtomicBoolean(true);
            fixture.channel.pipeline().addFirst("immediate-input", new ChannelOutboundHandlerAdapter() {
                @Override
                public void flush(ChannelHandlerContext ctx) throws Exception {
                    ctx.flush();
                    if (firstFlush.getAndSet(false)) {
                        assertInstanceOf(GameMessageEncoder.class, ctx.pipeline().get("game-encoder"));
                        assertInstanceOf(GameMessageDecoder.class, ctx.pipeline().get("game-decoder"));
                        fixture.channel.writeInbound(fixture.gamePacket(42, 21));
                    }
                }
            });

            assertTrue(fixture.finish(request));
            fixture.channel.runPendingTasks();

            assertTrue(fixture.channel.isActive());
            fixture.assertSuccess();
            fixture.assertGamePacket(42, 21);
            assertSame(fixture.client.get(), fixture.channel.attr(Client.KEY).get());
        }
    }

    @Test
    void keepsHeldGameOutputBehindTheSuccessResponse() throws Exception {
        try (Fixture fixture = new Fixture()) {
            LoginRequestMessage request = fixture.authenticate(false);
            doAnswer(invocation -> {
                fixture.client.get().queue(new GameMessageWriter() {
                    @Override
                    public ByteMessage write(Player player, ByteBuf buffer) {
                        return ByteMessage.message(54, MessageType.FIXED, buffer).put(71);
                    }
                });
                return null;
            }).when(fixture.player).loadData(fixture.data);

            assertTrue(fixture.finish(request));
            fixture.channel.runPendingTasks();
            fixture.channel.flushOutbound();

            fixture.assertSuccess();
            ByteBuf game = fixture.channel.readOutbound();
            try {
                assertEquals((54 + fixture.serverCipher.nextInt()) & 0xff, game.readUnsignedByte());
                assertEquals(71, game.readUnsignedByte());
                assertFalse(game.isReadable());
            } finally {
                game.release();
            }
        }
    }

    @Test
    void preservesAFragmentedFirstGamePacket() throws Exception {
        try (Fixture fixture = new Fixture()) {
            LoginRequestMessage request = fixture.authenticate(false);
            assertTrue(fixture.finish(request));
            fixture.channel.runPendingTasks();
            fixture.assertSuccess();

            ByteBuf packet = fixture.gamePacket(42, 31);
            try {
                fixture.channel.writeInbound(packet.readRetainedSlice(1));
                assertNull(fixture.channel.readInbound());
                fixture.channel.writeInbound(packet.readRetainedSlice(1));
            } finally {
                packet.release();
            }
            fixture.assertGamePacket(42, 31);
        }
    }

    @Test
    void rejectsAChannelThatClosedBeforePlayerDataWasLoaded() throws Exception {
        try (Fixture fixture = new Fixture()) {
            LoginRequestMessage request = fixture.authenticate(false);
            fixture.channel.close();

            assertFalse(fixture.finish(request));
            verify(fixture.player, never()).loadData(any());
            verify(fixture.logout, never()).submit(anyString(), any());
        }
    }

    @Test
    void requestsLogoutIfTheChannelClosesBeforeTheQueuedTransition() throws Exception {
        try (Fixture fixture = new Fixture()) {
            LoginRequestMessage request = fixture.authenticate(false);
            fixture.channel.eventLoop().execute(() -> fixture.channel.close());

            assertTrue(fixture.finish(request));
            fixture.channel.runPendingTasks();

            assertFalse(fixture.channel.isActive());
            verify(fixture.logout).submit(eq("tester"), any(LogoutService.LogoutRequest.class));
            assertNull(fixture.channel.readOutbound());
        }
    }

    @Test
    void requestsLogoutAndClosesWhenTheSuccessWriteFails() throws Exception {
        try (Fixture fixture = new Fixture()) {
            LoginRequestMessage request = fixture.authenticate(false);
            fixture.channel.pipeline().addFirst("failed-write", new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                    ReferenceCountUtil.release(message);
                    promise.setFailure(new IOException("test write failure"));
                }
            });

            assertTrue(fixture.finish(request));
            fixture.channel.runPendingTasks();

            assertFalse(fixture.channel.isActive());
            verify(fixture.logout).submit(eq("tester"), any(LogoutService.LogoutRequest.class));
            assertNull(fixture.channel.readOutbound());
        }
    }

    @Test
    void releasesHeldPayloadsWhenLoadingFailsBeforeTheTransition() throws Exception {
        try (Fixture fixture = new Fixture()) {
            LoginRequestMessage request = fixture.authenticate(false);
            AtomicReference<ByteBuf> payload = new AtomicReference<>();
            doAnswer(invocation -> {
                fixture.client.get().queue(new GameMessageWriter() {
                    @Override
                    public ByteMessage write(Player player, ByteBuf buffer) {
                        payload.set(buffer);
                        return ByteMessage.message(54, MessageType.FIXED, buffer).put(71);
                    }
                });
                throw new IllegalStateException("test load failure");
            }).when(fixture.player).loadData(fixture.data);

            assertThrows(IllegalStateException.class, () -> fixture.finish(request));

            assertFalse(fixture.channel.isActive());
            assertEquals(0, payload.get().refCnt());
            verify(fixture.logout, never()).submit(anyString(), any());
        }
    }

    @Test
    void preservesAnExistingManualReadConfiguration() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.channel.config().setAutoRead(false);
            LoginRequestMessage request = fixture.authenticate(false);

            assertTrue(fixture.finish(request));
            fixture.channel.runPendingTasks();

            assertFalse(fixture.channel.config().isAutoRead());
            fixture.assertSuccess();
        }
    }

    private static int[] seed(long serverHalf) {
        return new int[]{(int) (CLIENT_HALF >> 32), (int) CLIENT_HALF,
                (int) (serverHalf >> 32), (int) serverHalf};
    }

    private static ByteBuf loginPacket(long serverHalf, String username, String password) throws IOException {
        var publicKey = JsonParser.parseString(Files.readString(Path.of("data/net/rsa/rsapub.json")))
                .getAsJsonObject();
        BigInteger modulus = new BigInteger(publicKey.get("modulus").getAsString());
        BigInteger exponent = new BigInteger(publicKey.get("exponent").getAsString());
        ByteBuf clear = Unpooled.buffer();
        byte[] encrypted;
        try {
            clear.writeByte(10).writeLong(CLIENT_HALF).writeLong(serverHalf).writeInt(0);
            clear.writeBytes((username + "\n" + password + "\n")
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            byte[] clearBytes = new byte[clear.readableBytes()];
            clear.readBytes(clearBytes);
            encrypted = new BigInteger(1, clearBytes).modPow(exponent, modulus).toByteArray();
        } finally {
            clear.release();
        }
        ByteBuf request = Unpooled.buffer();
        request.writeByte(16).writeByte(encrypted.length + 41);
        request.writeByte(255).writeShort(377).writeByte(0);
        request.writeZero(9 * Integer.BYTES);
        request.writeByte(encrypted.length).writeBytes(encrypted);
        return request;
    }

    /**
     * Supplies an authenticated encrypted stream without starting world services.
     *
     * @author lare96
     */
    private static final class Fixture implements AutoCloseable {
        private final LunaContext context = mock(LunaContext.class);
        private final World world = mock(World.class);
        private final LogoutService logout = mock(LogoutService.class);
        private final Player player = mock(Player.class);
        private final PlayerData data = mock(PlayerData.class);
        private final AtomicReference<GameClient> client = new AtomicReference<>();
        private final AtomicReference<Thread> loadThread = new AtomicReference<>();
        private final GameMessageRepository repository = new GameMessageRepository();
        private final EmbeddedChannel channel = new EmbeddedChannel() {
            private boolean runningTasks;

            @Override
            protected SocketAddress remoteAddress0() {
                return new InetSocketAddress("127.0.0.1", 43594);
            }

            @Override
            public void runPendingTasks() {
                // EmbeddedUnsafe also drains tasks from write/close; real event loops do not reenter their queue.
                if (!runningTasks) {
                    runningTasks = true;
                    try {
                        super.runPendingTasks();
                    } finally {
                        runningTasks = false;
                    }
                }
            }
        };
        private IsaacCipher clientCipher;
        private IsaacCipher serverCipher;

        private Fixture() {
            when(context.getWorld()).thenReturn(world);
            when(world.getLogoutService()).thenReturn(logout);
            when(world.getPlayer(anyLong())).thenReturn(Optional.empty());
            when(player.getWorld()).thenReturn(world);
            when(player.getUsername()).thenReturn("tester");
            when(player.getRights()).thenReturn(PlayerRights.PLAYER);
            doAnswer(invocation -> {
                client.set(invocation.getArgument(0));
                return null;
            }).when(player).setClient(any());
            doAnswer(invocation -> {
                loadThread.set(Thread.currentThread());
                return null;
            }).when(player).loadData(data);
            for (int opcode : new int[]{42, 43}) {
                GameMessageReader<?> reader = mock(GameMessageReader.class);
                when(reader.getOpcode()).thenReturn(opcode);
                when(reader.getSize()).thenReturn(1);
                repository.put(reader);
            }
            channel.pipeline().addLast("login-decoder", new LoginDecoder(context, repository));
            channel.pipeline().addLast("login-encoder", new LoginEncoder());
        }

        private LoginRequestMessage authenticate(boolean pipelineGameInput) throws Exception {
            return authenticate(pipelineGameInput, "tester", "password");
        }

        private LoginRequestMessage authenticate(boolean pipelineGameInput, String username, String password)
                throws Exception {
            channel.writeInbound(Unpooled.buffer(2).writeByte(14).writeByte(0));
            ByteBuf handshake = channel.readOutbound();
            long serverHalf;
            try {
                handshake.skipBytes(9);
                serverHalf = handshake.readLong();
            } finally {
                handshake.release();
            }
            int[] seed = seed(serverHalf);
            clientCipher = new IsaacCipher(seed.clone());
            for (int i = 0; i < seed.length; i++) {
                seed[i] += 50;
            }
            serverCipher = new IsaacCipher(seed);

            ByteBuf request = loginPacket(serverHalf, username, password);
            if (pipelineGameInput) {
                ByteBuf first = gamePacket(42, 11);
                ByteBuf second = gamePacket(43, 12);
                try {
                    request.writeBytes(first).writeBytes(second);
                } finally {
                    first.release();
                    second.release();
                }
            }
            channel.writeInbound(request);
            return channel.readInbound();
        }

        private ByteBuf gamePacket(int opcode, int value) {
            return Unpooled.buffer(2).writeByte(opcode + clientCipher.nextInt()).writeByte(value);
        }

        private boolean finish(LoginRequestMessage request) {
            LoginClient login = (LoginClient) channel.attr(Client.KEY).get();
            return login.sendFinalLoginResponse(player, data, request);
        }

        private void assertSuccess() {
            ByteBuf response = channel.readOutbound();
            try {
                assertNotNull(response);
                assertEquals(LoginResponse.NORMAL.getOpcode(), response.readUnsignedByte());
                assertEquals(PlayerRights.PLAYER.getClientValue(), response.readUnsignedByte());
                assertEquals(0, response.readUnsignedByte());
                assertFalse(response.isReadable());
            } finally {
                ReferenceCountUtil.release(response);
            }
        }

        private void assertGamePacket(int opcode, int value) {
            GameMessage message = channel.readInbound();
            try {
                assertNotNull(message);
                assertEquals(opcode, message.getOpcode());
                assertEquals(value, message.getPayload().getBuffer().readUnsignedByte());
            } finally {
                if (message != null) {
                    message.getPayload().releaseAll();
                }
            }
        }

        @Override
        public void close() {
            channel.finish();
            Object message;
            while ((message = channel.readInbound()) != null) {
                if (message instanceof GameMessage game) {
                    game.getPayload().releaseAll();
                } else {
                    ReferenceCountUtil.release(message);
                }
            }
            while ((message = channel.readOutbound()) != null) {
                ReferenceCountUtil.release(message);
            }
        }
    }
}
