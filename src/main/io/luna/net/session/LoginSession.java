package io.luna.net.session;

import static com.google.common.base.Preconditions.checkState;
import io.luna.LunaContext;
import io.luna.game.model.World;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.PlayerCredentials;
import io.luna.game.model.mobile.PlayerSerializer;
import io.luna.net.codec.game.MessageDecoder;
import io.luna.net.codec.game.MessageEncoder;
import io.luna.net.codec.login.LoginCredentialsMessage;
import io.luna.net.codec.login.LoginResponse;
import io.luna.net.codec.login.LoginResponseMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
 * A {@link Session} implementation that handles networking for a {@link Player}
 * during login.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class LoginSession extends Session {

    /**
     * The {@link World} dedicated to this {@code LoginSession}.
     */
    private final World world;

    /**
     * The {@link Player} dedicated to this {@code LoginSession}.
     */
    private final Player player;

    /**
     * Creates a new {@link GameSession}.
     *
     * @param channel The {@link Channel} for this session.
     */
    public LoginSession(LunaContext context, Channel channel) {
        super(channel);
        world = context.getWorld();
        player = new Player(context);
    }

    @Override
    public void handleUpstreamMessage(Object msg) throws Exception {
        if (msg instanceof LoginCredentialsMessage) {
            LoginCredentialsMessage credentials = (LoginCredentialsMessage) msg;
            handleCredentials(credentials);
        }
    }

    /**
     * Loads the character file and sends the {@link LoginResponse} code to the
     * client.
     * 
     * @param msg The message containing the credentials.
     * @throws Exception If any errors occur while handling credentials.
     */
    private void handleCredentials(LoginCredentialsMessage msg) throws Exception {
        Channel channel = getChannel();
        LoginResponse response = LoginResponse.NORMAL;

        String username = msg.getUsername();
        String password = msg.getPassword();

        checkState(username.matches("^[a-zA-Z0-9_ ]{1,12}$") && !password.isEmpty() && password.length() <= 20);

        player.setCredentials(new PlayerCredentials(username, password));

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
            return;
        }

        msg.getPipeline().replace("login-encoder", "game-encoder", new MessageEncoder(msg.getEncryptor()));
        msg.getPipeline().replace("login-decoder", "game-decoder", new MessageDecoder(msg.getDecryptor()));
        // TODO queue player for login
    }
}
