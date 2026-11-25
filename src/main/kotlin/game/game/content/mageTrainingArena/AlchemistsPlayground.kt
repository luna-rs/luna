package game.content.mageTrainingArena

import api.predef.*
import api.predef.ext.*
import game.content.mageTrainingArena.MageTrainingArena.Points
import game.content.mageTrainingArena.MageTrainingArena.arenaRoom
import game.player.Animations
import game.player.Sound
import game.skill.magic.Magic
import game.skill.magic.Magic.castDelay
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.action.impl.QueuedAction
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.UseSpellEvent.MagicOnItemEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.net.msg.out.WidgetVisibilityMessageWriter

/**
 * The Alchemists' Playground: eight cupboards hold five items in a fixed clockwise order that shifts round from time
 * to time, as do the coins each item alchemises into. Coins go in the Coin Collector for Alchemist Pizazz Points.
 *
 * @author TheLining
 */
object AlchemistsPlayground : ArenaRoom("Alchemists' Playground", 10780, 21, 15892, Position(3363, 3320)) {

    private const val COINS = 995

    /**
     * The five items, in their clockwise order in the cupboards.
     */
    val ITEMS = listOf(6893, 6894, 6895, 6896, 6897)

    /**
     * What the Alchemy Guardian calls each item when it can be converted for free.
     */
    private val SHOUT_NAMES = listOf("leather boots are", "adamant kite shield is", "adamant medium helm is",
                                     "emerald is", "rune long sword is")

    /**
     * The eight cupboards in clockwise order: up the west wall, then down the east wall.
     */
    val CUPBOARDS = listOf(10783, 10785, 10787, 10789, 10791, 10793, 10795, 10797)

    /**
     * The coins an item can be worth; each item takes a different one.
     */
    private val VALUES = listOf(1, 5, 8, 15, 30)

    /**
     * Ticks between changes of cupboard contents and costs (about 42 seconds).
     */
    private const val CHANGE_TICKS = 70

    /**
     * The coins every 100 deposited earns back, and the most that can be taken out of the room.
     */
    private const val REWARD_PER_HUNDRED = 10
    private const val MAX_REWARD = 1000

    private const val POINTS_LINE = 15896
    private val VALUE_LINES = listOf(15902, 15903, 15904, 15905, 15906)
    private val ARROW_LAYERS = listOf(15907, 15908, 15909, 15910, 15911)

    private val START = Position(3366, 9623, 2)

    /**
     * How far round the cupboards the items have moved.
     */
    private var rotation = 0

    /**
     * The coins each item is worth now, by its place in [ITEMS].
     */
    private var values = VALUES.shuffled()

    /**
     * The item that can be converted without runes, or -1.
     */
    private var freeItem = -1

    private var timer = 0

    /**
     * Coins each player has made in the room and not yet deposited, and the coins deposits have earned them.
     */
    private val created = HashMap<Player, Int>()
    private val earned = HashMap<Player, Int>()

    override val roomItems = ITEMS.toSet()

    override fun inside(position: Position) = position.z == 2 && position.x in 3335..3392 && position.y in 9612..9663

    override fun canEnter(plr: Player): Boolean {
        if (plr.inventory.contains(COINS)) {
            plr.sendMessage("You can't take coins into the Alchemists' Playground.")
            return false
        }
        return true
    }

    override fun enter(plr: Player) {
        created[plr] = 0
        earned[plr] = 0
        plr.move(START)
    }

    override fun leave(plr: Player, throughPortal: Boolean) {
        created.remove(plr)
        val reward = earned.remove(plr) ?: 0
        val coins = plr.inventory.computeAmountForId(COINS)
        if (coins > 0) plr.inventory.remove(Item(COINS, coins))
        if (reward > 0) {
            val payment = Item(COINS, reward)
            if (plr.bank.hasSpaceFor(payment)) plr.bank.add(payment) else plr.inventory.add(payment)
            plr.sendMessage("$reward coins have been added to your bank for your efforts.")
        }
    }

    override fun refreshOverlay(plr: Player) {
        plr.sendText(MageTrainingArena.points(plr, Points.ALCHEMIST), POINTS_LINE)
        for (index in ITEMS.indices) {
            plr.sendText(values[index], VALUE_LINES[index])
            plr.queue(WidgetVisibilityMessageWriter(ARROW_LAYERS[index], index != freeItem))
        }
    }

    /**
     * The item in [cupboard] now, or `null` if it is empty.
     */
    fun itemIn(cupboard: Int): Int? {
        val place = CUPBOARDS.indexOf(cupboard)
        if (place == -1) return null
        return ITEMS.getOrNull(Math.floorMod(place - rotation, CUPBOARDS.size))
    }

    /**
     * Searching a cupboard.
     */
    fun search(plr: Player, cupboard: Int) {
        if (plr.arenaRoom !== this) return
        plr.animation(Animation(832))
        val item = itemIn(cupboard)
        if (item == null) {
            plr.sendMessage("The cupboard is empty.")
            return
        }
        if (!plr.inventory.add(Item(item))) plr.sendMessage("You don't have enough inventory space.")
    }

    /**
     * Putting coins in the Coin Collector: a point and ten coins back for every whole hundred, and two Magic
     * experience a coin.
     */
    fun deposit(plr: Player) {
        if (plr.arenaRoom !== this) return
        val coins = plr.inventory.computeAmountForId(COINS)
        if (coins == 0) {
            plr.sendMessage("You don't have any coins to deposit.")
            return
        }
        plr.inventory.remove(Item(COINS, coins))
        val made = created[plr] ?: 0
        val deposited = minOf(coins, made)
        created[plr] = 0
        if (deposited < coins) plr.sendMessage("You didn't create those coins from the items in this room!")
        if (deposited == 0) return
        val hundreds = deposited / 100
        plr.magic.addExperience(deposited * 2.0)
        MageTrainingArena.addPoints(plr, Points.ALCHEMIST, hundreds)
        earned[plr] = minOf(MAX_REWARD, (earned[plr] ?: 0) + hundreds * REWARD_PER_HUNDRED)
        plr.sendMessage("You deposit $deposited coins.")
    }

    override fun event(plr: Player, event: ControllableEvent): Boolean {
        when (event) {
            is MagicOnItemEvent -> {
                val type = AlchemyType.values().firstOrNull { it.spellId == event.spellId } ?: return true
                val id = plr.inventory.computeIdForIndex(event.targetItemIndex)
                if (id !in ITEMS) {
                    plr.sendMessage("You can only convert the items from the cupboards in here.")
                    return false
                }
                plr.submitAction(Convert(plr, type, event.targetItemIndex))
                return false
            }

            is EquipItemEvent -> if (event.itemId in ITEMS) {
                plr.sendMessage("You can't wear that in here.")
                return false
            }

            is DropItemEvent -> if (plr.inventory.computeIdForIndex(event.index) == COINS) {
                plr.sendMessage("You should deposit your coins in the Coin Collector.")
                return false
            }
        }
        return true
    }

    /**
     * Low or high alchemy on a cupboard item: the same coins from either, at today's price, and no runes for the
     * item the arrow points at.
     */
    private class Convert(plr: Player, private val type: AlchemyType, private val index: Int) :
        QueuedAction<Player>(plr, plr.castDelay, 5) {

        override fun execute() {
            val id = mob.inventory.computeIdForIndex(index)
            val place = ITEMS.indexOf(id)
            if (place == -1 || mob.arenaRoom !== AlchemistsPlayground) return
            val costs = if (place == freeItem) {
                if (mob.magic.level < type.level) {
                    mob.sendMessage("Your Magic level is not high enough for this spell.")
                    return
                }
                emptyList()
            } else {
                Magic.checkRequirements(mob, type.level, type.requirements) ?: return
            }
            val coins = values[place]
            if (!mob.inventory.remove(index, Item(id))) return
            mob.inventory.removeAll(costs)
            mob.inventory.add(Item(COINS, coins))
            created[mob] = (created[mob] ?: 0) + coins
            mob.magic.addExperience(type.xp)
            val low = type == AlchemyType.LOW
            mob.animation(if (low) Animations.LOW_ALCHEMY else Animations.HIGH_ALCHEMY)
            mob.graphic(if (low) Graphic(112, 75) else Graphic(113, 75))
            mob.playSound(if (low) Sound.LOW_ALCHEMY else Sound.HIGH_ALCHEMY)
            mob.tabs.show(TabIndex.MAGIC)
        }
    }

    /**
     * Runs once a tick: the guardian warns of the change, then the items shift one cupboard round and take new costs,
     * every other time with one of them free to convert.
     */
    fun tick() {
        timer++
        if (timer == CHANGE_TICKS - 2) guardian()?.speak("The costs are changing!")
        if (timer < CHANGE_TICKS) return
        timer = 0
        rotation = (rotation + 1) % CUPBOARDS.size
        values = VALUES.shuffled()
        freeItem = if (freeItem == -1) ITEMS.indices.random() else -1
        if (freeItem != -1) guardian()?.speak("The ${SHOUT_NAMES[freeItem]} free to convert!")
        world.players.filter { it.arenaRoom === this }.forEach { refreshOverlay(it) }
    }

    private fun guardian(): Npc? = world.npcs.firstOrNull { it.id == MageTrainingArena.ALCHEMY_GUARDIAN }
}
