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

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link Session} implementation that handles login networking.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LoginSession extends Session {

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * The message repository.
     */
    private final MessageRepository messageRepository;

    /**
     * Creates a new {@link GameSession}.
     *
     * @param context The context instance.
     * @param channel The channel.
     * @param messageRepository The message repository.
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
     * Handles the received login credentials.
     */
    private void handleCredentials(LoginCredentialsMessage msg) throws Exception {
        // TODO: Pretty ugly, find a nicer way of doing this?

        Channel channel = getChannel();
        World world = context.getWorld();
        LoginResponse response = LoginResponse.NORMAL;
        ChannelPipeline pipeline = msg.getPipeline();

        String username = msg.getUsername();
        String password = msg.getPassword();

        checkState(username.matches("^[a-z0-9_ ]{1,12}$") && !password.isEmpty() && password.length() <= 20);

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
            response = handlePunishments(player).orElse(response);
        }

        ChannelFuture future = channel.writeAndFlush(new LoginResponseMessage(response, player.getRights(), false));
        if (response != LoginResponse.NORMAL) {
            future.addListener(ChannelFutureListener.CLOSE);
        } else {
            future.addListener(it -> {
                pipeline.replace("login-encoder", "game-encoder", new GameMessageEncoder(msg.getEncryptor()));
                pipeline.replace("login-decoder", "game-decoder",
                    new GameMessageDecoder(msg.getDecryptor(), messageRepository));

                GameSession session = new GameSession(player, channel, msg.getEncryptor(), msg.getDecryptor(),
                    messageRepository);

                channel.attr(LunaNetworkConstants.SESSION_KEY).set(session);
                player.setSession(session);

                world.queueLogin(player);
            });
        }
    }

    /**
     * Returns an optional describing the result of managing punishments.
     */
    private Optional<LoginResponse> handlePunishments(Player player) {
        // TODO: Pretty ugly, find a nicer way of doing this?
        LocalDate now = LocalDate.now();
        Function<String, LocalDate> computeDate = it -> it.equals("never") ? now.plusYears(1) : LocalDate.parse(it);

        if (player.isBanned()) {
            LocalDate then = computeDate.apply(player.getUnbanDate());
            if (now.isBefore(then)) {
                return Optional.of(LoginResponse.ACCOUNT_BANNED);
            } else {
                player.setUnbanDate("n/a");
            }
        }

        if (player.isMuted()) {
            LocalDate then = computeDate.apply(player.getUnmuteDate());
            if (then.isBefore(now)) {
                player.setUnbanDate("n/a");
            }
        }
        return Optional.empty();
    }
}
