package io.luna.net.client;

import io.luna.LunaContext;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.service.LoginRequestService;
import io.luna.game.service.LoginRequestService.LoginRequest;
import io.luna.net.codec.login.LoginRequestMessage;
import io.luna.net.codec.login.LoginResponse;
import io.luna.net.codec.login.LoginResponseMessage;
import io.luna.net.msg.GameMessageRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;

/**
 * A {@link Client} implementation model representing login protocol I/O communications.
 *
 * @author lare96 <http://github.com/lare96>
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
    private final LoginRequestService loginService;

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
        var username = msg.getUsername();
        var password = msg.getPassword();
        var player = new Player(context, new PlayerCredentials(username, password));

        if (!username.matches("^[a-z0-9_ ]{1,12}$") || password.isEmpty() || password.length() > 20) {
            // Username/password format invalid, drop connection.
            channel.close();
        } else {
            // Passed initial check, submit login request.
            loginService.submit(new LoginRequest(player, this, msg));
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
     * @return The message repository.
     */
    public GameMessageRepository getMessageRepository() {
        return messageRepository;
    }
}