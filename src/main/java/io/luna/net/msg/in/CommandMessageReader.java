package io.luna.net.msg.in;

import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.Event;
import io.luna.game.event.impl.CommandEvent;
import io.luna.game.model.mobile.Player;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.game.plugin.PluginManager;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.InboundMessageReader;

/**
 * An {@link InboundMessageReader} implementation that decodes data sent when a {@link Player} tries to activate a command.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class CommandMessageReader extends InboundMessageReader {

    @Override
    public Event decode(Player player, GameMessage msg) throws Exception {
        String string = msg.getPayload().getString();
        string = string.toLowerCase();
        int index = string.indexOf(' ');
        if (index == -1) {
            return new CommandEvent(string);
        }

        String name = string.substring(0, index);
        String[] args = string.substring(index + 1).split(" ");

        // Has to be done in Java because of classpath conflicts.
        if (name.equals("reloadplugins")) {
            // TODO: implement some sort of additional cache that will allow for "hot fixes" without potentially interrupting
            // gameplay. for now just clear plugin cache and do async reload of bootstrap
            LunaContext ctx = player.getContext();
            PluginManager plugins = ctx.getPlugins();
            GameService service = ctx.getService();
            plugins.clear();
            service.execute(new PluginBootstrap(ctx));
            return null;
        }

        return new CommandEvent(name, args);
    }
}
