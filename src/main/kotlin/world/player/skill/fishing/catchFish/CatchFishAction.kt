package world.player.skill.fishing.catchFish

import api.predef.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import world.player.Sounds
import world.player.skill.Skills
import world.player.skill.fishing.Tool

/**
 * An [InventoryAction] implementation that will catch fish.
 *
 * @author lare96
 */
class CatchFishAction(msg: NpcClickEvent, private val tool: Tool) :
    InventoryAction(msg.plr, false, tool.speed, Int.MAX_VALUE) {

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
                mob.newDialogue().empty(onNoMaterials()).open()
                false
            }

            !mob.inventory.contains(tool.id) -> {
                // Check if we have required tool.
                val toolName = ItemDefinition.ALL.retrieve(tool.id).name
                mob.newDialogue().empty("You need ${addArticle(toolName)} to bait these fish.").open()
                false
            }

            else -> {
                // Start fishing!
                mob.animation(Animation(tool.animation))
                if (start) {
                    mob.sendMessage(tool.message)
                    if(tool == Tool.FISHING_ROD || tool == Tool.FLY_FISHING_ROD) {
                        mob.sendMessage("You attempt to catch a fish.")
                    }
                    playSound(Sounds.START_ROD_FISHING, Sounds.START_FISHING)
                }
                true
            }
        }

    override fun execute() {
        if (currentAdd.isNotEmpty()) {
            messages.removeAll { mob.sendMessage(it); true }
            mob.fishing.addExperience(exp)
            exp = 0.0
            playSound(Sounds.CATCH_ROD_FISH, Sounds.CATCH_FISH)
        }
    }

    override fun add(): List<Item> {
        val amount = tool.catchAmount.random()
        val availableFish = tool.fish.filter { it.level <= mob.fishing.level }
        val addItems = ArrayList<Item>(amount)

        // Fill addItems with random available fish.
        repeat(amount) {
            val fish = availableFish.random()
            val item = fish.toItem(this)
            if (item != null && Skills.success(fish.chance, mob.fishing.level)) {
                addItems += item
                exp += fish.exp
                messages += "You catch some ${fish.formattedName}."
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

    override fun onNoMaterials(): String =
        if (tool.bait != null) "You don't have enough ${itemName(tool.bait)} to fish here." else
            super.onNoMaterials()

    override fun onInventoryFull(): String = "Your inventory is too full to hold any more fish."

    /**
     * Plays one of the argued sounds based on the tool the player is fishing with.
     */
    private fun playSound(rodSound: Sounds, otherSound: Sounds) {
        if(tool == Tool.FISHING_ROD || tool == Tool.FLY_FISHING_ROD) {
            mob.playSound(rodSound)
        } else {
            mob.playSound(otherSound)
        }
    }
}
