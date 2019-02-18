package world.player.skill.fishing

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.HarvestingAction
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation

/**
 * A [HarvestingAction] implementation that will catch fish.
 */
class FishAction(private val msg: NpcClickEvent,
                 private val tool: Tool) : HarvestingAction(msg.plr, rand(10, 50)) {

    /**
     * The messages to send on harvest.
     */
    val messages = mutableListOf<String>()

    /**
     * The exp that will be awarded on harvest.
     */
    private var exp = 0.0

    override fun initialize() {
        // TODO Send proper tool message
        mob.sendMessage("You begin to fish...")
    }

    override fun canHarvest() =
        when {
            mob.fishing.level < tool.level -> {
                // Check if we have required level.
                mob.sendMessage("You need a Fishing level of ${tool.level} to fish here.")
                false
            }
            tool.bait != null && mob.inventory.contains(tool.bait) -> {
                // Check if we have required bait.
                mob.sendMessage("You do not have the bait required to fish here.")
                false
            }
            !mob.inventory.contains(tool.id) -> {
                // Check if we have required tool.
                mob.sendMessage("You do not have the tool required to fish here.")
                false
            }
            else -> {
                // Start fishing!
                mob.animation(Animation(tool.animation))
                true
            }
        }

    override fun onHarvest() {
        // Send messages.
        messages.forEach(mob::sendMessage)
        messages.clear()

        // Add experience.
        mob.fishing.addExperience(exp)
        exp = 0.0
    }

    override fun harvestChance() = tool.frequency

    override fun add(): Array<Item?> {
        val amount = tool.catchAmount.random()
        val availableFish = tool.fish.filter { it.level <= mob.fishing.level }
        val addItems = arrayOfNulls<Item>(amount)
        var index = 0

        // Fill addItems with random available fish.
        repeat(amount) {
            val fish = availableFish.random()
            val item = fish.toItem(this)
            if (item != null) {
                addItems[index++] = item
                exp += fish.exp
                messages += fish.catchMessage
            }
        }
        return addItems
    }

    override fun remove() =
        if (tool.bait != null)
            arrayOf(Item(tool.bait)) else emptyArray()

    override fun onInterrupt() = mob.animation(Animation.CANCEL)

    override fun isEqual(other: Action<*>?) =
        when (other) {
            is FishAction -> msg.npc == other.msg.npc
            else -> false
        }
}
