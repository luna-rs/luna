package world.player.skill.fishing

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation

/**
 * An [InventoryAction] implementation that will catch fish.
 */
class FishAction(private val msg: NpcClickEvent,
                 private val tool: Tool) : InventoryAction(msg.plr, false, 1, rand(RANDOM_FAIL_RATE)) {


    companion object {

        /**
         * Between 10 and 100 fishing actions for the 'random stop' effect.
         */
        val RANDOM_FAIL_RATE = 10..50

        /**
         * Ensures that fishing does not happen too fast. The minimum tick factor one can achieve with any tool.
         */
        const val MINIMUM_TICK_FACTOR = 5
    }

    /**
     * The messages to send on harvest.
     */
    val messages = mutableListOf<String>()

    /**
     * The exp that will be awarded on harvest.
     */
    private var exp = 0.0

    override fun executeIf(start: Boolean) =
        when {
            mob.fishing.level < tool.level -> {
                // Check if we have required level.
                mob.sendMessage("You need a Fishing level of ${tool.level} to fish here.")
                false
            }
            tool.bait != null && !mob.inventory.contains(tool.bait) -> {
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
                if (start) {
                    // TODO Send proper tool messages
                    mob.sendMessage("You begin to fish...")
                    delay = getFishingDelay()
                }
                true
            }
        }

    override fun execute() {
        // Send messages.
        messages.forEach(mob::sendMessage)
        messages.clear()

        // Add experience.
        mob.fishing.addExperience(exp)
        exp = 0.0

        // Reset fishing delay.
        delay = getFishingDelay()
    }

    override fun add(): List<Item?> {
        val amount = tool.catchAmount.random()
        val availableFish = tool.fish.filter { it.level <= mob.fishing.level }
        val addItems = ArrayList<Item>(amount)

        // Fill addItems with random available fish.
        repeat(amount) {
            val fish = availableFish.random()
            val item = fish.toItem(this)
            if (item != null) {
                addItems += item
                exp += fish.exp
                messages += fish.catchMessage
            }
        }
        return addItems
    }

    override fun remove() =
        if (tool.bait != null)
            listOf(Item(tool.bait)) else emptyList()

    override fun stop() = mob.animation(Animation.CANCEL)

    override fun ignoreIf(other: Action<*>?) =
        when (other) {
            is FishAction -> msg.npc == other.msg.npc
            else -> false
        }

    /**
     * Computes the next fishing delay.
     */
    private fun getFishingDelay(): Int {
        var ticksFactor = tool.catchRate
        ticksFactor -= (mob.fishing.level - tool.level) / 3
        if (ticksFactor < MINIMUM_TICK_FACTOR) {
            ticksFactor = MINIMUM_TICK_FACTOR
        }
        return rand(1, ticksFactor)
    }
}
