package io.luna.net.session;

import io.luna.LunaContext;
import io.luna.game.model.World;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.PlayerCredentials;
import io.luna.game.model.mobile.PlayerSerializer;
import io.luna.net.LunaNetworkConstants;
import io.luna.net.codec.game.GameMessageDecoder;
import io.luna.net.codec.game.GameMessageEncoder;
import io.luna.net.codec.login.LoginCredentialsMessage;
import io.luna.net.codec.login.LoginResponse;
import io.luna.net.codec.login.LoginResponseMessage;
import io.luna.net.msg.MessageRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link Session} implementation that handles networking for a {@link Player} during login.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LoginSession extends Session {

    /**
     * The {@link World} dedicated to this {@code LoginSession}.
     */
    private final LunaContext context;

    /**
     * The repository containing data for incoming messages.
     */
    private final MessageRepository messageRepository;

    /**
     * Creates a new {@link GameSession}.
     *
     * @param context The context to be managed under.
     * @param channel The {@link Channel} for this session.
     * @param messageRepository The repository containing data for incoming messages.
     */
    public LoginSession(LunaContext context, Channel channel, MessageRepository messageRepository) {
        super(channel);
        this.context = context;
        this.messageRepository = messageRepository;
    }

    @Override
    public void handleUpstreamMessage(Object msg) throws Exception {
        if (msg instanceof LoginCredentialsMessage) {
            LoginCredentialsMessage credentials = (LoginCredentialsMessage) msg;
            handleCredentials(credentials);
        }
    }

    /**
     * Loads the character file and sends the {@link LoginResponse} code to the client.
     *
     * @param msg The message containing the credentials.
     * @throws Exception If any errors occur while handling credentials.
     */
    private void handleCredentials(LoginCredentialsMessage msg) throws Exception {
        Channel channel = getChannel();
        World world = context.getWorld();
        LoginResponse response = LoginResponse.NORMAL;
        ChannelPipeline pipeline = msg.getPipeline();

        String username = msg.getUsername();
        String password = msg.getPassword();

        checkState(username.matches("^[a-zA-Z0-9_ ]{1,12}$") && !password.isEmpty() && password.length() <= 20);

        Player player = new Player(context, new PlayerCredentials(username, password));

        if (world.getPlayers().isFull()) {
            response = LoginResponse.WORLD_FULL;
        }

        if (world.getPlayer(player.getUsernameHash()).isPresent()) {
            response = LoginResponse.ACCOUNT_ONLINE;
        }

        if (response == LoginResponse.NORMAL) {
            PlayerSerializer deserializer = new PlayerSerializer(player);
            response = deserializer.load(password);
        }

        ChannelFuture future = channel.writeAndFlush(new LoginResponseMessage(response));
        if (response != LoginResponse.NORMAL) {
            future.addListener(ChannelFutureListener.CLOSE);
        } else {
            future.addListener(it -> {
                pipeline.replace("login-encoder", "game-encoder", new GameMessageEncoder(msg.getEncryptor()));
                pipeline
                    .replace("login-decoder", "game-decoder", new GameMessageDecoder(msg.getDecryptor(), messageRepository));

                GameSession session = new GameSession(player, channel, msg.getEncryptor(), msg.getDecryptor(),
                    messageRepository);

                channel.attr(LunaNetworkConstants.SESSION_KEY).set(session);
                player.setSession(session);

                world.queueLogin(player);
            });
        }
    }
}
