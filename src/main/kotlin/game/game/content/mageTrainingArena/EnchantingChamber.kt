package game.content.mageTrainingArena

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.mageTrainingArena.MageTrainingArena.Points
import game.content.mageTrainingArena.MageTrainingArena.arenaRoom
import game.skill.magic.Magic
import game.skill.magic.Magic.castDelay
import game.skill.magic.enchantJewellery.EnchantJewelleryType
import io.luna.game.action.impl.QueuedAction
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.UseSpellEvent.MagicOnItemEvent
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.net.msg.out.WidgetVisibilityMessageWriter

/**
 * The Enchanting Chamber: shapes from the piles in the corners are enchanted into orbs and dropped in the hole in the
 * middle. Every tenth shape pays Enchantment Pizazz Points by the level of the spell, the shape the guardian calls out
 * pays a bonus point, and the dragonstones about the floor pay double the spell's level.
 *
 * @author TheLining
 */
object EnchantingChamber : ArenaRoom("Enchanting Chamber", 10779, 7, 15917, Position(3361, 3318)) {

    /**
     * A shape, the pile it comes from, its overlay picture and its name as the guardian calls it.
     */
    enum class Shape(val item: Int, val pile: Int, val layer: Int, val shout: String) {
        CUBE(6899, 10799, 15922, "cube"),
        CYLINDER(6898, 10800, 15923, "cylinder"),
        PENTAMID(6901, 10802, 15924, "pentamid"),
        ICOSAHEDRON(6900, 10801, 15925, "icosahedron")
    }

    const val ORB = 6902
    const val DRAGONSTONE = 6903
    const val ORB_HOLE = 10803

    private const val POINTS_LINE = 15921

    /**
     * Ticks between changes of the bonus shape (about 24 seconds).
     */
    private const val BONUS_TICKS = 40

    /**
     * Ticks before a picked-up dragonstone appears again (five minutes).
     */
    private const val DRAGONSTONE_TICKS = 500

    /**
     * Where the dragonstones lie.
     */
    private val DRAGONSTONE_SPOTS = listOf(Position(3354, 9645), Position(3353, 9635), Position(3359, 9632),
                                           Position(3375, 9633), Position(3374, 9643), Position(3373, 9651))

    /**
     * Orbs deposited for each reward of three runes, and the runes it can be.
     */
    private const val ORBS_PER_REWARD = 20
    private val REWARD_RUNES = listOf(564, 565, 560)

    private val START = Position(3363, 9649, 0)

    /**
     * Shapes enchanted towards the next tenth.
     */
    var Player.shapesEnchanted by Attr.int().persist("mta_shapes_enchanted")

    internal var bonus = Shape.values().random()
        private set
    private var timer = 0

    /**
     * Each player's orbs towards the next rune reward, and their dragonstones and when each spot fills again.
     */
    private val orbs = HashMap<Player, Int>()
    private val stones = HashMap<Player, Array<GroundItem?>>()
    private val regrow = HashMap<Player, IntArray>()

    override val roomItems = Shape.values().map { it.item }.toSet() + ORB + DRAGONSTONE

    override fun inside(position: Position) = position.z == 0 && position.x in 3335..3392 && position.y in 9612..9663

    override fun enter(plr: Player) {
        orbs[plr] = 0
        stones[plr] = arrayOfNulls(DRAGONSTONE_SPOTS.size)
        regrow[plr] = IntArray(DRAGONSTONE_SPOTS.size)
        plr.move(START)
    }

    override fun leave(plr: Player, throughPortal: Boolean) {
        orbs.remove(plr)
        regrow.remove(plr)
        stones.remove(plr)?.forEach { if (it != null && it.state == EntityState.ACTIVE) world.items.unregister(it) }
    }

    override fun refreshOverlay(plr: Player) {
        plr.sendText(MageTrainingArena.points(plr, Points.ENCHANTMENT), POINTS_LINE)
        Shape.values().forEach { plr.queue(WidgetVisibilityMessageWriter(it.layer, it != bonus)) }
    }

    override fun process(plr: Player) {
        val placed = stones[plr] ?: return
        val timers = regrow[plr] ?: return
        for (index in DRAGONSTONE_SPOTS.indices) {
            val stone = placed[index]
            if (stone != null && stone.state == EntityState.ACTIVE) continue
            if (stone != null) {
                placed[index] = null
                timers[index] = DRAGONSTONE_TICKS
            }
            if (timers[index] > 0) {
                timers[index]--
                continue
            }
            val item = GroundItem(ctx, DRAGONSTONE, 1, DRAGONSTONE_SPOTS[index], ChunkUpdatableView.localView(plr))
            item.setExpire(false)
            if (world.items.register(item)) placed[index] = item
        }
    }

    /**
     * Taking a shape from a pile.
     */
    fun takeShape(plr: Player, pile: Int) {
        if (plr.arenaRoom !== this) return
        val shape = Shape.values().firstOrNull { it.pile == pile } ?: return
        if (!plr.inventory.hasSpaceFor(Item(shape.item))) {
            plr.sendMessage("You don't have enough inventory space.")
            return
        }
        plr.animation(Animation(832))
        plr.inventory.add(Item(shape.item))
    }

    /**
     * Dropping every orb into the hole: three cosmic, blood or death runes for each twenty.
     */
    fun deposit(plr: Player) {
        if (plr.arenaRoom !== this) return
        val count = plr.inventory.computeAmountForId(ORB)
        if (count == 0) {
            plr.sendMessage("You don't have any orbs to deposit.")
            return
        }
        repeat(count) { plr.inventory.remove(Item(ORB)) }
        var total = (orbs[plr] ?: 0) + count
        while (total >= ORBS_PER_REWARD) {
            total -= ORBS_PER_REWARD
            val runes = Item(REWARD_RUNES.random(), 3)
            if (!plr.inventory.add(runes)) plr.bank.add(runes)
            plr.sendMessage("You receive ${runes.amount} ${itemName(runes.id).lowercase()}s.")
        }
        orbs[plr] = total
    }

    override fun event(plr: Player, event: ControllableEvent): Boolean {
        if (event !is MagicOnItemEvent) return true
        val type = EnchantJewelleryType.ALL.firstOrNull { it.spellId == event.spellId } ?: return true
        val id = plr.inventory.computeIdForIndex(event.targetItemIndex)
        if (id != DRAGONSTONE && Shape.values().none { it.item == id }) return true
        plr.submitAction(Enchant(plr, type, event.targetItemIndex))
        return false
    }

    /**
     * An enchantment spell cast on a shape or dragonstone, for three quarters of the spell's usual experience.
     */
    private class Enchant(plr: Player, private val type: EnchantJewelleryType, private val index: Int) :
        QueuedAction<Player>(plr, plr.castDelay, 3) {

        override fun execute() {
            if (mob.arenaRoom !== EnchantingChamber) return
            val id = mob.inventory.computeIdForIndex(index)
            val shape = Shape.values().firstOrNull { it.item == id }
            if (shape == null && id != DRAGONSTONE) return
            val costs = Magic.checkRequirements(mob, type.level, type.requirements) ?: return
            mob.inventory[index] = Item(ORB)
            mob.inventory.removeAll(costs)
            mob.magic.addExperience(type.xp * 0.75)
            val look = type.enchantMap.values.firstOrNull { it.animation.id != 931 } ?: type.enchantMap.values.first()
            mob.animation(look.animation)
            mob.graphic(look.graphic)
            mob.playSound(look.sound)
            mob.tabs.show(TabIndex.MAGIC)

            val spellLevel = type.ordinal + 1
            if (shape == null) {
                MageTrainingArena.addPoints(mob, Points.ENCHANTMENT, spellLevel * 2)
                return
            }
            var points = if (shape == bonus) 1 else 0
            mob.shapesEnchanted++
            if (mob.shapesEnchanted >= 10) {
                mob.shapesEnchanted = 0
                points += spellLevel
            }
            MageTrainingArena.addPoints(mob, Points.ENCHANTMENT, points)
        }
    }

    /**
     * Runs once a tick: every so often the guardian calls out a different bonus shape.
     */
    fun tick() {
        if (++timer < BONUS_TICKS) return
        timer = 0
        bonus = Shape.values().filter { it != bonus }.random()
        guardian()?.speak("The bonus shape has changed to the ${bonus.shout}.")
        world.players.filter { it.arenaRoom === this }.forEach { refreshOverlay(it) }
    }

    private fun guardian(): Npc? = world.npcs.firstOrNull { it.id == MageTrainingArena.ENCHANTMENT_GUARDIAN }
}
