package io.luna.net.msg.in;

import io.luna.game.event.impl.DesignPlayerEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerAppearance.DesignPlayerInterface;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the Player uses the character
 * design screen.
 *
 * @author lare96
 */
public final class DesignPlayerMessageReader extends GameMessageReader<DesignPlayerEvent> {

    @Override
    public DesignPlayerEvent decode(Player player, GameMessage msg) {
        int gender = msg.getPayload().get();
        byte[] models = msg.getPayload().getBytes(7);
        byte[] colors = msg.getPayload().getBytes(5);
        int[] values = new int[13];
        int index = 0;
        values[index++] = gender;
        for (int model : models) {
            values[index++] = model;
        }
        for (int color : colors) {
            values[index++] = color;
        }
        return new DesignPlayerEvent(player, gender, models, colors, values);
    }

    @Override
    public boolean validate(Player player, DesignPlayerEvent event) {
        return player.getInterfaces().standardTo(DesignPlayerInterface.class).isPresent();
    }
}