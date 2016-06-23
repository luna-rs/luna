package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.InboundMessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link InboundMessageReader} implementation that decodes data sent when a {@link Player} clicks an item on an
 * interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class InterfaceItemClickMessageReader extends InboundMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int opcode = msg.getOpcode();
        switch (opcode) {
        case 145:
            firstIndex(player, msg.getPayload());
            break;
        case 117:
            secondIndex(player, msg.getPayload());
            break;
        case 43:
            thirdIndex(player, msg.getPayload());
            break;
        case 129:
            fourthIndex(player, msg.getPayload());
            break;
        }
        return null;
    }

    /**
     * The first index click.
     */
    private void firstIndex(Player player, ByteMessage msg) {
        int interfaceId = msg.getShort(ByteTransform.A);
        int index = msg.getShort(ByteTransform.A);
        int itemId = msg.getShort(ByteTransform.A);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");

        switch (interfaceId) {
        case 1688:
            player.getEquipment().unequip(index);
            break;
        case 5064:
            player.getBank().deposit(index, 1);
            break;
        case 5382:
            player.getBank().withdraw(index, 1);
            break;
        case 3900:
            //Shop will sell for <x>
            break;
        case 3823:
            //Shop will buy for <x>
            break;
        case 3322:
            // Offer 1 item on trade screen
            break;
        case 3415:
            // Remove 1 item from trade screen
            break;
        }
    }

    /**
     * The second index click.
     */
    private void secondIndex(Player player, ByteMessage msg) {
        int interfaceId = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int itemId = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int index = msg.getShort(true, ByteOrder.LITTLE);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");

        switch (interfaceId) {
        case 5064:
            player.getBank().deposit(index, 5);
            break;
        case 5382:
            player.getBank().withdraw(index, 5);
            break;
        case 3900:
            // Buy 1 of <item> from shop
            break;
        case 3823:
            // Sell 1 of <item> to shop
            break;
        case 3322:
            // Add 5 of <item> on trade screen
            break;
        case 3415:
            // Remove 5 of <item> from trade screen
            break;
        }
    }

    /**
     * The third index click.
     */
    private void thirdIndex(Player player, ByteMessage msg) {
        int interfaceId = msg.getShort(ByteOrder.LITTLE);
        int itemId = msg.getShort(ByteTransform.A);
        int index = msg.getShort(ByteTransform.A);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");

        switch (interfaceId) {
        case 5064:
            player.getBank().deposit(index, 10);
            break;
        case 5382:
            player.getBank().withdraw(index, 10);
            break;
        case 3900:
            // Buy 5 of <item> from shop
            break;
        case 3823:
            // Sell 5 of <item> to shop
            break;
        case 3322:
            // Add 10 of <item> on trade screen
            break;
        case 3415:
            // Remove 10 of <item> from trade screen
            break;
        }
    }

    /**
     * The fourth index click.
     */
    private void fourthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteTransform.A);
        int interfaceId = msg.getShort();
        int itemId = msg.getShort(ByteTransform.A);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");

        switch (interfaceId) {
        case 5064:
            player.getBank().deposit(index, player.getInventory().computeAmountForId(itemId));
            break;
        case 5382:
            player.getBank().withdraw(index, player.getBank().computeAmountForId(itemId));
            break;
        case 3900:
            // Buy 10 of <item> from shop
            break;
        case 3823:
            // Sell 10 of <item> to shop
            break;
        case 3322:
            // Add all of <item> on trade screen
            break;
        case 3415:
            // Remove all of <item> from trade screen
            break;
        }
    }
}
