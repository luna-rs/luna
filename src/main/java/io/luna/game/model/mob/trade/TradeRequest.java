package io.luna.game.model.mob.trade;

import io.luna.game.action.DistancedAction;
import io.luna.game.model.mob.Player;

/**
 * A model containing functions that handle trading requests.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class TradeRequest {

    /**
     * A {@link DistancedAction} implementation that sends a trade request.
     */
    private final class TradeRequestAction extends DistancedAction<Player> {

        /**
         * Creates a new {@link TradeRequestAction}.
         */
        public TradeRequestAction() {
            super(player, other.getPosition(), 1, true);
        }

        @Override
        protected void execute() {
            if (player.getIndex() == other.getTradingWith()) {
                // They've both requested each other, open offer screen.
                player.getWalkingQueue().clear();
                other.getWalkingQueue().clear();

                player.getInterfaces().open(new OfferTradeInterface(other));
                other.getInterfaces().open(new OfferTradeInterface(player));

                player.interact(other);
                other.interact(player);

                player.resetTradingWith();
                other.resetTradingWith();
            } else {
                // Send trade request, wait for response.
                player.sendMessage("Sending trade request...");
                player.interact(other);
                other.sendMessage(player.getUsername() + ":tradereq:");
            }
            player.setTradingWith(other.getIndex());
        }
    }

    /**
     * The player.
     */
    private final Player player;

    /**
     * The trading player.
     */
    private final Player other;

    /**
     * Creates a new {@link TradeRequest}.
     *
     * @param player The player.
     */
    public TradeRequest(Player player, Player other) {
        this.player = player;
        this.other = other;
    }

    /**
     * Sends a trading request through {@link TradeRequestAction}.
     */
    public void request() {
        if (player.getInterfaces().isStandardOpen()) {
            other.sendMessage("You are busy.");
            return;
        }
        if (other.getInterfaces().isStandardOpen()) {
            other.sendMessage("That player is busy.");
            return;
        }
        TradeRequestAction requestAction = new TradeRequestAction();
        player.submitAction(requestAction);
    }
}