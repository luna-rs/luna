package io.luna.game.service;

import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.service.LoginRequestService.LoginRequest;
import io.luna.net.client.Client;
import io.luna.net.client.GameClient;
import io.luna.net.client.LoginClient;
import io.luna.net.codec.game.GameMessageDecoder;
import io.luna.net.codec.game.GameMessageEncoder;
import io.luna.net.codec.login.LoginRequestMessage;
import io.luna.net.codec.login.LoginResponse;

import java.util.Objects;

import static io.luna.util.ThreadUtils.awaitTerminationUninterruptibly;

/**
 * A {@link PersistenceService} implementation that handles login requests.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class LoginRequestService extends PersistenceService<LoginRequest> {

    /**
     * The login request model.
     */
    public static final class LoginRequest {

        /**
         * The player.
         */
        private final Player player;

        /**
         * The player's client.
         */
        private final LoginClient client;

        /**
         * The login request message.
         */
        private final LoginRequestMessage message;

        @Override
        public int hashCode() {
            return Objects.hash(player);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof LoginRequest) {
                var other = (LoginRequest) obj;
                return player.equals(other.player);
            }
            return false;
        }

        /**
         * Creates a new {@link LoginRequest}.
         *
         * @param player The player.
         * @param client The player's client.
         * @param message The login request message.
         */
        public LoginRequest(Player player, LoginClient client, LoginRequestMessage message) {
            this.player = player;
            this.client = client;
            this.message = message;
        }
    }

    /**
     * Creates a new {@link LoginRequestService}.
     *
     * @param world The world.
     */
    public LoginRequestService(World world) {
        super(world);
    }

    @Override
    void addRequest(LoginRequest request) {
        workers.submit(() -> {
            // Load player and get login response.
            if (!pending.contains(request) && !Thread.interrupted()) {
                var player = request.player;
                var response = PERSISTENCE.load(player);
                if (response == LoginResponse.NORMAL) {
                    // Load was successful, queue player for login.
                    pending.add(request);
                } else {
                    // Load wasn't successful, disconnect with login response.
                    request.client.sendLoginResponse(player, response);
                }
            }
        });
    }

    @Override
    void finishRequest(LoginRequest request) {
        // Send the final login response.
        var player = request.player;
        var client = request.client;
        var message = request.message;
        if (world.getPlayers().isFull()) {
            // World is full.
            client.sendLoginResponse(player, LoginResponse.WORLD_FULL);
        } else if (world.getPlayer(player.getUsername()).isPresent()) {
            // Player is already online.
            client.sendLoginResponse(request.player, LoginResponse.ACCOUNT_ONLINE);
        } else {
            // Login completed normally!
            client.sendLoginResponse(player, LoginResponse.NORMAL);

            // Replace login codecs with game codecs.
            var pipeline = message.getPipeline();
            var messageRepository = client.getMessageRepository();
            var messageEncoder = new GameMessageEncoder(message.getEncryptor());
            var messageDecoder = new GameMessageDecoder(message.getDecryptor(), messageRepository);
            pipeline.replace("login-encoder", "game-encoder", messageEncoder);
            pipeline.replace("login-decoder", "game-decoder", messageDecoder);

            // Replace login client with the game client.
            var gameClient = new GameClient(client.getChannel(), player, messageRepository);
            var clientAttr = client.getChannel().attr(Client.KEY);
            clientAttr.set(gameClient);
            player.setClient(gameClient);

            // Add them to the world!
            world.getPlayers().add(player);
            player.setState(EntityState.ACTIVE);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        workers.shutdownNow();
        awaitTerminationUninterruptibly(workers);
    }
}