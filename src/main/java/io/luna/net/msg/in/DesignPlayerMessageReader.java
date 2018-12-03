package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the Player uses the character
 * design screen.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class DesignPlayerMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int gender = msg.getPayload().get();
        byte[] models = msg.getPayload().getBytes(7);
        byte[] colors = msg.getPayload().getBytes(5);
        int[] values = new int[13];
        int index = 0;

        values[index++] = gender;
        for (byte model : models) {
            values[index++] = model;
        }
        for (byte color : colors) {
            values[index++] = color;
        }
        player.getAppearance().setValues(values);
        player.getFlags().flag(UpdateFlag.APPEARANCE);
        player.getInterfaces().close();
        return null;
    }
}