package io.luna.game.model.mob.bot;

import io.luna.util.GsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies merchant sale settings remain compatible with older configurations and safe inventory bounds.
 *
 * @author lare96
 */
class BotSettingsTest {

    @Test
    void omittedMerchantSettingsUseDefaults() {
        BotSettings settings = GsonUtils.GSON.fromJson("{}", BotSettings.class);

        assertEquals(7, settings.merchantSaleStackLimit());
        assertEquals(1_000L, settings.merchantSaleMinimumStackValue());
    }

    @Test
    void nullMerchantSettingsUseDefaults() {
        BotSettings settings = GsonUtils.GSON.fromJson("""
                {"merchant_sale_stack_limit": null, "merchant_sale_minimum_stack_value": null}
                """, BotSettings.class);

        assertEquals(7, settings.merchantSaleStackLimit());
        assertEquals(1_000L, settings.merchantSaleMinimumStackValue());
    }

    @Test
    void minimumStackValueRetainsValuesLargerThanTheIntegerRange() {
        BotSettings settings = GsonUtils.GSON.fromJson("""
                {"merchant_sale_minimum_stack_value": 3000000000}
                """, BotSettings.class);

        assertEquals(7, settings.merchantSaleStackLimit());
        assertEquals(3_000_000_000L, settings.merchantSaleMinimumStackValue());
    }

    @Test
    void negativeAndZeroLimitsKeepAtLeastOneSaleSlotAndANonnegativeMinimum() {
        for (int limit : new int[]{-1, 0}) {
            BotSettings settings = GsonUtils.GSON.fromJson(
                    "{\"merchant_sale_stack_limit\": " + limit + ", \"merchant_sale_minimum_stack_value\": -1}",
                    BotSettings.class);

            assertEquals(1, settings.merchantSaleStackLimit());
            assertEquals(0L, settings.merchantSaleMinimumStackValue());
        }
    }

    @Test
    void oversizedStackLimitPreservesRoomForTradeProceeds() {
        BotSettings settings = GsonUtils.GSON.fromJson("""
                {"merchant_sale_stack_limit": 2147483647, "merchant_sale_minimum_stack_value": 0}
                """, BotSettings.class);

        assertEquals(27, settings.merchantSaleStackLimit());
        assertEquals(0L, settings.merchantSaleMinimumStackValue());
    }
}
