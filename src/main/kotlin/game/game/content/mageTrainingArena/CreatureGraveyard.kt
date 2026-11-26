package game.content.mageTrainingArena

import api.predef.*
import api.predef.ext.*
import game.content.mageTrainingArena.MageTrainingArena.Points
import game.content.mageTrainingArena.MageTrainingArena.arenaRoom
import game.content.mageTrainingArena.MageTrainingArena.bonesToPeachesUnlocked
import game.player.Animations
import game.player.Sound
import game.skill.magic.Magic
import game.skill.magic.Magic.castDelay
import game.skill.magic.bonesToItems.BonesToItemsType
import io.luna.game.action.impl.QueuedAction
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.block.Hit.HitType

/**
 * The Creature Graveyard: bones from the piles are turned into fruit with Bones to Bananas (or Bones to Peaches) and
 * fed down the chutes in the walls, while falling bones wear the player down.
 *
 * @author TheLining
 */
object CreatureGraveyard : ArenaRoom("Creature Graveyard", 10781, 15, 15931, Position(3365, 3318)) {

    /**
     * The bones worth one to four fruit each.
     */
    val BONES = listOf(6904, 6905, 6906, 6907)

    /**
     * The bone piles, in the order of the bones they start on.
     */
    val PILES = listOf(10725, 10726, 10727, 10728)
    const val CHUTE = 10735

    const val BANANA = 1963
    const val PEACH = 6883

    /**
     * Bones grabbed from a pile before it gives the next kind.
     */
    private const val BONES_PER_KIND = 4

    /**
     * Fruit deposited for each point, and the runes a point can come with.
     */
    internal const val FRUIT_PER_POINT = 16
    private val REWARD_RUNES = listOf(555, 561, 560, 565)

    /**
     * Damage from falling bones, and the ticks between hits.
     */
    private const val FALL_DAMAGE = 2
    private const val FALL_TICKS = 10

    /**
     * Graveyard Pizazz Points lost on dying.
     */
    const val DEATH_PENALTY = 10

    private const val POINTS_LINE = 15935

    private val START = Position(3363, 9639, 1)

    /**
     * Each player's grabs from each pile tile, fruit towards the next point, and ticks to the next falling bone.
     */
    private val grabs = HashMap<Player, HashMap<Position, Int>>()
    private val fruit = HashMap<Player, Int>()
    private val fallTimer = HashMap<Player, Int>()

    override val roomItems = BONES.toSet() + BANANA + PEACH

    override fun inside(position: Position) = position.z == 1 && position.x in 3335..3392 && position.y in 9612..9663

    override fun canEnter(plr: Player): Boolean {
        if (plr.inventory.containsAny(BANANA, PEACH)) {
            plr.sendMessage("You can't take bananas or peaches into the Creature Graveyard.")
            return false
        }
        return true
    }

    override fun enter(plr: Player) {
        grabs[plr] = HashMap()
        fruit[plr] = 0
        fallTimer[plr] = FALL_TICKS
        plr.move(START)
    }

    override fun leave(plr: Player, throughPortal: Boolean) {
        grabs.remove(plr)
        fruit.remove(plr)
        fallTimer.remove(plr)
    }

    override fun refreshOverlay(plr: Player) {
        plr.sendText(MageTrainingArena.points(plr, Points.GRAVEYARD), POINTS_LINE)
    }

    override fun process(plr: Player) {
        val left = (fallTimer[plr] ?: return) - 1
        if (left > 0) {
            fallTimer[plr] = left
            return
        }
        fallTimer[plr] = FALL_TICKS
        if (plr.isAlive && !plr.overlays.hasWindow()) plr.damage(FALL_DAMAGE, HitType.NORMAL)
    }

    /**
     * Grabbing a bone from the pile at [pos], whose first kind is set by [pile]. Every fourth grab moves the pile on
     * to the next kind.
     */
    fun grab(plr: Player, pile: Int, pos: Position) {
        val counts = grabs[plr] ?: return
        val first = PILES.indexOf(pile)
        if (first == -1) return
        if (!plr.inventory.hasSpaceFor(Item(BONES[0]))) {
            plr.sendMessage("You don't have enough inventory space.")
            return
        }
        val taken = counts[pos] ?: 0
        counts[pos] = taken + 1
        val kind = (first + taken / BONES_PER_KIND) % BONES.size
        plr.animation(Animation(832))
        plr.inventory.add(Item(BONES[kind]))
    }

    /**
     * Feeding every banana and peach down a chute: a point, 25 Magic experience and a rune for each sixteen.
     */
    fun deposit(plr: Player) {
        if (plr.arenaRoom !== this) return
        val count = plr.inventory.computeAmountForId(BANANA) + plr.inventory.computeAmountForId(PEACH)
        if (count == 0) {
            plr.sendMessage("You don't have any fruit to deposit.")
            return
        }
        for (index in 0 until plr.inventory.capacity()) {
            val id = plr.inventory.computeIdForIndex(index)
            if (id == BANANA || id == PEACH) plr.inventory[index] = null
        }
        var total = (fruit[plr] ?: 0) + count
        var points = 0
        while (total >= FRUIT_PER_POINT) {
            total -= FRUIT_PER_POINT
            points++
            plr.magic.addExperience(25.0)
            val rune = Item(REWARD_RUNES.random())
            if (!plr.inventory.add(rune)) plr.bank.add(rune)
        }
        fruit[plr] = total
        if (points > 0) MageTrainingArena.addPoints(plr, Points.GRAVEYARD, points)
        plr.sendMessage("You deposit $count ${if (count == 1) "piece" else "pieces"} of fruit.")
    }

    /**
     * Dying in the graveyard costs ten Graveyard Pizazz Points.
     */
    fun died(plr: Player) {
        MageTrainingArena.removePoints(plr, Points.GRAVEYARD, DEATH_PENALTY)
    }

    override fun event(plr: Player, event: ControllableEvent): Boolean {
        if (event !is ButtonClickEvent) return true
        val type = BonesToItemsType.values().firstOrNull { it.spellId == event.id } ?: return true
        if (type == BonesToItemsType.PEACHES && !plr.bonesToPeachesUnlocked) return true
        if (BONES.none { plr.inventory.contains(it) }) return true
        plr.submitAction(Convert(plr, type))
        return false
    }

    /**
     * Bones to Bananas or Peaches on the graveyard's bones: each becomes as many fruit as it is worth, as far as the
     * inventory has room.
     */
    private class Convert(plr: Player, private val type: BonesToItemsType) :
        QueuedAction<Player>(plr, plr.castDelay, 3) {

        override fun execute() {
            if (mob.arenaRoom !== CreatureGraveyard) return
            val runes = type.requirements.filterNot { it is game.skill.magic.ItemRequirement }
            val costs = Magic.checkRequirements(mob, type.level, runes) ?: return
            val made = ArrayList<Int>()
            for (index in 0 until mob.inventory.capacity()) {
                val kind = BONES.indexOf(mob.inventory.computeIdForIndex(index))
                if (kind == -1) continue
                mob.inventory[index] = Item(type.id)
                made += kind
            }
            if (made.isEmpty()) return
            mob.inventory.removeAll(costs)
            for (kind in made) {
                repeat(kind) { if (!mob.inventory.add(Item(type.id))) return@repeat }
            }
            mob.animation(Animations.BONES_TO_ITEMS)
            mob.graphic(Graphic(141, 100))
            mob.playSound(Sound.BONES_TO_BANANAS_ALL)
            mob.magic.addExperience(type.xp)
        }
    }
}
