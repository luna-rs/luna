package game.content.mortton

import api.predef.ext.addObject
import api.predef.ext.animation
import api.predef.ext.removeObject
import api.predef.ext.scheduleOnce
import api.predef.firemaking
import api.predef.prayer
import game.player.Animations
import game.player.Sound
import game.skill.firemaking.Firemaking
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import java.util.WeakHashMap

/**
 * Mort'ton pyre preparation and cremation. Inputs stay in the owner's inventory until the burn succeeds.
 *
 * @author lare96
 */
object ShadePyres {
    const val EMPTY = 4093
    private const val EMPTY_VIAL = 229
    private const val SETUP_TICKS = 49
    private const val PYRE_FIRE = 292
    private val pyres = WeakHashMap<World, MutableMap<Position, Funeral>>()

    /**
     * One temporary pyre and the player allowed to light it.
     *
     * @author lare96
     */
    private class Funeral(val owner: Player, val base: GameObject, val logs: PyreLogs) {
        var current = base
        var remains: Int? = null
    }

    fun makeLogs(player: Player, logs: PyreLogs, oilSlot: Int, woodSlot: Int, oil: Int) {
        val oilIndex = FlamtaerTemple.sacredOils.indexOf(oil)
        val doses = 4 - oilIndex
        if (oilIndex < 0 || player.inventory[oilSlot]?.id != oil || player.inventory[woodSlot]?.id != logs.wood) return
        if (doses < logs.doses) {
            player.sendMessage("You need ${logs.doses} doses of sacred oil for those logs.")
            return
        }
        player.inventory[woodSlot] = Item(logs.item)
        val unused = doses - logs.doses
        player.inventory[oilSlot] = Item(if (unused == 0) EMPTY_VIAL else FlamtaerTemple.sacredOils[4 - unused])
        player.firemaking.addExperience(logs.mixXp)
        player.sendMessage("You pour sacred oil onto the logs.")
    }

    fun load(player: Player, pyre: GameObject, logs: PyreLogs) {
        if (pyre.state != EntityState.ACTIVE || !player.inventory.contains(logs.item)) return
        if (player.firemaking.level < logs.level) {
            player.sendMessage("You need level ${logs.level} Firemaking to use those pyre logs.")
            return
        }
        val active = pyres.getOrPut(player.world) { hashMapOf() }
        if (active.values.any { it.owner === player }) {
            player.sendMessage("You already have a funeral pyre prepared.")
            return
        }
        val funeral = Funeral(player, pyre, logs)
        active[pyre.position] = funeral
        funeral.current = change(pyre, logs.loadedObject)
        player.animation(Animations.PICKPOCKET)
        player.sendMessage("You place the logs on the pyre.")
        player.world.scheduleOnce(SETUP_TICKS) { clear(funeral) }
    }

    fun place(player: Player, pyre: GameObject, remains: Int) {
        val funeral = owned(player, pyre) ?: return
        if (funeral.remains != null || remains !in ShadeRewards.remains || !player.inventory.contains(remains)) return
        funeral.remains = remains
        funeral.current = change(pyre, funeral.logs.readyObject)
        player.animation(Animations.PICKPOCKET)
        player.sendMessage("You place the shade's remains on the pyre.")
    }

    fun light(player: Player, pyre: GameObject) {
        val funeral = owned(player, pyre) ?: return
        val remains = funeral.remains ?: return
        val inputs = listOf(Item(funeral.logs.item), Item(remains))
        if (!player.inventory.contains(Firemaking.TINDERBOX) || !player.inventory.containsAll(inputs)) {
            player.sendMessage("You need a tinderbox, the pyre logs and the shade remains to finish the funeral.")
            return
        }
        player.submitAction(object : LockedAction(player, false, 3) {
            override fun onLock() {
                mob.animation(Animations.FIREMAKING)
            }

            override fun run(): Boolean {
                if (owned(mob, pyre) != funeral || mob.firemaking.level < funeral.logs.level ||
                    !mob.inventory.contains(Firemaking.TINDERBOX) || !mob.inventory.containsAll(inputs) ||
                    !mob.inventory.removeAll(inputs)) return true
                clear(funeral)
                mob.firemaking.addExperience(funeral.logs.burnXp)
                mob.prayer.addExperience((if (remains == ShadeRewards.LOAR_REMAINS) 25.0 else 37.5) +
                    funeral.logs.prayerBonus)
                LocalGraphic(mob.context, PYRE_FIRE, 0, 0, pyre.position, ChunkUpdatableView.globalView()).display()
                mob.playSound(Sound.FIRE_LIT)
                val reward = ShadeRewards.cremation(remains)
                mob.giveItem(reward)
                mob.sendMessage("The shade is laid to rest. You recover ${if (reward.id in ShadeRewards.keys) "a shade key" else "some coins"}.")
                return true
            }
        })
    }

    private fun owned(player: Player, pyre: GameObject): Funeral? =
        pyres[player.world]?.get(pyre.position)?.takeIf {
            it.owner === player && it.current === pyre && pyre.state == EntityState.ACTIVE
        }

    private fun change(pyre: GameObject, id: Int): GameObject =
        pyre.world.addObject(id, pyre.position, pyre.objectType, pyre.direction)

    private fun clear(funeral: Funeral) {
        val world = funeral.owner.world
        val active = pyres[world] ?: return
        if (!active.remove(funeral.base.position, funeral)) return
        if (active.isEmpty()) pyres.remove(world)
        if (funeral.current.state == EntityState.ACTIVE && world.removeObject(funeral.current)) {
            val base = funeral.base
            world.addObject(base.id, base.position, base.objectType, base.direction)
        }
    }
}
