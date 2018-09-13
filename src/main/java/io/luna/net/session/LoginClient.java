package io.luna.net.session;

import io.luna.LunaContext;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.model.mob.PlayerSerializer;
import io.luna.net.LunaChannelFilter;
import io.luna.net.codec.game.GameMessageDecoder;
import io.luna.net.codec.game.GameMessageEncoder;
import io.luna.net.codec.login.LoginCredentialsMessage;
import io.luna.net.codec.login.LoginResponse;
import io.luna.net.codec.login.LoginResponseMessage;
import io.luna.net.msg.GameMessageRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link Client} implementation model representing login protocol I/O communications.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class LoginClient extends Client<LoginCredentialsMessage> {

    /**
     * A listener ran once the login response message is sent to the client. Its primary function is to
     * add new codecs and prepare the Player for gameplay.
     */
    private final class LoginResponseFlushListener implements GenericFutureListener<Future<Void>> {

        /**
         * The player.
         */
        private final Player player;

        /**
         * The login credentials.
         */
        private final LoginCredentialsMessage msg;

        /**
         * Creates a new {@link LoginResponseFlushListener}.
         *
         * @param player The player.
         * @param msg The login credentials.
         */
        private LoginResponseFlushListener(Player player, LoginCredentialsMessage msg) {
            this.player = player;
            this.msg = msg;
        }

        @Override
        public void operationComplete(Future<Void> future) {
            ChannelPipeline pipeline = msg.getPipeline();

            // Replace message encoder.
            GameMessageEncoder messageEncoder = new GameMessageEncoder(msg.getEncryptor());
            pipeline.replace("login-encoder", "game-encoder", messageEncoder);

            // Replace message decoder.
            GameMessageDecoder messageDecoder = new GameMessageDecoder(msg.getDecryptor(), repository);
            pipeline.replace("login-decoder", "game-decoder", messageDecoder);

            // Set new client values.
            GameClient gameClient = new GameClient(channel, player, repository);
            channel.attr(KEY).set(gameClient);
            player.setClient(gameClient);

            // Queue for login.
            world.queueLogin(player);
        }
    }

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * The message repository.
     */
    private final GameMessageRepository repository;

    /**
     * The world.
     */
    private final World world;

    /**
     * Creates a new {@link Client}.
     *
     * @param channel The client's channel.
     * @param repository The message repository.
     */
    public LoginClient(Channel channel, LunaContext context, GameMessageRepository repository) {
        super(channel);
        this.context = context;
        this.repository = repository;
        world = context.getWorld();
    }

    @Override
    void onMessageReceived(LoginCredentialsMessage msg) {

        // Validate username and password.
        String username = msg.getUsername();
        String password = msg.getPassword();
        checkState(username.matches("^[a-z0-9_ ]{1,12}$") && !password.isEmpty() && password.length() <= 20);

        // Create player and assign rights.
        Player player = new Player(context, new PlayerCredentials(username, password));
        if (LunaChannelFilter.WHITELIST.contains(ipAddress)) {
            player.setRights(PlayerRights.DEVELOPER);
        }

        // Determine login response and send it to the client.
        LoginResponse response = getLoginResponse(player);
        LoginResponseMessage responseMsg = new LoginResponseMessage(response, player.getRights(), false);

        channel.writeAndFlush(responseMsg).addListener(response == LoginResponse.NORMAL ?
                new LoginResponseFlushListener(player, msg) : ChannelFutureListener.CLOSE);
    }

    /**
     * Determines the login response for {@code player}.
     *
     * @param player The player.
     * @return The login response.
     */
    private LoginResponse getLoginResponse(Player player) {
        if (world.getPlayers().isFull()) {
            return LoginResponse.WORLD_FULL;
        } else if (world.getPlayer(player.getUsernameHash()).isPresent()) {
            return LoginResponse.ACCOUNT_ONLINE;
        } else {
            PlayerSerializer deserializer = new PlayerSerializer(player);
            LoginResponse response = deserializer.load(player.getPassword());
            return handlePunishments(player).orElse(response);
        }
    }


    /**
     * Returns an optional describing the result of managing punishments.
     *
     * @param player The player.
     * @return The login response, wrapped in an optional.
     */
    private Optional<LoginResponse> handlePunishments(Player player) {
        // TODO rewrite
        LocalDate now = LocalDate.now();
        Function<String, LocalDate> liftFunc =
                it -> it.equals("never") ? now.plusYears(1) : LocalDate.parse(it);

        if (player.isBanned()) {
            LocalDate liftDate = liftFunc.apply(player.getUnbanDate());
            if (now.isBefore(liftDate)) {
                return Optional.of(LoginResponse.ACCOUNT_BANNED);
            } else {
                // Player has been unbanned.
                player.setUnbanDate("n/a");
            }
        }

        if (player.isMuted()) {
            LocalDate liftDate = liftFunc.apply(player.getUnmuteDate());
            if (liftDate.isBefore(now)) {
                // Player has been unmuted.
                player.setUnmuteDate("n/a");
            }
        }
        return Optional.empty();
    }
}