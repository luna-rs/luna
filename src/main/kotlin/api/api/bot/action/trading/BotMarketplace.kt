
package api.bot.action.trading

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.area.SimpleBoxArea

/**
 * Defines a marketplace where bots can gather to participate in trading activities.
 *
 * Each marketplace contains rectangular anchor areas that determine its possible trading positions.
 * Blocked positions are excluded, leaving only tiles that bots can potentially occupy.
 *
 * Marketplace positions are computed lazily and reused after initialization. Marketplaces without
 * configured anchor areas have no available positions until their anchors are implemented.
 *
 * @param anchors The areas containing potential trading positions.
 * @author lare96
 */
enum class BotMarketplace(anchors: List<SimpleBoxArea>) {

    // TODO Likelihood of going to a marketplace increases based on certain factors.
    // TODO Finish other marketplaces.

    /**
     * The default marketplace, containing several trading areas in Varrock.
     */
    VARROCK(anchors = listOf( // default
        SimpleBoxArea.of(3173, 3433, 3178, 3445),
        SimpleBoxArea.of(3180, 3433, 3184, 3447),
        SimpleBoxArea.of(3191, 3433, 3197, 3447),
        SimpleBoxArea.of(3173, 3448, 3190, 3454),
        SimpleBoxArea.of(3173, 3427, 3197, 3432)
    )),

    /**
     * A marketplace intended primarily for PKing and combat-related items.
     */
    EDGEVILLE(anchors = listOf()), // pking/combat items mainly

    /**
     * A marketplace intended primarily for woodcutting and fletching materials.
     */
    SEERS_VILLAGE(anchors = listOf()), // skilling equipment

    /**
     * A marketplace intended primarily for ores, smelted bars, and cooked food.
     */
    AL_KHARID(anchors = listOf());

    /**
     * The available trading positions within this marketplace.
     *
     * Collects positions from all configured anchor areas and excludes tiles blocked by the world's
     * collision manager. The result is computed lazily and cached for subsequent access.
     *
     * Marketplaces without anchors, or whose anchors contain no unblocked tiles, produce an empty list.
     */
    val positions by lazyVal {
        anchors.flatMap { it.computePositions() }.filter { !world.collisionManager.isBlocked(it, false) }.toList()
    }

    /**
     * Selects a random available position within this marketplace.
     *
     * Every position in [positions] has an equal chance of being selected.
     *
     * @return A randomly selected, unblocked marketplace position.
     * @throws NoSuchElementException If this marketplace contains no available positions.
     */
    fun randomPosition(): Position {
        return positions.random()
    }
}
