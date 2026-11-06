package api.bot.webwalk.generate

import api.bot.webwalk.model.ItemRequirement
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.NativeAgilityShortcutCrossing
import game.skill.agility.shortcut.AgilityShortcut
import game.skill.agility.shortcut.SpikyChains
import game.skill.agility.shortcut.SteppingStones
import game.skill.agility.TrollheimMountainRocks
import io.luna.game.model.Direction
import io.luna.game.model.Position

/**
 * Directed routes through the native open-world shortcuts, stone rows and Slayer Tower chains.
 * Source geometry supplies the actual click at each end, free exits and consumed ropes.
 *
 * @author lare96
 */
object NativeAgilityShortcutSpec {

    /**
     * One supported traversal, with the stones to click in order when it crosses a complete row.
     *
     * @author lare96
     */
    data class Passage(val objectId: Int, val position: Position, val from: Position, val to: Position,
                       val level: Int, val cost: Int, val maxDamage: Int = 0, val rope: Boolean = false,
                       val stones: List<Pair<Int, Position>> = emptyList(), val damagePercent: Int = 0) {
        val requirements: Requirements = Requirements(
            flags = if (level > 0) setOf(NativeAgilityShortcutCrossing.flag(level)) else emptySet(),
            items = if (rope) listOf(ItemRequirement(AgilityShortcut.ROPE)) else emptyList())

        val placement: CrossingPlacement = CrossingPlacement(objectId, position, from, to, requirements)

        /**
         * The native fall damage at the bot's current Hitpoints, including any proportional component.
         */
        fun fallDamage(health: Int): Int = maxDamage + health * damagePercent / 100
    }

    val PASSAGES: List<Passage> = AgilityShortcut.values().flatMap { shortcut ->
        (if (shortcut.oneWay) listOf(0) else listOf(0, 1)).map { end ->
            val from = shortcut.ends[end]
            val to = shortcut.ends[1 - end]
            val obj = shortcut.objects.minWithOrNull(compareBy(
                { if (it.second.z == from.z) 0 else 1 },
                { from.computeLongestDistance(it.second) }))!!
            Passage(obj.first, obj.second, from, to, shortcut.requiredLevel(from),
                cost(shortcut), damage(shortcut), shortcut.rope)
        }
    } + SteppingStones.ROWS.flatMap { row ->
        listOf(false, true).map { reverse ->
            val stones = if (reverse) row.stones.reversed() else row.stones
            val from = if (reverse) row.banks.second else row.banks.first
            val to = if (reverse) row.banks.first else row.banks.second
            Passage(stones.first().first, stones.first().second, from, to, row.level,
                row.stones.size * 3 + 12, maxDamage = 3, stones = stones)
        }
    } + SpikyChains.CHAINS.flatMap { chain ->
        listOf(false, true).flatMap { down ->
            val position = if (down) chain.top else chain.bottom
            listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST).map { side ->
                val from = position.translate(1, side)
                Passage(if (down) SpikyChains.DOWN else SpikyChains.UP, position, from,
                    from.translate(0, 0, if (down) -1 else 1), chain.level, cost = 3, maxDamage = 5)
            }
        }
    } + TrollheimMountainRocks.PASSAGES.flatMap { passage ->
        listOf(passage.bottom to passage.top, passage.top to passage.bottom).map { (from, to) ->
            Passage(passage.objectId, passage.position, from, to, TrollheimMountainRocks.LEVEL,
                cost = 6, maxDamage = 1, damagePercent = 5)
        }
    }

    /**
     * A shared object id belongs to one spec, while each directed placement keeps its own admission rules.
     */
    val ALL: List<CrossingSpec> = PASSAGES.groupBy { it.objectId }.map { (id, passages) ->
        CrossingSpec(NativeAgilityShortcutCrossing.ID, setOf(id), bidirectional = false,
            cost = passages.maxOf { it.cost }, placements = passages.map { it.placement })
    }

    private fun cost(shortcut: AgilityShortcut): Int = when (shortcut) {
        AgilityShortcut.FALADOR_TUNNEL, AgilityShortcut.VARROCK_TUNNEL, AgilityShortcut.YANILLE_TUNNEL ->
            shortcut.ends[0].computeLongestDistance(shortcut.ends[1]) + 5
        AgilityShortcut.ARDOUGNE_LOG, AgilityShortcut.FREMENNIK_LOG -> 16
        AgilityShortcut.DWARVEN_MINE_CREVICE, AgilityShortcut.FREMENNIK_CREVICE -> 9
        AgilityShortcut.EDGEVILLE_PIPE, AgilityShortcut.EDGEVILLE_MONKEY_BARS -> 10
        AgilityShortcut.COSMIC_WALL_MEDIUM, AgilityShortcut.COSMIC_WALL_ADVANCED_NORTH,
        AgilityShortcut.COSMIC_WALL_ADVANCED_SOUTH -> 10
        AgilityShortcut.SWAMP_CAVES_STONE, AgilityShortcut.SWAMP_CAVES_FROG_STONE -> 10
        AgilityShortcut.OGRE_ISLAND_BRANCH -> 6
        else -> maxOf(4, shortcut.ends[0].computeLongestDistance(shortcut.ends[1]) + 1)
    }

    private fun damage(shortcut: AgilityShortcut): Int = when (shortcut) {
        AgilityShortcut.ARDOUGNE_LOG, AgilityShortcut.FREMENNIK_LOG -> 6
        AgilityShortcut.TROLLHEIM_EASY, AgilityShortcut.TROLLHEIM_MEDIUM,
        AgilityShortcut.TROLLHEIM_ADVANCED, AgilityShortcut.COSMIC_WALL_MEDIUM,
        AgilityShortcut.COSMIC_WALL_ADVANCED_NORTH, AgilityShortcut.COSMIC_WALL_ADVANCED_SOUTH -> 4
        AgilityShortcut.TROLLHEIM_HARD, AgilityShortcut.FREMENNIK_BLADES_EAST,
        AgilityShortcut.FREMENNIK_BLADES_WEST -> 3
        else -> 0
    }
}
