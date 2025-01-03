package world.minigame.partyRoom

import api.predef.*
import com.google.common.collect.ImmutableList
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.`object`.GameObject
import world.minigame.partyRoom.dropParty.DropPartyOption

/**
 * Manages functions and data for the party room minigame.
 */
object PartyRoom {

    /**
     * The list of registered lever options. Maximum of 5, minimum of 2.
     */
    private val LEVER_OPTIONS: ImmutableList<PartyRoomOption> = ImmutableList.of(
            DancingDrunksOption,
            FightingWomenOption,
            DropPartyOption
    )

    /**
     * The party room teleport positions.
     */
    val TELEPORT_POSITIONS = listOf(
            Position(2736, 3475, 0),
            Position(2737, 3475, 0)
    )

    /**
     * The active [PartyRoomOption], possibly null.
     */
    var option: PartyRoomOption? = null

    /**
     * If this party room is locked from being able to be started by regular players.
     */
    var locked = false

    /**
     * Display the party room lever options.
     */
    fun pullLever(plr: Player, obj: GameObject?) {
        if (locked && plr.rights < RIGHTS_ADMIN) {
            plr.sendMessage("The lever has been locked by an administrator.")
            return
        }
        if (option != null) {
            plr.sendMessage("Event \"${option!!.description}\" is still running! Please try again later.")
            return
        }
        val optionList = LEVER_OPTIONS.map {
            DialogueOption(it.description) { plr -> setLeverOption(plr, it, obj) }
        }.toList()
        plr.newDialogue().options(optionList).open()
    }

    /**
     * Resets the lever status to null.
     */
    fun resetLeverOption() {
        option = null
    }

    /**
     * Sets a new event option to this lever. Takes payment from the player and registers the option.
     */
    private fun setLeverOption(plr: Player, newOption: PartyRoomOption, obj: GameObject?) {
        val costItem = Item(995, newOption.cost)
        if (plr.rights <= RIGHTS_MOD && !plr.inventory.remove(costItem)) {
            plr.newDialogue().empty("You do not have enough coins to pull this lever.").open()
        } else if (newOption.canExecute(plr)) {
            if (obj != null) {
                plr.animation(Animation(2140))
            } else {
                plr.sendMessage("You have set the current party room lever option to [${newOption.description}].")
            }
            option = newOption
            newOption.execute(plr)
        }
    }
}
