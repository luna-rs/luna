package io.luna.net.msg.in;

import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the Player uses the character
 * design screen.
 *
 * @author lare96
 */
public final class DesignPlayerMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
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
        return NullEvent.INSTANCE;
    }
}