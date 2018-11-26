package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFifthClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFourthClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemThirdClickEvent;
import io.luna.game.model.item.Item;
import io.luna.game.model.item.shop.ShopInterface;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.model.mob.inter.AmountInputInterface;
import io.luna.game.model.mob.trade.OfferTradeInterface;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on interface item clicks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WidgetItemClickMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int opcode = msg.getOpcode();
        switch (opcode) {
            case 145:
                return firstIndex(player, msg.getPayload());
            case 117:
                return secondIndex(player, msg.getPayload());
            case 43:
                return thirdIndex(player, msg.getPayload());
            case 129:
                return fourthIndex(player, msg.getPayload());
            case 135:
                return fifthIndex(player, msg.getPayload());
        }
        return null;
    }

    /**
     * The first index click.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private Event firstIndex(Player player, ByteMessage msg) {
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
                if (player.getBank().isOpen()) {
                    player.getBank().deposit(index, 1);
                }
                break;
            case 5382:
                if (player.getBank().isOpen()) {
                    player.getBank().withdraw(index, 1);
                } else if (player.getRights().equalOrGreater(PlayerRights.DEVELOPER)) {
                    player.getInventory().add(new Item(itemId, 1));
                }
                break;
            case 3900:
                player.getInterfaces().standardTo(ShopInterface.class).
                        ifPresent(inter -> inter.getShop().sendBuyValue(player, index));
                break;
            case 3823:
                player.getInterfaces().standardTo(ShopInterface.class).
                        ifPresent(inter -> inter.getShop().sendSellValue(player, index));
                break;
            case 3322:
                player.getInterfaces().standardTo(OfferTradeInterface.class).
                        ifPresent(inter -> inter.add(player, index, 1));
                break;
            case 3415:
                player.getInterfaces().standardTo(OfferTradeInterface.class).
                        ifPresent(inter -> inter.remove(player, index, 1));
                break;
            default:
                return new WidgetItemFirstClickEvent(player, index, interfaceId, itemId);
        }
        return null;
    }

    /**
     * The second index click.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private Event secondIndex(Player player, ByteMessage msg) {
        int interfaceId = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int itemId = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int index = msg.getShort(true, ByteOrder.LITTLE);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");

        switch (interfaceId) {
            case 5064:
                if (player.getBank().isOpen()) {
                    player.getBank().deposit(index, 5);
                }
                break;
            case 5382:
                if (player.getBank().isOpen()) {
                    player.getBank().withdraw(index, 5);
                } else if (player.getRights().equalOrGreater(PlayerRights.DEVELOPER)) {
                    player.getInventory().add(new Item(itemId, 5));
                }
                break;
            case 3900:
                player.getInterfaces().standardTo(ShopInterface.class).
                        ifPresent(inter -> inter.getShop().buy(player, index, 1));
                break;
            case 3823:
                player.getInterfaces().standardTo(ShopInterface.class).
                        ifPresent(inter -> inter.getShop().sell(player, index, 1));
                break;
            case 3322:
                player.getInterfaces().standardTo(OfferTradeInterface.class).
                        ifPresent(inter -> inter.add(player, index, 5));
                break;
            case 3415:
                player.getInterfaces().standardTo(OfferTradeInterface.class).
                        ifPresent(inter -> inter.remove(player, index, 5));
                break;
            default:
                return new WidgetItemSecondClickEvent(player, index, interfaceId, itemId);
        }
        return null;
    }

    /**
     * The third index click.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private Event thirdIndex(Player player, ByteMessage msg) {
        int interfaceId = msg.getShort(ByteOrder.LITTLE);
        int itemId = msg.getShort(ByteTransform.A);
        int index = msg.getShort(ByteTransform.A);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");

        switch (interfaceId) {
            case 5064:
                if (player.getBank().isOpen()) {
                    player.getBank().deposit(index, 10);
                }
                break;
            case 5382:
                if (player.getBank().isOpen()) {
                    player.getBank().withdraw(index, 10);
                } else if (player.getRights().equalOrGreater(PlayerRights.DEVELOPER)) {
                    player.getInventory().add(new Item(itemId, 10));
                }
                break;
            case 3900:
                player.getInterfaces().standardTo(ShopInterface.class).
                        ifPresent(inter -> inter.getShop().buy(player, index, 5));
                break;
            case 3823:
                player.getInterfaces().standardTo(ShopInterface.class).
                        ifPresent(inter -> inter.getShop().sell(player, index, 5));
                break;
            case 3322:
                player.getInterfaces().standardTo(OfferTradeInterface.class).
                        ifPresent(inter -> inter.add(player, index, 10));
                break;
            case 3415:
                player.getInterfaces().standardTo(OfferTradeInterface.class).
                        ifPresent(inter -> inter.remove(player, index, 10));
                break;
            default:
                return new WidgetItemThirdClickEvent(player, index, interfaceId, itemId);
        }
        return null;
    }

    /**
     * The fourth index click.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private Event fourthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteTransform.A);
        int interfaceId = msg.getShort();
        int itemId = msg.getShort(ByteTransform.A);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");

        switch (interfaceId) {
            case 5064:
                if (player.getBank().isOpen()) {
                    player.getBank().deposit(index, player.getInventory().computeAmountForId(itemId));
                }
                break;
            case 5382:
                if (player.getBank().isOpen()) {
                    player.getBank().withdraw(index, player.getBank().computeAmountForId(itemId));
                } else if (player.getRights().equalOrGreater(PlayerRights.DEVELOPER)) {
                    player.getInventory().add(new Item(itemId, 1_000_000));
                }
                break;
            case 3900:
                player.getInterfaces().standardTo(ShopInterface.class).
                        ifPresent(inter -> inter.getShop().buy(player, index, 10));
                break;
            case 3823:
                player.getInterfaces().standardTo(ShopInterface.class).
                        ifPresent(inter -> inter.getShop().sell(player, index, 10));
                break;
            case 3322:
                player.getInterfaces().standardTo(OfferTradeInterface.class).
                        ifPresent(inter -> inter.add(player, index, -1));
                break;
            case 3415:
                player.getInterfaces().standardTo(OfferTradeInterface.class).
                        ifPresent(inter -> inter.remove(player, index, -1));
                break;
            default:
                return new WidgetItemFourthClickEvent(player, index, interfaceId, itemId);
        }
        return null;
    }

    /**
     * The fifth index click.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private Event fifthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteOrder.LITTLE);
        int interfaceId = msg.getShort(false, ByteTransform.A);
        int itemId = msg.getShort(ByteOrder.LITTLE);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");

        switch (interfaceId) {
            case 5064:
                if (player.getBank().isOpen()) {
                    player.getInterfaces().open(new AmountInputInterface() {
                        @Override
                        public void onAmountInput(Player player, int value) {
                            player.getBank().deposit(index, value);
                        }
                    });
                }
                break;
            case 5382:
                player.getInterfaces().open(new AmountInputInterface() {
                    @Override
                    public void onAmountInput(Player player, int value) {
                        if (player.getBank().isOpen()) {
                            player.getBank().withdraw(index, value);
                        } else if (player.getRights().equalOrGreater(PlayerRights.DEVELOPER)) {
                            player.getInventory().add(new Item(itemId, value));
                        }
                    }
                });
                break;
            case 3322:
                player.getInterfaces().open(new AmountInputInterface() {
                    @Override
                    public void onAmountInput(Player player, int value) {
                        player.getInterfaces().standardTo(OfferTradeInterface.class).
                                ifPresent(inter -> inter.add(player, index, value));
                    }
                });
                break;
            case 3415:
                player.getInterfaces().open(new AmountInputInterface() {
                    @Override
                    public void onAmountInput(Player player, int value) {
                        player.getInterfaces().standardTo(OfferTradeInterface.class).
                                ifPresent(inter -> inter.remove(player, index, value));
                    }
                });
                break;
            default:
                return new WidgetItemFifthClickEvent(player, index, interfaceId, itemId);
        }
        return null;
    }
}
