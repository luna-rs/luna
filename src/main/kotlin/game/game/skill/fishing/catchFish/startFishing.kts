package game.skill.fishing.catchFish

import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Submits a [CatchFishAction] to start fishing.
 */
fun fish(plr: Player, target: Entity, spot: FishingSpot, tool: Tool) {
    plr.submitAction(CatchFishAction(plr, target, spot, tool))
}

/**
 * Opens a dialogue that says why [item] can't be used on [spot]. Items that aren't fishing tools get the default
 * message instead.
 */
fun wrongTool(plr: Player, spot: FishingSpot, item: Int) {
    val message = when (item) {
        Tool.BIG_NET.id ->
            when (spot) {
                FishingSpot.NET_BAIT -> "The fish here aren't suitable for catching with a Big Net."
                FishingSpot.CAGE_HARPOON, FishingSpot.MISCELLANIA ->
                    "The fish here aren't suitable for catching with a big net."

                else -> "There is nothing suitable for catching with a net here."
            }

        Tool.SMALL_NET.id -> "There is nothing suitable for catching with a net here."
        Tool.FISHING_ROD.id -> "There is nothing suitable for catching with a rod here."
        Tool.FLY_FISHING_ROD.id -> "You should use this rod to catch freshwater fish."
        Tool.HARPOON.id -> "There aren't any fish suitable for Harpooning here."
        Tool.LOBSTER_POT.id -> "There aren't any Lobsters to catch here."
        Tool.KARAMBWAN_VESSEL.id -> "There is nothing suitable for catching with a Karambwan vessel here."
        else -> {
            plr.sendMessage("Nothing interesting happens.")
            return
        }
    }
    plr.newDialogue().text(message).open()
}

/**
 * Fishing tools that may be used on fishing spots.
 */
val FISHING_TOOLS = listOf(Tool.SMALL_NET.id, Tool.BIG_NET.id, Tool.FISHING_ROD.id, Tool.FLY_FISHING_ROD.id,
                           Tool.HARPOON.id, Tool.LOBSTER_POT.id, Tool.LAVA_EEL_FISHING_ROD.id,
                           Tool.KARAMBWAN_VESSEL.id)

/**
 * Lubufu's Karambwan spot in Brimhaven.
 */
val LUBUFU_SPOT = 1178

/**
 * Lubufu's NPC id.
 */
val LUBUFU = 1171

/**
 * Handles [item] being used on Lubufu's spot.
 */
fun useOnLubufuSpot(plr: Player, item: Int) {
    // TODO Tai Bwo Wannai Trio: Lubufu keeps players off his spot until he teaches them, and after they finish the
    //  quest.
    when {
        item != Tool.EMPTY_KARAMBWAN_VESSEL -> plr.sendMessage("Nothing interesting happens.")
        world.locator.findNpcs(plr, 20) { it.id == LUBUFU }.isNotEmpty() ->
            plr.newDialogue().npc(LUBUFU, Expression.DEFAULT, "You'll first have to bait that pot with Karambwanji.")
                .open()
    }
}

/**
 * The tools that burn up when used on lava, and what they're called when they do.
 */
val BURNABLE_TOOLS = mapOf(Tool.SMALL_NET.id to "net",
                           Tool.BIG_NET.id to "net",
                           Tool.HARPOON.id to "harpoon",
                           Tool.FISHING_ROD.id to "rod",
                           Tool.FLY_FISHING_ROD.id to "rod")

/**
 * A chatbox box that shows a tool burning up, without giving the tool back.
 */
class BurntToolDialogue(private val itemId: Int, private val text: String) : DialogueInterface(306) {
    override fun init(plr: Player): Boolean {
        plr.sendText(text, 308)
        plr.queue(WidgetItemModelMessageWriter(307, 200, itemId))
        return true
    }
}

/**
 * Handles [item] being used on [spot]. Using the right tool fishes with it.
 */
fun useTool(plr: Player, target: Entity, spot: FishingSpot, item: Int) {
    when {
        spot == FishingSpot.LAVA_EEL && item in BURNABLE_TOOLS -> {
            plr.inventory.remove(Item(item))
            plr.overlays.open(BurntToolDialogue(item, "The ${BURNABLE_TOOLS[item]} burns up in the lava!"))
        }

        spot == FishingSpot.LAVA_EEL && item != Tool.LAVA_EEL_FISHING_ROD.id ->
            plr.sendMessage("Nothing interesting happens.")

        spot == FishingSpot.NET_BAIT && item == Tool.FLY_FISHING_ROD.id ->
            plr.sendMessage("Nothing interesting happens.")

        target is Npc && target.id == LUBUFU_SPOT && item != Tool.KARAMBWAN_VESSEL.id -> useOnLubufuSpot(plr, item)
        spot == FishingSpot.KARAMBWAN && item == Tool.EMPTY_KARAMBWAN_VESSEL ->
            plr.newDialogue().text("You need to load your vessel with a karambwanji to fish here.").open()

        item == spot.firstOption?.id -> fish(plr, target, spot, spot.firstOption)
        item == spot.secondOption?.id -> fish(plr, target, spot, spot.secondOption)
        else -> wrongTool(plr, spot, item)
    }
}

/**
 * Spots that tools can't be used on.
 */
val NO_TOOL_SPOTS = setOf(FishingSpot.NET_HARPOON, FishingSpot.HARPOON_NET, FishingSpot.CAVE_EEL)

/**
 * Lava spots can be fished from any tile next to them, including across the lava.
 */
val LAVA_REACH = InteractionPolicySupplier { _, _ -> InteractionPolicy(InteractionType.LINE_OF_SIGHT, 1) }

// Fishing spot options, and fishing tools used on fishing spots.
for (spot in FishingSpot.entries) {
    val firstOption = spot.firstOption
    val secondOption = spot.secondOption
    val tools = when (spot) {
        in NO_TOOL_SPOTS -> emptyList()
        FishingSpot.KARAMBWAN -> FISHING_TOOLS + Tool.EMPTY_KARAMBWAN_VESSEL
        else -> FISHING_TOOLS
    }
    val reach = if (spot == FishingSpot.LAVA_EEL) LAVA_REACH else InteractionPolicy.STANDARD_SIZE_BIF
    for (id in spot.npcs + listOfNotNull(spot.whirlpoolId)) {
        if (firstOption != null) {
            npc1(id, reach, false) { fish(plr, targetNpc, spot, firstOption) }
        }
        if (secondOption != null) {
            npc2(id, reach, false) { fish(plr, targetNpc, spot, secondOption) }
        }
        for (tool in tools) {
            useItem(tool).onNpc(id, reach) { useTool(plr, targetNpc, spot, tool) }
        }
    }
    for (id in spot.objects) {
        if (firstOption != null) {
            object1(id, reach) { fish(plr, gameObject, spot, firstOption) }
        }
        if (secondOption != null) {
            object2(id, reach) { fish(plr, gameObject, spot, secondOption) }
        }
        for (tool in tools) {
            useItem(tool).onObject(id, reach) { useTool(plr, gameObject, spot, tool) }
        }
    }
}

// Loading a Karambwan vessel.
useItem(Fish.KARAMBWANJI.id).onItem(Tool.EMPTY_KARAMBWAN_VESSEL) {
    plr.inventory.remove(Item(Fish.KARAMBWANJI.id))
    plr.inventory.remove(Item(Tool.EMPTY_KARAMBWAN_VESSEL))
    plr.inventory.add(Item(Tool.KARAMBWAN_VESSEL.id))
    plr.sendMessage("You load the Karambwan vessel with the raw Karambwanji.")
}

// The Hemenster spots are only for the Fishing Contest.
for (id in 233..236) {
    npc1(id, false) {
        // TODO Fishing Contest: competitors fish here, and Bonzo assigns them a spot.
        if (world.locator.findNpcs(plr, 12) { it.id == 225 }.isNotEmpty()) {
            plr.newDialogue().npc(225, Expression.HAPPY, "Hey, you need to pay to enter the competition first!",
                                  "Only 5 gp entrance fee!").open()
            plr.sendMessage("Talk to Bonzo to pay the entrance fee.")
        }
    }
}
