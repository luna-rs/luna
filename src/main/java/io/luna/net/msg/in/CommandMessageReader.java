package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.CommandEvent;
import io.luna.game.event.impl.VoidEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.net.msg.custom.CustomPacketCodec;
import io.luna.net.msg.custom.CustomPacketEvent;
import io.luna.net.msg.custom.CustomPacketRegistry;
import io.luna.util.logging.LoggingSettings.FileOutputType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/**
 * A {@link GameMessageReader} implementation that decodes data sent when a {@link Player} tries to
 * activate a command.
 *
 * @author lare96
 */
public final class CommandMessageReader extends GameMessageReader<Event> {

    /**
     * An asynchronous logger that will handle command logs.
     */
    private static final Logger logger = FileOutputType.COMMANDS.getLogger();

    /**
     * The {@code COMMANDS} logging level.
     */
    private static final Level COMMANDS = FileOutputType.COMMANDS.getLevel();

    @Override
    public Event decode(Player player, GameMessage msg) {
        if (CustomPacketCodec.isReserved(msg)) {
            return CustomPacketRegistry.INSTANCE.decode(msg)
                    .<Event>map(request -> new CustomPacketEvent(player, request))
                    .orElse(VoidEvent.INSTANCE);
        }
        String string = msg.getPayload().getString().toLowerCase();
        int index = string.indexOf(' ');
        if (index == -1) {
            return new CommandEvent(player, string, string);
        }
        String name = string.substring(0, index);
        String[] args = string.substring(index + 1).split(" ");
        return new CommandEvent(player, string, name, args);
    }

    @Override
    public void handle(Player player, Event event) {
        if (event instanceof CustomPacketEvent custom) {
            CustomPacketRegistry.INSTANCE.dispatch(player, custom.getRequest());
        } else if (event instanceof CommandEvent command) {
            logger.log(COMMANDS, "{}: {}", player.getUsername(), command.getCompleteString());
        }
    }
}
