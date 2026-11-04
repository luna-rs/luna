package game.obj.resource

import api.predef.*
import engine.spawn.PersistentGroundItem
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic

/**
 * World-spawned Zamorak wine theft, using Lost City's burn, drain and temple guardian retaliation rules.
 * Every persistent wine source uses the same interaction, including Wilderness and future sources;
 * nearby living Zamorak monks and chaos druids prevent collection until defeated.
 * Player drops and Telekinetic Grab bypass theft.
 *
 * @author lare96
 */
object ZamorakWine {

    /**
     * Searches the wine's surroundings on its own plane, covering the Falador temple's full monk group.
     * The same radius is used for other wine sources without requiring per-temple coordinates.
     */
    private const val GUARDIAN_RADIUS = 10

    /**
     * Attackable Zamorak monks, chaos druids and chaos druid warriors from Luna's revision-377 NPC data.
     */
    private val guardianIds = setOf(181, 188, 189, 190, 193, 1044, 1045, 1046)

    /**
     * Applies a world wine source's theft penalty and returns whether it prevents this pickup.
     * Ordinary drops, noted wine and expired ground items are left to normal pickup handling.
     */
    fun onPickup(plr: Player, groundItem: GroundItem): Boolean {
        if (groundItem.id != 245 || groundItem !is PersistentGroundItem ||
            groundItem.state != EntityState.ACTIVE || !plr.isAlive) {
            return false
        }
        plr.sendMessage("STOP STEALING MY WINE! GAH!")
        plr.graphic(Graphic(78))
        plr.playSound(Sound.FLAMES_OF_ZAMORAK)
        plr.damage(plr.hitpoints.level / 20 + 1)
        for (skill in listOf(plr.attack, plr.strength, plr.defence, plr.ranged, plr.magic)) {
            skill.level -= 1 + skill.staticLevel * 5 / 100
        }
        val guardians = world.locator.findNpcs(groundItem.position, GUARDIAN_RADIUS) {
            it.id in guardianIds && it.isAlive
        }
        guardians.firstOrNull()?.speak("Hands off Zamorak's wine!")
        for (guardian in guardians) {
            if (guardian.combat.target !is Player) {
                guardian.combat.attack(plr)
            }
        }
        return guardians.isNotEmpty() || !plr.isAlive
    }
}
