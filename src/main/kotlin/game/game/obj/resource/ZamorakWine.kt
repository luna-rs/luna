package game.obj.resource

import api.predef.*
import engine.spawn.PersistentGroundItem
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic

/**
 * World-spawned Zamorak wine theft, using Lost City's burn, drain and monk retaliation rules.
 * Every persistent wine source uses the same interaction, including Wilderness and future sources;
 * nearby living monks prevent collection until defeated. Player drops and Telekinetic Grab bypass theft.
 *
 * @author lare96
 */
object ZamorakWine {

    /**
     * Searches the wine's surroundings on its own plane, covering the Falador temple's full monk group.
     * The same radius is used for other wine sources without requiring per-temple coordinates.
     */
    private const val MONK_RADIUS = 10

    /**
     * All attackable Monk of Zamorak variants defined by Luna's revision-377 NPC data.
     */
    private val monkIds = setOf(188, 189, 190, 1044, 1045, 1046)

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
        val monks = world.locator.findNpcs(groundItem.position, MONK_RADIUS) {
            it.id in monkIds && it.isAlive
        }
        monks.firstOrNull()?.speak("Hands off Zamorak's wine!")
        for (monk in monks) {
            if (monk.combat.target !is Player) {
                monk.combat.attack(plr)
            }
        }
        return monks.isNotEmpty() || !plr.isAlive
    }
}
