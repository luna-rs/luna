package engine.item.developerItem

import api.predef.*
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.economy.EconomySeedOverrides
import io.luna.game.model.mob.Player

/**
 * Explicit guide and seed edits, using background persistence and game-thread feedback.
 *
 * @author lare96
 */
internal object DeveloperEconomy {
    fun priceInput(actor: Player, id: Int, seed: Boolean) {
        if (!DeveloperItemHandler.allowed(actor)) return
        val baseId = ItemDefinition.ALL.retrieve(id).unnotedId.orElse(id)
        DeveloperItemHandler.number(actor, "New ${if (seed) "seed" else "economy"} price in GP:") { player, price ->
            try {
                EconomySeedOverrides.validate(baseId, price)
                val future = if (seed) gameService.submit { EconomySeedOverrides.setAndSave(baseId, price) }
                    else player.world.economy.setGuidePrice(baseId, price)
                player.sendMessage(if (seed) "Saving seed price; existing market prices are retained."
                    else "Price edit queued for the next economy update.")
                future.whenComplete { _, error ->
                    gameService.sync {
                        if (DeveloperItemHandler.allowed(player)) player.sendMessage(
                            if (error == null) "${if (seed) "Seed" else "Economy"} price saved: $baseId = $price GP."
                            else "Price edit failed: ${error.cause?.message ?: error.message}")
                    }
                }
            } catch (error: IllegalArgumentException) {
                player.sendMessage(error.message ?: "Invalid price.")
            } catch (error: IllegalStateException) {
                player.sendMessage(error.message ?: "Price edit unavailable.")
            }
        }
    }
}
