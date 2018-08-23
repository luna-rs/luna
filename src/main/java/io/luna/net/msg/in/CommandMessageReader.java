package io.luna.net.msg.in;

import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.Event;
import io.luna.game.event.impl.CommandEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;
import io.luna.net.msg.out.GameChatboxMessageWriter;

import java.util.function.Consumer;

/**
 * A {@link MessageReader} implementation that decodes data sent when a {@link Player} tries to activate a command.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class CommandMessageReader extends MessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        String string = msg.getPayload().getString();
        string = string.toLowerCase();
        int index = string.indexOf(' ');

        /* Has to be done in Java because of classloader conflicts. */
        if (string.equals("hotfix") && player.getRights().equalOrGreater(PlayerRights.DEVELOPER)) {
            hotfix(player, player.getContext());
            return null;
        }

        if (index == -1) {
            return new CommandEvent(player, string);
        }

        String name = string.substring(0, index);
        String[] args = string.substring(index + 1).split(" ");
        return new CommandEvent(player, name, args);
    }

    /**
     * Constructs and loads another bootstrap instance.
     */
    private void hotfix(Player player, LunaContext ctx) {
        GameService service = ctx.getService();
        Consumer<String> sendMessage = msg -> player.queue(new GameChatboxMessageWriter(msg));

        sendMessage.accept("Hotfix request received...");
        service.execute(() -> {
            try {
                PluginBootstrap bootstrap = new PluginBootstrap(ctx);
                bootstrap.init();

                service.sync(() -> sendMessage.accept("Hotfix request finished successfully!"));
            } catch (Exception e) {
                service.sync(() ->
                        sendMessage.accept("Hotfix request failed. Please check console for error message."));
                e.printStackTrace();
            }
        });
    }
}
