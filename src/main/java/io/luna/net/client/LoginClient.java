package io.luna.net.client;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.luna.LunaContext;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.PlayerRights;
import io.luna.net.LunaChannelFilter;
import io.luna.net.codec.game.GameMessageDecoder;
import io.luna.net.codec.game.GameMessageEncoder;
import io.luna.net.codec.login.LoginCredentialsMessage;
import io.luna.net.codec.login.LoginResponse;
import io.luna.net.codec.login.LoginResponseMessage;
import io.luna.net.msg.GameMessageRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link Client} implementation model representing login protocol I/O communications.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class LoginClient extends Client<LoginCredentialsMessage> {

    /**
     * A callback that sends the login response after deserialization.
     */
    private final class LoginResponseCallback implements FutureCallback<LoginResponse> {

        /**
         * The player.
         */
        private final Player player;

        /**
         * The login credentials.
         */
        private final LoginCredentialsMessage msg;

        /**
         * Creates a new {@link LoginCredentialsMessage}.
         *
         * @param player The player.
         * @param msg The login credentials.
         */
        private LoginResponseCallback(Player player, LoginCredentialsMessage msg) {
            this.player = player;
            this.msg = msg;
        }

        @Override
        public void onSuccess(LoginResponse result) {
            sendLoginResponse(player, result, msg);
        }

        @Override
        public void onFailure(Throwable t) {
            sendLoginResponse(player, LoginResponse.COULD_NOT_COMPLETE_LOGIN, msg);
        }

        /**
         * Initializes this callback using the argued future.
         */
        public void init(ListenableFuture<LoginResponse> loadFuture) {
            Futures.addCallback(loadFuture, this, channel.eventLoop());
        }
    }

    /**
     * A listener that loads the player after the login response is sent.
     */
    private final class LoadPlayerListener implements GenericFutureListener<Future<Void>> {

        /**
         * The player.
         */
        private final Player player;

        /**
         * The login credentials.
         */
        private final LoginCredentialsMessage msg;

        /**
         * Creates a new {@link LoadPlayerListener}.
         *
         * @param player The player.
         * @param msg The login credentials.
         */
        private LoadPlayerListener(Player player, LoginCredentialsMessage msg) {
            this.player = player;
            this.msg = msg;
        }

        @Override
        public void operationComplete(Future<Void> future) {
            var pipeline = msg.getPipeline();

            // Replace message encoder.
            var messageEncoder = new GameMessageEncoder(msg.getEncryptor());
            pipeline.replace("login-encoder", "game-encoder", messageEncoder);

            // Replace message decoder.
            var messageDecoder = new GameMessageDecoder(msg.getDecryptor(), repository);
            pipeline.replace("login-decoder", "game-decoder", messageDecoder);

            // Set new client values.
            var gameClient = new GameClient(channel, player, repository);
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
        var player = new Player(context, new PlayerCredentials(username, password));
        
        if (LunaChannelFilter.WHITELIST.contains(ipAddress)) {
            player.setRights(PlayerRights.DEVELOPER);
        }

        // Determine login response and send it to the client.
        if (world.getPlayers().isFull()) {
            // World is full.
            sendLoginResponse(player, LoginResponse.WORLD_FULL, msg);
        } else if (world.getPlayer(player.getUsernameHash()).isPresent() ||
                world.getPersistence().hasPendingSave(player)) {
            // Player is online, or their file is still being saved.
            sendLoginResponse(player, LoginResponse.ACCOUNT_ONLINE, msg);
        } else if (player.isBanned()) {
            // Player is banned.
            sendLoginResponse(player, LoginResponse.ACCOUNT_BANNED, msg);
        } else {
            // Load persistent player data.
            LoginResponseCallback loadCallback = new LoginResponseCallback(player, msg);
            loadCallback.init(world.loadPlayer(player));
        }
    }

    /**
     * Determines the login response for {@code player}.
     *
     * @param player The player.
     * @param response The login response.
     * @param msg The login credentials message.
     */
    private void sendLoginResponse(Player player, LoginResponse response, LoginCredentialsMessage msg) {
        LoginResponseMessage responseMsg = new LoginResponseMessage(response, player.getRights(), false);
        channel.writeAndFlush(responseMsg).addListener(response == LoginResponse.NORMAL ?
                new LoadPlayerListener(player, msg) : ChannelFutureListener.CLOSE);
    }
}