package world.player.skill.magic

import api.attr.Attr
import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.util.StringUtils
import world.player.skill.magic.teleportSpells.TeleportAction
import world.player.skill.magic.teleportSpells.TeleportSpell


object Magic {

    /**
     * Seconds that must be waited in-between teleother requests to the same person.
     */
    const val TELEOTHER_DELAY_SECONDS = 30

    /**
     * An attribute representing all recent teleother requests sent by the player.
     */
    val Player.teleOtherRequests by Attr.map<Long, Long>()

    // todo check teleport for controllers for stuff like minigames and wild?? or wild level?
    // TODO documentation, testing, finish reqs, should also add xp once runes are taken. should return list of items to remove
    // empty list means req failed
    fun checkRequirements(plr: Player, level: Int, requirements: List<SpellRequirement>): Boolean {
        if (plr.magic.level < level) {
            plr.sendMessage("Your Magic level is not high enough for this spell.")
            return false
        }
        val runesNeeded = HashMap<Rune, Int>()
        val removeItems = ArrayList<Item>(4)
        for (req in requirements) {
            if(req is RuneRequirement) {
                runesNeeded[req.rune] = req.amount
            } else if(req is ItemRequirement) {
                if(!plr.inventory.contains(req.id, req.amount)) {
                    plr.sendMessage("You do not have enough ${itemName(req.id)}s to cast this spell.")
                    return false
                }
                removeItems += Item(req.id, req.amount)
            }
        }
        // TODO take items lol
       // magic.addExperience(spell.xp)

        plr.inventory.removeAll(removeItems)
        return true
    }

    /**
     * Function for helping [TeleportAction] move the player in a regular spellbook style.
     */
    internal fun regularStyle(action: TeleportAction): Boolean {
        val plr = action.mob
        return when (action.executions) {
            0 -> {
                plr.animation(Animation(714))
                true
            }

            1 -> {
                plr.graphic(Graphic(308, 50))
                true
            }

            2 -> true
            3 -> {
                plr.teleport(action.destination)
                plr.animation(Animation(715))
                false
            }

            else -> false
        }
    }

    /**
     * Function for helping [TeleportAction] move the player in ancient spellbook style.
     */
    internal fun ancientStyle(action: TeleportAction): Boolean {
        val plr = action.mob
        return when (action.executions) {
            0 -> {
                plr.animation(Animation(1979))
                true
            }

            1 -> {
                plr.graphic(Graphic(392))
                true
            }

            2 -> true
            3 -> true
            4 -> {
                plr.teleport(action.destination)
                false
            }

            else -> false
        }
    }

    fun Player.teleport(spell: TeleportSpell, checkLevel: Boolean = false, checkRequirements: Boolean = false) {
        submitAction(object : TeleportAction(this, if (checkLevel) spell.level else 1, spell.destination,
                                             spell.style, if (checkRequirements) spell.requirements else emptyList()) {
            override fun onTeleport() {
                sendMessage("You teleport to ${StringUtils.capitalize(spell.displayName)}.")
            }
        })
    }
}