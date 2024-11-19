package io.luna.net.client;

import io.luna.Luna;
import io.luna.LunaContext;
import io.luna.game.GameSettings.PasswordStrength;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.persistence.PlayerData;
import io.luna.game.service.LoginService;
import io.luna.game.service.LoginService.LoginRequest;
import io.luna.net.codec.game.GameMessageDecoder;
import io.luna.net.codec.game.GameMessageEncoder;
import io.luna.net.msg.login.LoginRequestMessage;
import io.luna.net.msg.login.LoginResponse;
import io.luna.net.msg.login.LoginResponseMessage;
import io.luna.net.msg.GameMessageRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import org.mindrot.jbcrypt.BCrypt;

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
        var player = new Player(context, new PlayerCredentials(username, password));

        if (!username.matches("^[a-z0-9_ ]{1,12}$") ||
                password.isEmpty() || password.length() > 20) {
            // Username/password format invalid, drop connection. Or we're already loading player data.
            channel.close();
        } else {
            // Passed initial check, submit login request.
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
     * Sends the final login response before the player is added to the world.
     *
     * @param player The player.
     * @param data The data to sync with the player.
     * @param message The login request message.
     * @return {@code true} if the final login response {@link LoginResponse#NORMAL}.
     */
    public boolean sendFinalLoginResponse(Player player, PlayerData data, LoginRequestMessage message) {
        if (world.getPlayers().isFull()) {
            sendLoginResponse(player, LoginResponse.WORLD_FULL);
            return false;
        } else if (world.getLogoutService().isSavePending(player.getUsername()) || world.getPlayer(player.getUsernameHash()).isPresent()) {
            sendLoginResponse(player, LoginResponse.ACCOUNT_ONLINE);
            return false;
        } else {
            var gameClient = new GameClient(channel, messageRepository);
            channel.attr(KEY).set(gameClient);
            player.setClient(gameClient);

            player.loadData(data);
            sendLoginResponse(player, LoginResponse.NORMAL);

            var pipeline = channel.pipeline();
            var messageEncoder = new GameMessageEncoder(message.getEncryptor());
            var messageDecoder = new GameMessageDecoder(message.getDecryptor(), messageRepository);
            pipeline.replace("login-encoder", "game-encoder", messageEncoder);
            pipeline.replace("login-decoder", "game-decoder", messageDecoder);
            return true;
        }
    }
}