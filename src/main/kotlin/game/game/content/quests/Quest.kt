package game.content.quests

import game.content.dragonSlayer.DragonSlayer
import game.content.lostCity.LostCity
import io.luna.game.model.mob.Player

/**
 * Quests that gate content elsewhere in the game, and how each one's completion is read.
 *
 * @author TheLining
 */
enum class Quest(private val completion: ((Player) -> Boolean)?) {
    DRAGON_SLAYER({ DragonSlayer.stage(it) == DragonSlayer.COMPLETE }),
    LOST_CITY({ LostCity.stage(it) == LostCity.COMPLETE }),

    // TODO Heroes' Quest: read its completion once the quest exists.
    HEROES_QUEST(null),

    // TODO Legends' Quest: read its completion once the quest exists.
    LEGENDS_QUEST(null),

    // TODO Mourning's End Part I: read whether it has been started once the quest exists.
    MOURNINGS_END_PART_I(null),

    // TODO Regicide: read its completion once the quest exists.
    REGICIDE(null),

    // TODO Roving Elves: read its completion once the quest exists.
    ROVING_ELVES(null),

    // TODO The Fremennik Trials: read its completion once the quest exists.
    FREMENNIK_TRIALS(null),

    // TODO Icthlarin's Little Helper: read its completion once the quest exists.
    ICTHLARINS_LITTLE_HELPER(null),

    // TODO Rune Mysteries: read its completion once the quest exists.
    RUNE_MYSTERIES(null),

    // TODO The Feud: read its completion once the quest exists.
    THE_FEUD(null);

    /**
     * Whether [player] has finished this quest. A quest that Luna does not have yet counts as finished.
     */
    fun completed(player: Player): Boolean = completion?.invoke(player) ?: true
}
