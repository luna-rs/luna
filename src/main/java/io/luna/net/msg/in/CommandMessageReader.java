package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.CommandEvent;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.PlayerRights;
import io.luna.game.plugin.Hotfixer;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.InboundMessageReader;
import io.luna.net.msg.out.GameChatboxMessageWriter;

/**
 * An {@link InboundMessageReader} implementation that decodes data sent when a {@link Player} tries to activate a command.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class CommandMessageReader extends InboundMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        String string = msg.getPayload().getString();
        string = string.toLowerCase();
        int index = string.indexOf(' ');

        // Has to be done in Java because of classpath conflicts.
        if (string.equals("hotfix") && player.getRights().equalOrGreater(PlayerRights.DEVELOPER)) {
            initHotfix(player);
            return null;
        }

        if (index == -1) {
            return new CommandEvent(string, player.getRights());
        }

        String name = string.substring(0, index);
        String[] args = string.substring(index + 1).split(" ");
        return new CommandEvent(name, args, player.getRights());
    }

    /**
     * Initiates a bootstrapping process in which all plugins will be 'reloaded' dynamically. Also popularly known as
     * 'hotfixing'.
     */
    private void initHotfix(Player player) {
        player.queue(new GameChatboxMessageWriter("Hotfix request received, initializing Hotfixer..."));

        Hotfixer.newHotfixer(player.getContext()).
            addListener(it -> player.queue(new GameChatboxMessageWriter("Hotfix successfully completed!"))).init();
    }
}
