package game.skill.magic.teleportSpells

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import game.skill.magic.Magic
import game.skill.magic.SpellRequirement
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Teleports a player through the native locked action. Teleblock, controller and spell requirements
 * are checked before locking, allowing a spellbook caller to offer a random event after validation
 * without spending runes or invoking teleport callbacks. Other teleport callers use the same checks.
 *
 * @author lare96
 */
open class TeleportAction(plr: Player,
                          val level: Int = 1,
                          val xp: Double = 0.0,
                          val destination: Position,
                          val style: TeleportSequence,
                          val requirements: List<SpellRequirement> = emptyList(),
                          val maxWildernessLevel: Int = MAX_WILDERNESS_LEVEL) : LockedAction(plr) {

    companion object {

        /**
         * The deepest Wilderness level that a teleport works from, unless it says otherwise.
         */
        const val MAX_WILDERNESS_LEVEL = 20

        /**
         * An attribute representing teleport throttling.
         */
        val Player.teleportDelay by Attr.timeSource()

        /**
         * The Karamjan rum item id. It breaks when the player teleports.
         */
        const val KARAMJAN_RUM = 431

        /**
         * The plague sample item id. It disintegrates when the player teleports.
         */
        const val PLAGUE_SAMPLE = 418

        /**
         * The waxed sled item id. It can't be held through a teleport.
         */
        const val WAXED_SLED = 4084
    }

    /**
     * Costs checked immediately before locking; validation and consumption run in the same submission.
     */
    private var preparedCosts: List<Item>? = null

    override fun beforeLock(): Boolean {
        preparedCosts = checkCast()
        return preparedCosts != null
    }

    override fun onLock() {
        val removeItems = preparedCosts ?: checkCast()
        if (removeItems == null) {
            complete()
            return
        }
        onTeleport()
        if (removeItems.isNotEmpty()) {
            mob.inventory.removeAll(removeItems)
        }
        if (xp > 0.0) {
            mob.magic.addExperience(xp)
        }
    }

    /**
     * Applies native teleport restrictions before costs are consumed or teleport side effects begin.
     */
    private fun checkCast(): List<Item>? {
        if (mob.status.isTeleBlocked()) {
            mob.sendMessage("A magical force stops you from teleporting.")
            return null
        }
        if (!mob.controllers.checkTeleport(this)) {
            return null
        }
        return Magic.checkRequirements(mob, level, requirements)
    }

    override fun run(): Boolean = !style.step(this)

    override fun onUnlock() {
         mob.teleportDelay.reset()
    }

    /**
     * Moves the player to [destination]. Called by the [style] on the landing tick. Nothing happens if the player
     * died during the teleport.
     */
    fun land() {
        if (!mob.isAlive) {
            return
        }
        removeSled()
        mob.move(destination)
        breakFragileItems()
        onLand()
    }

    /**
     * Invoked one tick before the teleportation starts. Send teleport messages, etc. here.
     */
    open fun onTeleport() {

    }

    /**
     * Invoked on the tick the player lands at [destination].
     */
    open fun onLand() {

    }

    /**
     * Takes a waxed sled out of the player's hands before they leave, dropping it if there's no room for it.
     */
    private fun removeSled() {
        if (mob.equipment[Equipment.WEAPON]?.id != WAXED_SLED) {
            return
        }
        if (mob.equipment.unequip(Equipment.WEAPON)) {
            mob.sendMessage("You remove the sled.")
        } else {
            mob.equipment[Equipment.WEAPON] = null
            world.addItem(WAXED_SLED, 1, mob.position)
            mob.sendMessage("You remove the sled. It drops to the ground as you have no space for it.")
        }
    }

    /**
     * Destroys the items that can't survive a teleport.
     */
    private fun breakFragileItems() {
        val rum = mob.inventory.computeAmountForId(KARAMJAN_RUM)
        if (rum > 0) {
            mob.inventory.remove(Item(KARAMJAN_RUM, rum))
            mob.sendMessage("Your Karamjan rum gets broken and spilled.")
        }
        val samples = mob.inventory.computeAmountForId(PLAGUE_SAMPLE)
        if (samples > 0) {
            mob.inventory.remove(Item(PLAGUE_SAMPLE, samples))
            mob.sendMessage("The plague sample is too delicate...it disintegrates in the crossing.")
        }
    }
}
