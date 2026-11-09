package io.luna.net.msg.custom;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Reports the current economy unit price for public item Examine requests.
 *
 * @author lare96
 */
public final class ItemExaminePriceHandler implements CustomPacketHandler {

    /**
     * The permanent custom packet assignment for item Examine prices.
     */
    public static final int ITEM_EXAMINE_PRICE = 0;

    @Override
    public int id() {
        return ITEM_EXAMINE_PRICE;
    }

    @Override
    public boolean validate(List<Object> payload) {
        return payload.size() == 1 && payload.getFirst() instanceof Long itemId &&
                itemId >= 0 && itemId < ItemDefinition.SIZE;
    }

    @Override
    public boolean isPassive() {
        return true;
    }

    @Override
    public void handle(Player player, CustomPacketRequest request) {
        if (!validate(request.payload())) {
            return;
        }
        int itemId = ((Long) request.payload().getFirst()).intValue();
        ItemDefinition definition = ItemDefinition.ALL.get(itemId).orElse(null);
        if (definition == null) {
            return;
        }
        ItemDefinition base = definition;
        if (definition.isNoted()) {
            int baseId = definition.getUnnotedId().orElse(-1);
            if (baseId < 0 || baseId >= ItemDefinition.SIZE) {
                return;
            }
            base = ItemDefinition.ALL.get(baseId).orElse(null);
            if (base == null) {
                return;
            }
        }
        // This is public read-only information; ownership and elevated rights are not required.
        if (!definition.isTradeable() || !base.isTradeable()) {
            player.sendMessage(definition.getName() + ": untradeable; no market guide price.");
            return;
        }
        long price = player.getWorld().getEconomy().getPrice(itemId);
        String formatted = NumberFormat.getIntegerInstance(Locale.US).format(price);
        player.sendMessage(definition.getName() + ": current economy guide price is " + formatted + " gp each.");
    }
}
