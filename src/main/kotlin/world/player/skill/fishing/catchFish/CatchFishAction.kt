package world.player.skill.fishing.catchFish

import api.predef.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.model.def.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import world.player.Sounds
import world.player.skill.fishing.Tool

/**
 * An [InventoryAction] implementation that will catch fish.
 */
class CatchFishAction(
    private val msg: NpcClickEvent,
    private val tool: Tool
) : InventoryAction(msg.plr, false, 1, rand(RANDOM_FAIL_RATE)) {


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
                mob.sendMessage("You need a Fishing level of ${tool.level} to fish this.")
                false
            }

            tool.bait != null && !mob.inventory.contains(tool.bait) -> {
                // Check if we have required bait.
                mob.sendMessage(onNoMaterials())
                false
            }

            !mob.inventory.contains(tool.id) -> {
                // Check if we have required tool.
                val toolName = ItemDefinition.ALL.retrieve(tool.id).name
                mob.sendMessage("You need ${addArticle(toolName)} to fish here.")
                false
            }

            else -> {
                // Start fishing!
                mob.animation(Animation(tool.animation))
                mob.playSound(Sounds.FISH) // TODO https://github.com/luna-rs/luna/issues/357
                if (start) {
                    // TODO https://github.com/luna-rs/luna/issues/112
                    mob.sendMessage("You begin to fish...")
                    delay = getFishingDelay()
                }
                true
            }
        }

    override fun execute() {
        messages.forEach(mob::sendMessage)
        messages.clear()
        mob.fishing.addExperience(exp)
        exp = 0.0
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

    override fun onFinished() {
        mob.animation(Animation.CANCEL)
    }

    override fun onNoMaterials(): String {
        return if (tool.bait != null) "You don't have ${itemName(tool.bait)} to fish here." else
            super.onNoMaterials()
    }

    override fun onInventoryFull(): String = "Your inventory is too full to hold any more fish."

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
