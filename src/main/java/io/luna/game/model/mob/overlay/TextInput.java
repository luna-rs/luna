package io.luna.game.model.mob.overlay;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.TextInputMessageWriter;

/**
 * An {@link InputOverlay} that prompts the player to enter text input (e.g., a name or string value).
 * <p>
 * This overlay opens the client’s standard “Enter name” input box via {@link TextInputMessageWriter}, allowing
 * the player to type arbitrary text that is then sent back to the server.
 *
 * @author lare96
 */
public abstract class TextInput extends InputOverlay<String> {

    @Override
    public final void open(Player player) {
        player.queue(new TextInputMessageWriter());
    }
}
