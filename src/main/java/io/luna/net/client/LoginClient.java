package io.luna.net.client;

import io.luna.Luna;
import io.luna.LunaContext;
import io.luna.game.GameSettings.PasswordStrength;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.persistence.PlayerData;
import io.luna.game.LoginService;
import io.luna.game.LoginService.LoginRequest;
import io.luna.net.codec.game.GameMessageDecoder;
import io.luna.net.codec.game.GameMessageEncoder;
import io.luna.net.msg.login.LoginRequestMessage;
import io.luna.net.msg.login.LoginResponse;
import io.luna.net.msg.login.LoginResponseMessage;
import io.luna.net.msg.GameMessageRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import org.mindrot.jbcrypt.BCrypt;

import java.util.concurrent.RejectedExecutionException;

/**
 * A {@link Client} implementation model representing login protocol I/O communications.
 *
 * @author lare96 
 */
public class LoginClient extends Client<LoginRequestMessage> {

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * The message repository.
     */
    private final GameMessageRepository messageRepository;

    /**
     * The world.
     */
    private final World world;

    /**
     * The login service.
     */
    private final LoginService loginService;

    /**
     * Creates a new {@link Client}.
     *
     * @param channel The client's channel.
     * @param messageRepository The message repository.
     */
    public LoginClient(Channel channel, LunaContext context, GameMessageRepository messageRepository) {
        super(channel);
        this.context = context;
        this.messageRepository = messageRepository;
        world = context.getWorld();
        loginService = world.getLoginService();
    }

    @Override
    void onMessageReceived(LoginRequestMessage msg) {
        String username = msg.getUsername();
        String password = msg.getPassword();

        if (!PlayerCredentials.isValidUsername(username) ||
                password.isEmpty() || password.length() > 20) {
            // Username/password format invalid, drop connection. Or we're already loading player data.
            channel.close();
        } else {
            // Passed initial check, submit login request.
            var player = new Player(context,
                    new PlayerCredentials(username, password));
            player.setLowMemory(msg.isLowMemory());
            loginService.submit(username, new LoginRequest(player, this, msg));
        }
    }

    /**
     * Sends a login response to the client. Will add a disconnect listener for any response that isn't {@link LoginResponse#NORMAL}.
     */
    public void sendLoginResponse(Player player, LoginResponse response) {
        var channelFuture = channel.writeAndFlush(new LoginResponseMessage(response, player.getRights(), false));
        if (response != LoginResponse.NORMAL) {
            channelFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Determines what the login response should be once the player's data is loaded.
     *
     * @param data The loaded data.
     * @param enteredPassword The entered password.
     */
    public LoginResponse getLoginResponse(PlayerData data, String enteredPassword) {
        PasswordStrength passwordStrength = Luna.settings().game().passwordStrength();
        if (data == null || passwordStrength == PasswordStrength.NONE) {
            return LoginResponse.NORMAL;
        } else if (!BCrypt.checkpw(enteredPassword, data.password)) {
            return LoginResponse.INVALID_CREDENTIALS;
        } else if (data.isBanned()) {
            return LoginResponse.ACCOUNT_BANNED;
        } else {
            return LoginResponse.NORMAL;
        }
    }

    /**
     * Loads player data and queues the final login response and game protocol transition.
     *
     * @param player The player.
     * @param data The data to sync with the player.
     * @param message The login request message.
     * @return {@code true} if login was accepted and its event-loop transition was queued.
     */
    public boolean sendFinalLoginResponse(Player player, PlayerData data, LoginRequestMessage message) {
        if (!channel.isActive()) {
            return false;
        } else if (world.isFull()) {
            sendLoginResponse(player, LoginResponse.WORLD_FULL);
            return false;
        } else if (world.getLogoutService().isSavePending(player.getUsername()) || world.getPlayer(player.getUsernameHash()).isPresent()) {
            sendLoginResponse(player, LoginResponse.ACCOUNT_ONLINE);
            return false;
        } else {
            var gameClient = new GameClient(channel, messageRepository, player);
            player.setClient(gameClient);

            // Loading player data and releasing its held messages remain game-thread work.
            gameClient.holdOutgoingMessages();
            try {
                player.loadData(data);
                var response = new LoginResponseMessage(LoginResponse.NORMAL, player.getRights(), false);
                channel.eventLoop().execute(() -> finishLoginTransition(gameClient, response, message));
            } catch (RejectedExecutionException e) {
                gameClient.releasePendingMessages();
                channel.close();
                return false;
            } catch (RuntimeException e) {
                gameClient.releasePendingMessages();
                channel.close();
                throw e;
            }

            // FIFO event-loop ordering keeps these writes behind the codec transition.
            gameClient.releaseHeldMessages();
            return true;
        }
    }

    /**
     * Installs the game protocol before flushing success, so immediate input uses the new decoder.
     * Failures request logout because the game thread may already have registered the player.
     *
     * @param gameClient The prepared game client.
     * @param response The successful login response.
     * @param message The authenticated login request.
     */
    private void finishLoginTransition(GameClient gameClient, LoginResponseMessage response,
                                      LoginRequestMessage message) {
        if (!channel.isActive()) {
            gameClient.releasePendingMessages();
            gameClient.sendLogoutRequest();
            return;
        }
        try {
            channel.attr(KEY).set(gameClient);
            var responseFuture = channel.write(response);
            responseFuture.addListener(future -> {
                if (!future.isSuccess()) {
                    gameClient.releasePendingMessages();
                    gameClient.sendLogoutRequest();
                    channel.close();
                }
            });
            if (!channel.isActive()) {
                return;
            }

            var pipeline = channel.pipeline();
            pipeline.replace("login-encoder", "game-encoder", new GameMessageEncoder(message.getEncryptor()));
            pipeline.replace("login-decoder", "game-decoder",
                    new GameMessageDecoder(message.getDecryptor(), messageRepository));
            channel.flush();
        } catch (RuntimeException e) {
            gameClient.releasePendingMessages();
            gameClient.sendLogoutRequest();
            channel.pipeline().fireExceptionCaught(e);
            channel.close();
        }
    }
}
