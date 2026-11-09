package io.luna.game.model.mob.bot;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.net.msg.GameMessageReader;
import io.luna.net.msg.in.*;

import io.luna.game.model.mob.PlayerRights;
import io.luna.game.model.EntityState;

import static com.google.common.base.Preconditions.checkState;

/**
 * Dormant script support for connected developer accounts, selected after successful authentication.
 * Capability does not start scripts; execution requires current developer rights and explicit injection.
 *
 * @author lare96
 */
public final class PlayerAutomation {

    private PlayerAutomation() {
    }

    /**
     * Selects the final login entity after authentication resolves the account's rights, before player data,
     * the real client or world membership are installed. Non-developers retain the provisional player.
     *
     * @param pending The unregistered player used for credential validation.
     * @param rights The effective rights for this login.
     * @return The player to use for the final handshake and world registration.
     */
    public static Player prepareLoginPlayer(Player pending, PlayerRights rights) {
        checkState(pending.getState() == EntityState.NEW, "Select script capability before world registration.");
        if (rights != PlayerRights.DEVELOPER) return pending;
        Bot player = Bot.connectedPlayer(pending.getContext(),
                new PlayerCredentials(pending.getUsername(), pending.getPassword()));
        player.setLowMemory(pending.isLowMemory());
        return player;
    }

    /**
     * Distinguishes deliberate client actions from camera, focus, audio and keepalive traffic.
     * The network queue invokes this on the game thread, before submitting manual input.
     */
    public static boolean interrupts(GameMessageReader<?> reader) {
        return reader instanceof WalkingMessageReader || reader instanceof ButtonClickMessageReader ||
                reader instanceof ItemClickMessageReader || reader instanceof WidgetItemClickMessageReader ||
                reader instanceof NpcClickMessageReader || reader instanceof PlayerClickMessageReader ||
                reader instanceof ObjectClickMessageReader || reader instanceof GroundItemClickMessageReader ||
                reader instanceof ItemOnItemMessageReader || reader instanceof ItemOnNpcMessageReader ||
                reader instanceof ItemOnObjectMessageReader || reader instanceof ItemOnPlayerMessageReader ||
                reader instanceof ItemOnGroundItemMessageReader || reader instanceof MagicOnNpcMessageReader ||
                reader instanceof MagicOnPlayerMessageReader || reader instanceof MagicOnObjectMessageReader ||
                reader instanceof MagicOnItemMessageReader || reader instanceof MagicOnGroundItemMessageReader ||
                reader instanceof EquipItemMessageReader || reader instanceof DropItemMessageReader ||
                reader instanceof ArrangeItemMessageReader || reader instanceof ContinueDialogueMessageReader ||
                reader instanceof CloseInterfaceMessageReader || reader instanceof NumberInputMessageReader ||
                reader instanceof TextInputMessageReader || reader instanceof CommandMessageReader;
    }
}
