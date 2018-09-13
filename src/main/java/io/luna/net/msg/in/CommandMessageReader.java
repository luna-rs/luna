package io.luna.net.msg.in;

import io.luna.LunaContext;
import io.luna.game.event.Event;
import io.luna.game.event.impl.CommandEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.script.ScriptException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * A {@link GameMessageReader} implementation that decodes data sent when a {@link Player} tries to
 * activate a command.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class CommandMessageReader extends GameMessageReader {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        String string = msg.getPayload().getString();
        string = string.toLowerCase();
        int index = string.indexOf(' ');

        // Has to be done in Java because of classloader conflicts.
        PlayerRights rights = player.getRights();
        if (string.equals("hotfix") && rights.equalOrGreater(PlayerRights.DEVELOPER)) {
            startHotfix(player);
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
     *
     * @param player The player.
     */
    private void startHotfix(Player player) {
        LunaContext ctx = player.getContext();
        Consumer<String> sendMessage = msg ->
                player.queue(new GameChatboxMessageWriter(msg));

        sendMessage.accept("Hotfix request received.");
        ctx.getService().submit(() -> {
            try {
                PluginBootstrap bootstrap = new PluginBootstrap(ctx);
                bootstrap.init(false);
                sendMessage.accept("Hotfix request finished successfully.");
            } catch (ScriptException | ExecutionException | IOException e) {
                sendMessage.accept("Hotfix request failed. Please check console for error message.");
                LOGGER.catching(e);
            }
        });
    }
}
