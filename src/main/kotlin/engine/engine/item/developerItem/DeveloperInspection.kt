package engine.item.developerItem

import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.EntityState
import io.luna.game.model.item.Bank.DynamicBankInterface
import io.luna.game.model.item.Item
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.movement.wandering.WanderingAction
import io.luna.game.model.`object`.GameObject

/**
 * Read-only target snapshots and explicit economy controls for Luna's toy.
 *
 * @author lare96
 */
internal object DeveloperInspection {
    fun containers(actor: Player, target: Player) {
        if (!DeveloperItemHandler.available(actor, target)) return
        DeveloperItemHandler.menu(actor, listOf("Inventory", "Equipment", "Bank").map { name ->
            name to { viewer: Player ->
                if (DeveloperItemHandler.available(viewer, target)) {
                    val items = when (name) {
                        "Inventory" -> target.inventory
                        "Equipment" -> target.equipment
                        else -> target.bank
                    }.filterNotNull().map { Item(it.id, it.amount) }
                    viewer.overlays.open(object : DynamicBankInterface("${target.username}: $name") {
                        override fun buildDisplayItems(player: Player): ArrayList<Item> = ArrayList(items)
                    })
                }
            }
        })
    }

    fun objectInfo(actor: Player, target: GameObject) {
        if (!DeveloperItemHandler.allowed(actor) || target.state != EntityState.ACTIVE) return
        val d = target.def()
        DeveloperItemHandler.report(actor, "Object: ${d.name}", listOf(
            "Name/ID: ${d.name} / ${target.id}", "Position: ${target.position}",
            "Type: ${target.objectType}; orientation: ${target.direction}",
            "Dimensions: ${target.sizeX()} x ${target.sizeY()} (definition ${d.sizeX} x ${d.sizeY})",
            "Dynamic: ${target.isDynamic}", "Actions: ${d.actions.joinToString()}"))
    }

    fun npcInfo(actor: Player, target: Npc) {
        if (!DeveloperItemHandler.allowed(actor) || target.state != EntityState.ACTIVE) return
        DeveloperItemHandler.report(actor, "NPC: ${target.def().name}", listOf(
            "Name/ID: ${target.def().name} / ${target.id}; base ID ${target.baseId}",
            "Spawn: ${target.basePosition}", "Current: ${target.position}",
            "World index: ${target.index}; HP: ${target.health}",
            "Stationary: ${target.isStationary}; wandering action: ${target.actions.getAll(WanderingAction::class.java).isNotEmpty()}"))
    }

    internal fun itemLines(actor: Player, item: Item, slot: Int? = null): List<String> {
        val d = item.itemDef
        return listOf("Name/ID: ${d.name} / ${item.id}", "Quantity: ${item.amount}; slot: ${slot ?: "ground"}",
            "Store/base value: ${d.value}",
            "Alchemy low/high: ${(d.value * AlchemyType.LOW.valueMultiplier).toInt()} / ${(d.value * AlchemyType.HIGH.valueMultiplier).toInt()}",
            "Economy value: ${actor.world.economy.getPrice(item.id)}",
            "Tradeable: ${d.isTradeable}; noted: ${d.isNoted}",
            "Unnoted ID: ${d.unnotedId.orElse(-1)}; noted ID: ${d.notedId.orElse(-1)}",
            "Inventory actions: ${d.inventoryActions.joinToString()}", "Dynamic: ${item.isDynamic}") +
            if (item.isDynamic) item.asDynamic().attributes().save().entries.map { "${it.key}: ${it.value}" } else emptyList()
    }

    fun inventoryItem(actor: Player, slot: Int, id: Int) {
        if (!DeveloperItemHandler.allowed(actor)) return
        val item = actor.inventory[slot] ?: return
        if (item.id != id) return
        DeveloperItemHandler.menu(actor, listOf(
            "Inspect ${item.itemDef.name}" to { p ->
                if (p.inventory.contains(slot, id)) DeveloperItemHandler.report(p, "Item details", itemLines(p, p.inventory[slot]!!, slot))
            }, "Set economy price" to { p -> DeveloperEconomy.priceInput(p, id, false) },
            "Set economy seed price" to { p -> DeveloperEconomy.priceInput(p, id, true) }))
    }

    fun groundInfo(actor: Player, item: GroundItem) {
        if (!DeveloperItemHandler.allowed(actor) || item.state != EntityState.ACTIVE || !item.isVisibleTo(actor)) return
        val visibility = if (item.isGlobal) "global" else "viewers: " + item.view.joinToString { it.username }
        DeveloperItemHandler.report(actor, "Ground item", itemLines(actor, item.toItem()) + listOf(
            "Position: ${item.position}", "Visibility: $visibility", "Visible to you: ${item.isVisibleTo(actor)}",
            "Expiration enabled: ${item.isExpiring}; elapsed ticks: ${item.expireTicks}",
            "Ticks until next expiration transition: ${item.remainingLifetimeTicks}"))
    }
}
